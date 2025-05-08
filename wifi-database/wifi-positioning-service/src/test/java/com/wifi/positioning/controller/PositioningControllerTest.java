package com.wifi.positioning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResultDto;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.service.PositioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PositioningController.class)
class PositioningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PositioningService positioningService;

    private PositionRequestDto validRequest;
    private final PositionResponseDto successResponse = new PositionResponseDto(createSuccessMapResult());

    private static final String TEST_CLIENT = "test-client";
    private static final String TEST_REQUEST_ID = "test-request-id";
    private static final String TEST_APPLICATION = "test-application";

    @BeforeEach
    void setUp() {
        WifiScanResultDto scanResult = new WifiScanResultDto(
                "00:11:22:33:44:55",
                -65,
                2437,
                "test-ssid",
                54,
                40);

        validRequest = new PositionRequestDto(
                List.of(scanResult),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);
    }

    @Test
    void calculatePositionSuccess() throws Exception {
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
                .thenReturn(successResponse);

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.latitude").value(37.7749))
                .andExpect(jsonPath("$.data.longitude").value(-122.4194));
    }

    @Test
    void calculatePositionBadRequest() throws Exception {
        PositionRequestDto invalidRequest = new PositionRequestDto(
                Collections.emptyList(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                false);

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculatePositionError() throws Exception {
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
                .thenThrow(PositioningException.badRequest("Test error"));

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Test error"));
    }

    @Test
    void calculatePositionWithCalculationDetail() throws Exception {
        // Create success response with calculation info
        HashMap<String, Object> resultWithDetail = createSuccessMapResult();
        resultWithDetail.put("calculationInfo", "Detailed calculation information");
        PositionResponseDto responseWithDetail = new PositionResponseDto(resultWithDetail);
        
        // Create request with calculationDetail flag
        PositionRequestDto requestWithDetail = new PositionRequestDto(
                validRequest.wifiScanResults(),
                TEST_CLIENT,
                TEST_REQUEST_ID,
                TEST_APPLICATION,
                true);
        
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
                .thenReturn(responseWithDetail);

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestWithDetail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.metadata.calculationInfo").value("Detailed calculation information"));
    }

    private static HashMap<String, Object> createSuccessMapResult() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("latitude", 37.7749);
        result.put("longitude", -122.4194);
        result.put("altitude", 10.0);
        result.put("horizontalAccuracy", 5.0);
        result.put("verticalAccuracy", 2.0);
        result.put("confidence", 0.85);
        result.put("methodsUsed", List.of("test-method-1", "test-method-2"));
        result.put("apCount", 1);
        
        // Metadata
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("result", "SUCCESS");
        metadata.put("positionFound", true);
        metadata.put("calculationTimeMs", 50L);
        metadata.put("timestamp", System.currentTimeMillis());
        result.put("metadata", metadata);
        
        // Empty alternatives
        result.put("alternatives", new ArrayList<>());
        
        return result;
    }
} 