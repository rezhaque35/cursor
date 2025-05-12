package com.wifi.positioning.repository;

import com.wifi.positioning.dto.WifiAccessPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of the WifiAccessPointRepository interface.
 * Optimized to use batch operations and secondary indexes for efficient access patterns.
 */
@Repository
@Profile("!test") // Only active when not in test profile
public class WifiAccessPointRepositoryImpl implements WifiAccessPointRepository {

    /**
     * Maximum number of items in a single BatchGetItem request.
     * DynamoDB limits batch operations to 100 items per request.
     */
    private static final int MAX_BATCH_SIZE = 100;
    
    /**
     * Retry count for handling unprocessed keys in batch operations.
     * Provides a balance between reliability and performance.
     */
    private static final int MAX_BATCH_RETRIES = 3;

    private static final Logger logger = LoggerFactory.getLogger(WifiAccessPointRepositoryImpl.class);
    private final DynamoDbTable<WifiAccessPoint> accessPointTable;
    private final DynamoDbEnhancedClient enhancedClient;

    public WifiAccessPointRepositoryImpl(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.accessPointTable = enhancedClient.table(tableName, TableSchema.fromBean(WifiAccessPoint.class));
        logger.info("Initialized WifiAccessPointRepository with table: {}", tableName);
    }

    @Override
    public Optional<WifiAccessPoint> findByMacAddress(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            logger.error("MAC address cannot be null or empty");
            throw new IllegalArgumentException("MAC address cannot be null or empty");
        }

        logger.debug("Querying access point by partition key (MAC address): {}", macAddress);
        try {
            // Create a key using only the partition key (MAC address)
            Key partitionKey = Key.builder()
                    .partitionValue(macAddress)
                    .build();

            // Get the item directly using the partition key
            WifiAccessPoint result = accessPointTable.getItem(GetItemEnhancedRequest.builder().key(partitionKey).build());
            
            if (result == null) {
                logger.info("No access point found for MAC address: {}", macAddress);
                return Optional.empty();
            }

            logger.info("Successfully retrieved access point for MAC address: {}", macAddress);
            return Optional.of(result);
            
        } catch (Exception e) {
            logger.error("Error retrieving access point by MAC address: {}", macAddress, e);
            throw new RuntimeException("Failed to retrieve access point", e);
        }
    }
    
    /**
     * Find multiple access points by their MAC addresses in a single batch operation.
     * This method optimizes DynamoDB access by using BatchGetItem to retrieve multiple items
     * in a single API call.
     * 
     * Implementation handles:
     * 1. DynamoDB's batch size limit of 100 items by splitting into multiple batches
     * 2. Unprocessed keys in the response by retrying
     * 3. Proper error handling and logging
     * 
     * @param macAddresses Set of MAC addresses to look up
     * @return Map of MAC addresses to matching access points
     */
    @Override
    public Map<String, WifiAccessPoint> findByMacAddresses(Set<String> macAddresses) {
        if (macAddresses == null || macAddresses.isEmpty()) {
            logger.debug("No MAC addresses provided for batch lookup");
            return Collections.emptyMap();
        }
        
        logger.debug("Performing batch lookup for {} MAC addresses", macAddresses.size());
        Map<String, WifiAccessPoint> resultMap = new HashMap<>();
        
        try {
            // Process in batches of MAX_BATCH_SIZE
            List<List<String>> batches = batchItems(new ArrayList<>(macAddresses), MAX_BATCH_SIZE);
            logger.debug("Split {} MAC addresses into {} batch requests", macAddresses.size(), batches.size());
            
            for (List<String> batch : batches) {
                // Process each batch
                Map<String, WifiAccessPoint> batchResults = processBatchSingle(batch);
                
                // Merge batch results into the overall result map
                resultMap.putAll(batchResults);
            }
            
            logger.info("Successfully retrieved {} MAC addresses in batch operation", resultMap.size());
            return resultMap;
            
        } catch (Exception e) {
            logger.error("Error in batch retrieval of access points", e);
            throw new RuntimeException("Failed to retrieve access points in batch", e);
        }
    }
    
    /**
     * Process a batch of MAC addresses using DynamoDB BatchGetItem.
     * Handles unprocessed keys by retrying the operation.
     * This version returns a single access point per MAC address.
     * 
     * @param macAddressBatch List of MAC addresses to process in this batch
     * @return Map of MAC addresses to matching access points
     */
    private Map<String, WifiAccessPoint> processBatchSingle(List<String> macAddressBatch) {
        Map<String, WifiAccessPoint> batchResults = new HashMap<>();
        
        // Prepare the batch get request
        ReadBatch.Builder<WifiAccessPoint> readBatchBuilder = ReadBatch.builder(WifiAccessPoint.class)
                .mappedTableResource(accessPointTable);
        
        // Add all MAC addresses to the batch
        for (String macAddress : macAddressBatch) {
            Key key = Key.builder().partitionValue(macAddress).build();
            readBatchBuilder.addGetItem(key);
        }
        
        BatchGetItemEnhancedRequest batchGetRequest = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatchBuilder.build())
                .build();
        
        // Execute the batch get request - simplified implementation
        int retryCount = 0;
        boolean hasUnprocessedKeys = false;
        
        do {
            // Execute the batch get request
            BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(batchGetRequest);
            
            // Process result pages
            for (BatchGetResultPage page : resultPages) {
                // Process results for our table - simplified to handle just the items
                List<WifiAccessPoint> results = page.resultsForTable(accessPointTable);
                
                // Process each result - take the first one for each MAC address
                for (WifiAccessPoint ap : results) {
                    String macAddress = ap.getMacAddress();
                    if (!batchResults.containsKey(macAddress)) {
                        batchResults.put(macAddress, ap);
                    }
                }
                
                // Check if there are unprocessed keys - simplified approach
                hasUnprocessedKeys = !page.unprocessedKeysForTable(accessPointTable).isEmpty();
            }
            
            retryCount++;
            
            // If we have unprocessed keys, log a warning but don't retry (simplified)
            if (hasUnprocessedKeys) {
                logger.warn("Batch operation has unprocessed keys after attempt {}. Some access points may not be returned.", retryCount);
            }
            
        } while (hasUnprocessedKeys && retryCount <= MAX_BATCH_RETRIES);
        
        // Log warning if there are still unprocessed keys after retries
        if (hasUnprocessedKeys) {
            logger.warn("Failed to process all keys after {} retries.", MAX_BATCH_RETRIES);
        }
        
        return batchResults;
    }
    
    /**
     * Split a list into batches of the specified size.
     * 
     * @param items List of items to split
     * @param batchSize Maximum batch size
     * @return List of batches
     */
    private <T> List<List<T>> batchItems(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }
        
        return batches;
    }
} 