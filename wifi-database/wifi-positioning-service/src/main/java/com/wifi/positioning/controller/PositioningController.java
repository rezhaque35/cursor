package com.wifi.positioning.controller;

import com.wifi.positioning.dto.WifiPositioningResponse;
import com.wifi.positioning.dto.WifiPositioningRequest;
import com.wifi.positioning.service.PositioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
    
    @PostMapping(value = "/calculate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Calculate position", description = "Calculate position based on WiFi scan results")
    public ResponseEntity<WifiPositioningResponse> calculatePosition(
            @Valid @RequestBody WifiPositioningRequest request) {
        try {
            WifiPositioningResponse response = positioningService.calculatePosition(request);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
        } catch (PositioningException e) {
            // Use the status code from the exception
            WifiPositioningResponse errorResponse = WifiPositioningResponse.error(e.getMessage(), request);
            return ResponseEntity.status(e.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
        } catch (NullPointerException e) {
            // Handle null pointer exceptions with a more specific error message
            WifiPositioningResponse errorResponse = WifiPositioningResponse.error(
                    "Error processing WiFi data: No matching access points found in database", 
                    request
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
        } catch (Exception e) {
            WifiPositioningResponse errorResponse = WifiPositioningResponse.error(e.getMessage(), request);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse);
        }
    }
} 