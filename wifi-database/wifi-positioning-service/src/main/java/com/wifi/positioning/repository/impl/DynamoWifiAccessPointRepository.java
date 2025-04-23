package com.wifi.positioning.repository.impl;

import com.wifi.positioning.model.DynamoWifiAccessPoint;
import com.wifi.positioning.model.WifiAccessPoint;
import com.wifi.positioning.repository.WifiAccessPointRepository;
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

/**
 * DynamoDB implementation of the WifiAccessPointRepository interface.
 * Simplified to only include methods needed for position calculation.
 */
@Repository
@Profile("!test") // Only active when not in test profile
public class DynamoWifiAccessPointRepository implements WifiAccessPointRepository {

    private static final Logger logger = LoggerFactory.getLogger(DynamoWifiAccessPointRepository.class);
    private final DynamoDbTable<DynamoWifiAccessPoint> accessPointTable;

    public DynamoWifiAccessPointRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName) {
        this.accessPointTable = enhancedClient.table(tableName, TableSchema.fromBean(DynamoWifiAccessPoint.class));
        logger.info("Initialized DynamoWifiAccessPointRepository with table: {}", tableName);
    }

    @Override
    public List<WifiAccessPoint> findByMacAddress(String macAddress) {
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
            DynamoWifiAccessPoint result = accessPointTable.getItem(GetItemEnhancedRequest.builder().key(partitionKey).build());
            
            if (result == null) {
                logger.info("No access point found for MAC address: {}", macAddress);
                return Collections.emptyList();
            }

            logger.info("Successfully retrieved access point for MAC address: {}", macAddress);
            return Collections.singletonList(result.toWifiAccessPoint());
            
        } catch (Exception e) {
            logger.error("Error retrieving access point by MAC address: {}", macAddress, e);
            throw new RuntimeException("Failed to retrieve access point", e);
        }
    }
} 