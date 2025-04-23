package com.wifi.positioning.algorithm.selection;

import lombok.Builder;
import lombok.Data;

/**
 * Holds context information about the current positioning scenario.
 * This context is shared between different selection rules and can be
 * enriched as the selection process progresses.
 */
@Data
@Builder
public class SelectionContext {
    /**
     * Whether the AP geometry is determined to be collinear
     */
    private boolean isCollinear;
    
    /**
     * Whether the APs are clustered in a small area
     */
    private boolean isClustered;
    
    /**
     * Whether the signal quality is weak overall
     */
    private boolean isWeakSignal;
    
    /**
     * Whether the signal strength varies significantly between APs
     */
    private boolean isVariableSignal;
    
    /**
     * Average confidence in the AP location data
     */
    private double apLocationConfidence;
} 