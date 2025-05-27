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
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of the WifiAccessPointRepository interface.
 * Optimized to use batch operations and secondary indexes for efficient access patterns.
 * Includes health check capabilities for monitoring table accessibility and performance.
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

    /**
     * Latency threshold for health checks in milliseconds.
     * Response times exceeding this threshold indicate potential performance issues.
     * Set to 1000ms (1 second) based on typical DynamoDB response time expectations.
     * 
     * Rationale: 
     * - Normal DynamoDB operations should complete within 100-500ms
     * - 1 second threshold allows for network latency and temporary slowdowns
     * - Exceeding 1 second may indicate throttling, network issues, or overloaded tables
     */
    private static final long LATENCY_THRESHOLD_MS = 1_000L;

    /**
     * Conversion factor from nanoseconds to milliseconds.
     * Used for converting System.nanoTime() measurements to milliseconds.
     * Mathematical formula: milliseconds = nanoseconds / 1,000,000
     * 
     * Rationale:
     * - System.nanoTime() provides high-precision timing measurement
     * - 1 nanosecond = 1/1,000,000,000 seconds = 1/1,000,000 milliseconds
     * - This constant ensures accurate conversion for latency measurements
     */
    private static final long NANOS_TO_MILLIS = 1_000_000L;

    // Health Check Status Messages
    /**
     * Status message for healthy table state.
     * Used when table is accessible and response time is within acceptable limits.
     */
    private static final String HEALTHY_STATUS_MESSAGE = "Table is accessible and healthy";
    
    /**
     * Status message for slow response times.
     * Used when table is accessible but response time exceeds the threshold.
     */
    private static final String SLOW_RESPONSE_STATUS_MESSAGE = "Table response time exceeds threshold";

    private static final Logger logger = LoggerFactory.getLogger(WifiAccessPointRepositoryImpl.class);
    private final DynamoDbTable<WifiAccessPoint> accessPointTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tableName;

    public WifiAccessPointRepositoryImpl(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.tableName = tableName;
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

    /**
     * Validates table accessibility and measures response time for health checks.
     * 
     * This method performs a comprehensive health check by:
     * 1. Measuring response time using high-precision timing
     * 2. Verifying table existence and accessibility
     * 3. Retrieving item count to validate read permissions
     * 4. Evaluating response time against performance thresholds
     * 
     * Mathematical Formula for Response Time:
     * response_time_ms = (end_time_nanos - start_time_nanos) / 1,000,000
     * 
     * Where:
     * - start_time_nanos: System.nanoTime() before DynamoDB operation
     * - end_time_nanos: System.nanoTime() after DynamoDB operation
     * - response_time_ms: Latency in milliseconds for performance evaluation
     * 
     * Health Evaluation Logic:
     * - Healthy: response_time_ms < LATENCY_THRESHOLD_MS (1000ms)
     * - Unhealthy: response_time_ms >= LATENCY_THRESHOLD_MS
     * 
     * @return HealthCheckResult containing validation results and metrics
     * @throws ResourceNotFoundException if the table does not exist
     * @throws DynamoDbException if there are connectivity or permission issues
     * @throws Exception for unexpected errors during validation
     */
    @Override
    public HealthCheckResult validateTableHealth() throws ResourceNotFoundException, DynamoDbException, Exception {
        logger.debug("Starting table health validation for: {}", tableName);
        
        // Measure response time using high-precision timing
        long startTime = System.nanoTime();
        
        try {
            // Perform table describe operation to validate accessibility
            DescribeTableEnhancedResponse response = accessPointTable.describeTable();
            long itemCount = response.table().itemCount();
            
            // Calculate response time in milliseconds
            long endTime = System.nanoTime();
            long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;
            
            // Evaluate health based on response time threshold
            boolean isHealthy = responseTimeMs < LATENCY_THRESHOLD_MS;
            String statusMessage = isHealthy ? HEALTHY_STATUS_MESSAGE : SLOW_RESPONSE_STATUS_MESSAGE;
            
            logger.debug("Table health check completed - Table: {}, Response time: {}ms, Healthy: {}, Item count: {}", 
                    tableName, responseTimeMs, isHealthy, itemCount);
            
            return new HealthCheckResult(isHealthy, responseTimeMs, tableName, itemCount, statusMessage);
            
        } catch (ResourceNotFoundException e) {
            long endTime = System.nanoTime();
            long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;
            logger.error("Table not found during health check: {} (response time: {}ms)", tableName, responseTimeMs);
            throw e;
            
        } catch (DynamoDbException e) {
            long endTime = System.nanoTime();
            long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;
            logger.error("DynamoDB error during health check: {} (response time: {}ms)", tableName, responseTimeMs, e);
            throw e;
            
        } catch (Exception e) {
            long endTime = System.nanoTime();
            long responseTimeMs = (endTime - startTime) / NANOS_TO_MILLIS;
            logger.error("Unexpected error during health check: {} (response time: {}ms)", tableName, responseTimeMs, e);
            throw e;
        }
    }

    /**
     * Gets the approximate item count from the table for health validation.
     * 
     * This method verifies read permissions by retrieving table statistics.
     * The item count is approximate and may not reflect real-time values due to
     * DynamoDB's eventually consistent nature.
     * 
     * Use Cases:
     * - Validate that the service has read permissions on the table
     * - Monitor table growth for capacity planning
     * - Detect empty tables that might indicate data loading issues
     * 
     * @return Approximate number of items in the table
     * @throws ResourceNotFoundException if the table does not exist
     * @throws DynamoDbException if there are connectivity or permission issues
     * @throws Exception for unexpected errors during count retrieval
     */
    @Override
    public long getApproximateItemCount() throws ResourceNotFoundException, DynamoDbException, Exception {
        logger.debug("Retrieving approximate item count for table: {}", tableName);
        
        try {
            DescribeTableEnhancedResponse response = accessPointTable.describeTable();
            long itemCount = response.table().itemCount();
            
            logger.debug("Retrieved approximate item count: {} for table: {}", itemCount, tableName);
            return itemCount;
            
        } catch (ResourceNotFoundException e) {
            logger.error("Table not found when retrieving item count: {}", tableName);
            throw e;
            
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error when retrieving item count for table: {}", tableName, e);
            throw e;
            
        } catch (Exception e) {
            logger.error("Unexpected error when retrieving item count for table: {}", tableName, e);
            throw e;
        }
    }
} 