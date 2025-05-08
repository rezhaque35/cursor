package com.wifi.positioning.controller;

import com.wifi.positioning.dto.ApiResponse;
import com.wifi.positioning.dto.PositionRequestDto;
import com.wifi.positioning.dto.PositionResponseDto;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.exception.PositioningException;
import com.wifi.positioning.service.PositioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positioning")
@Validated
@Tag(name = "WiFi Positioning", description = "APIs for WiFi-based indoor positioning")
public class PositioningController {

    private final PositioningService positioningService;
    
    @Autowired
    public PositioningController(PositioningService positioningService) {
        this.positioningService = positioningService;
    }
    
    @PostMapping("/calculate")
    @Operation(summary = "Calculate position", description = "Calculate position based on WiFi scan results")
    public ResponseEntity<ApiResponse<PositionResponseDto>> calculatePosition(
            @Valid @RequestBody PositionRequestDto request) {
        try {
            PositionResponseDto position = positioningService.calculatePosition(request);
            return ResponseEntity.ok(ApiResponse.success(position));
        } catch (PositioningException e) {
            // Use the status code from the exception
            return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
        } catch (NullPointerException e) {
            // Handle null pointer exceptions with a more specific error message
            return ResponseEntity.status(500).body(
                ApiResponse.error("Error processing WiFi data: No matching access points found in database")
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
} 