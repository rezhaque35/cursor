package com.wifi.positioning.algorithm.selection;

import com.wifi.positioning.algorithm.PositioningAlgorithm;

/**
 * Represents a positioning algorithm with its associated weight.
 * Used to track which algorithms are selected and their relative importance.
 */
public record WeightedAlgorithm(PositioningAlgorithm algorithm, double weight) {
} 