#!/usr/bin/env python3

import math
from enum import Enum
from dataclasses import dataclass
from typing import List, Dict, Optional
from colorama import Fore, Style, init

# Initialize colorama
init()

class SignalQuality(Enum):
    STRONG_SIGNAL = "STRONG_SIGNAL"
    MEDIUM_SIGNAL = "MEDIUM_SIGNAL"
    WEAK_SIGNAL = "WEAK_SIGNAL"

class SignalDistribution(Enum):
    UNIFORM_SIGNALS = "UNIFORM_SIGNALS"
    MIXED_SIGNALS = "MIXED_SIGNALS"
    SIGNAL_OUTLIERS = "SIGNAL_OUTLIERS"

class GeometricQuality(Enum):
    EXCELLENT_GDOP = "EXCELLENT_GDOP"
    GOOD_GDOP = "GOOD_GDOP"
    FAIR_GDOP = "FAIR_GDOP"
    POOR_GDOP = "POOR_GDOP"

@dataclass
class TestCase:
    name: str
    signal_strengths: List[float]
    expected_signal_quality: SignalQuality
    expected_signal_distribution: SignalDistribution
    gdop: float
    expected_geometric_quality: GeometricQuality

class AlgorithmWeights:
    def __init__(self, ap_count: int):
        self.weights: Dict[str, float] = {}
        if ap_count == 1:
            self.weights = {
                "proximity": 1.0,
                "log_distance": 0.4
            }
        elif ap_count == 2:
            self.weights = {
                "proximity": 0.4,
                "rssi_ratio": 1.0,
                "weighted_centroid": 0.8,
                "log_distance": 0.5
            }
        elif ap_count == 3:
            self.weights = {
                "proximity": 0.3,
                "rssi_ratio": 0.7,
                "weighted_centroid": 0.8,
                "trilateration": 1.0,
                "log_distance": 0.5
            }
        else:
            self.weights = {
                "proximity": 0.2,
                "rssi_ratio": 0.5,
                "weighted_centroid": 0.7,
                "trilateration": 0.8,
                "maximum_likelihood": 1.0,
                "log_distance": 0.4
            }

    def apply_signal_quality_adjustment(self, signal_quality: SignalQuality):
        if signal_quality == SignalQuality.STRONG_SIGNAL:
            self.weights["proximity"] *= 0.9
            self.weights["log_distance"] *= 1.0
        elif signal_quality == SignalQuality.MEDIUM_SIGNAL:
            self.weights["proximity"] *= 0.7
            self.weights["log_distance"] *= 0.8
        else:  # WEAK_SIGNAL
            self.weights["proximity"] *= 0.4
            self.weights["log_distance"] *= 0.6

    def apply_geometric_quality_adjustment(self, geometric_quality: GeometricQuality):
        if geometric_quality == GeometricQuality.EXCELLENT_GDOP:
            self.weights["log_distance"] *= 1.0
        elif geometric_quality == GeometricQuality.GOOD_GDOP:
            self.weights["log_distance"] *= 1.0
        elif geometric_quality == GeometricQuality.FAIR_GDOP:
            self.weights["log_distance"] *= 0.8
        else:  # POOR_GDOP
            self.weights["log_distance"] *= 0.7

    def apply_signal_distribution_adjustment(self, signal_distribution: SignalDistribution):
        if signal_distribution == SignalDistribution.UNIFORM_SIGNALS:
            self.weights["log_distance"] *= 1.1
        elif signal_distribution == SignalDistribution.MIXED_SIGNALS:
            self.weights["log_distance"] *= 0.8
        else:  # SIGNAL_OUTLIERS
            self.weights["log_distance"] *= 0.8

def validate_signal_quality(avg_signal: float, expected: SignalQuality) -> SignalQuality:
    if avg_signal > -70:
        actual = SignalQuality.STRONG_SIGNAL
    elif avg_signal >= -85:
        actual = SignalQuality.MEDIUM_SIGNAL
    else:
        actual = SignalQuality.WEAK_SIGNAL

    if actual == expected:
        print(f"{Fore.GREEN}✓ Signal Quality: {actual.value}{Style.RESET_ALL}")
    else:
        print(f"{Fore.RED}✗ Signal Quality: Expected {expected.value} but got {actual.value}{Style.RESET_ALL}")
    return actual

def validate_signal_distribution(std_dev: float, expected: SignalDistribution) -> SignalDistribution:
    if std_dev < 3.0:
        actual = SignalDistribution.UNIFORM_SIGNALS
    elif std_dev < 10.0:
        actual = SignalDistribution.MIXED_SIGNALS
    else:
        actual = SignalDistribution.SIGNAL_OUTLIERS

    if actual == expected:
        print(f"{Fore.GREEN}✓ Signal Distribution: {actual.value}{Style.RESET_ALL}")
    else:
        print(f"{Fore.RED}✗ Signal Distribution: Expected {expected.value} but got {actual.value}{Style.RESET_ALL}")
    return actual

def validate_geometric_quality(gdop: float, expected: GeometricQuality) -> GeometricQuality:
    if gdop < 2.0:
        actual = GeometricQuality.EXCELLENT_GDOP
    elif gdop < 4.0:
        actual = GeometricQuality.GOOD_GDOP
    elif gdop < 6.0:
        actual = GeometricQuality.FAIR_GDOP
    else:
        actual = GeometricQuality.POOR_GDOP

    if actual == expected:
        print(f"{Fore.GREEN}✓ Geometric Quality: {actual.value}{Style.RESET_ALL}")
    else:
        print(f"{Fore.RED}✗ Geometric Quality: Expected {expected.value} but got {actual.value}{Style.RESET_ALL}")
    return actual

def calculate_statistics(signal_strengths: List[float]) -> tuple[float, float]:
    avg_signal = sum(signal_strengths) / len(signal_strengths)
    variance = sum((x - avg_signal) ** 2 for x in signal_strengths) / len(signal_strengths)
    std_dev = math.sqrt(variance)
    return avg_signal, std_dev

def validate_test_case(test_case: TestCase):
    print(f"\n{Fore.YELLOW}Test Case: {test_case.name}{Style.RESET_ALL}")
    print(f"Signal Strengths: {test_case.signal_strengths}")
    
    avg_signal, std_dev = calculate_statistics(test_case.signal_strengths)
    print(f"Average Signal: {avg_signal:.2f} dBm")
    print(f"Signal Std Dev: {std_dev:.2f} dBm")
    
    actual_signal_quality = validate_signal_quality(avg_signal, test_case.expected_signal_quality)
    actual_signal_distribution = validate_signal_distribution(std_dev, test_case.expected_signal_distribution)
    actual_geometric_quality = validate_geometric_quality(test_case.gdop, test_case.expected_geometric_quality)
    
    weights = AlgorithmWeights(len(test_case.signal_strengths))
    weights.apply_signal_quality_adjustment(actual_signal_quality)
    weights.apply_geometric_quality_adjustment(actual_geometric_quality)
    weights.apply_signal_distribution_adjustment(actual_signal_distribution)
    
    print(f"\n{Fore.YELLOW}Final Algorithm Weights:{Style.RESET_ALL}")
    for algorithm, weight in weights.weights.items():
        print(f"{algorithm}: {weight:.3f}")

def main():
    test_cases = [
        TestCase(
            "Single AP with strong signal",
            [-65.0],
            SignalQuality.STRONG_SIGNAL,
            SignalDistribution.UNIFORM_SIGNALS,
            26.084,
            GeometricQuality.POOR_GDOP
        ),
        TestCase(
            "Two APs with mixed signals",
            [-68.5, -62.3],
            SignalQuality.STRONG_SIGNAL,
            SignalDistribution.MIXED_SIGNALS,
            27.849,
            GeometricQuality.POOR_GDOP
        ),
        TestCase(
            "Three APs with strong signals",
            [-62.3, -71.2, -85.5],
            SignalQuality.MEDIUM_SIGNAL,
            SignalDistribution.SIGNAL_OUTLIERS,
            1.152,
            GeometricQuality.EXCELLENT_GDOP
        ),
        TestCase(
            "Multiple APs with mixed signal quality",
            [-71.2, -85.5, -70.0, -68.0],
            SignalQuality.MEDIUM_SIGNAL,
            SignalDistribution.MIXED_SIGNALS,
            2.5,
            GeometricQuality.GOOD_GDOP
        ),
        TestCase(
            "Weak Signals",
            [-85.5],
            SignalQuality.WEAK_SIGNAL,
            SignalDistribution.MIXED_SIGNALS,
            3.8,
            GeometricQuality.GOOD_GDOP
        )
    ]
    
    print(f"{Fore.GREEN}Starting validation of test contexts against algorithm selection framework...{Style.RESET_ALL}")
    for test_case in test_cases:
        validate_test_case(test_case)
    print(f"\n{Fore.GREEN}Validation complete.{Style.RESET_ALL}")

if __name__ == "__main__":
    main() 