package com.wifi.positioning.algorithm;

import com.wifi.positioning.dto.Position;
import com.wifi.positioning.dto.WifiScanResult;
import com.wifi.positioning.model.WifiAccessPoint;
import java.util.List;

public interface PositioningAlgorithm {
    Position calculatePosition(List<WifiScanResult> wifiScan, List<WifiAccessPoint> knownAPs);
    double getConfidence();
    String getName();
} 