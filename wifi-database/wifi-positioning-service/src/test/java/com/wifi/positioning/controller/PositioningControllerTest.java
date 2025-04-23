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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PositioningController.class)
class PositioningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PositioningService positioningService;

    private PositionRequestDto validRequest;
    private PositionResponseDto mockResponse;

    @BeforeEach
    void setUp() {
        // Set up test data
        WifiScanResultDto scanResult = new WifiScanResultDto(
                "00:11:22:33:44:55",
                -65,
                2437,
                6,
                "test-ssid",
                54,
                40);

        validRequest = new PositionRequestDto(
                List.of(scanResult),
                true,
                false,
                "test-session-id");

        // Mock position response
        mockResponse = new PositionResponseDto(
                37.7749,
                -122.4194,
                10.0,
                5.0,
                2.0,
                0.85,
                "test-method",
                List.of("test-method-1", "test-method-2"),
                1,
                Map.of("calculationTimeMs", 50L, "timestamp", "2023-04-11T12:00:00Z"),
                Collections.emptyList());
    }

    @Test
    void calculatePositionSuccess() throws Exception {
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", is("SUCCESS")))
                .andExpect(jsonPath("$.data.latitude", is(37.7749)))
                .andExpect(jsonPath("$.data.longitude", is(-122.4194)))
                .andExpect(jsonPath("$.data.altitude", is(10.0)))
                .andExpect(jsonPath("$.data.confidence", is(0.85)))
                .andExpect(jsonPath("$.data.bestMethod", is("test-method")));
    }

    @Test
    void calculatePositionBadRequest() throws Exception {
        // Invalid request with no scan results
        PositionRequestDto invalidRequest = new PositionRequestDto(
                Collections.emptyList(),
                true,
                false,
                "test-session-id");

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result", is("ERROR")));
    }

    @Test
    void calculatePositionServiceError() throws Exception {
        when(positioningService.calculatePosition(any(PositionRequestDto.class)))
                .thenThrow(new PositioningException("Test error", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/positioning/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result", is("ERROR")));
    }
} 