# WiFi Positioning Algorithms Documentation

## Table of Contents
1. [Individual Algorithms](#individual-algorithms)
   - [Proximity Detection](#proximity-detection)
   - [RSSI Ratio](#rssi-ratio)
   - [Log-Distance Path Loss](#log-distance-path-loss)
   - [Trilateration](#trilateration)
   - [Maximum Likelihood](#maximum-likelihood)
   - [Weighted Centroid](#weighted-centroid)
2. [Hybrid Algorithm](#hybrid-algorithm)
   - [Overview](#overview)
   - [Selection Rules](#selection-rules)
   - [Algorithm Combination](#algorithm-combination)
3. [Performance Characteristics](#performance-characteristics)

## Individual Algorithms

### Proximity Detection
**Use Cases:**
- Single AP environments or sparse AP deployments
- When only coarse positioning is needed
- Emergency/fallback positioning
- Quick initial position estimation

**Strengths:**
- Extremely simple and fast computation
- Minimal resource requirements
- Works with single AP
- Resistant to multipath effects

**Weaknesses:**
- Limited accuracy (position is always at AP location)
- No interpolation between APs
- Highly sensitive to signal strength fluctuations
- Poor performance in dense AP environments

**Mathematical Model:**
```python
# Find AP with maximum RSSI
position = position_of_AP_with_max(RSSI)

# Confidence calculation
normalized = (RSSI - MIN_SIGNAL) / SIGNAL_RANGE
confidence = min(0.85, max(0, normalized))
```

### RSSI Ratio
**Use Cases:**
- Ideal for scenarios with 2-3 access points
- Effective when absolute signal calibration is difficult
- Works well with similar hardware characteristics

**Strengths:**
- No need for absolute signal strength calibration
- Resistant to environmental changes
- Computationally efficient
- Works well with dynamic transmit power changes

**Weaknesses:**
- Accuracy decreases with dissimilar AP hardware
- Performance degrades with more than 4-5 APs
- Sensitive to individual signal fluctuations

**Mathematical Model:**
```python
# RSSI Ratio Calculation
ratio = 10^((RSSI1 - RSSI2)/20)

# Position Estimation
P = (P1 + ratio * P2)/(1 + ratio)
```

### Log-Distance Path Loss
**Use Cases:**
- Indoor environments with consistent signal propagation
- When AP vendor information is available
- Reliable for distances up to 30-40 meters

**Strengths:**
- Accounts for environmental characteristics
- Adapts to vendor-specific AP characteristics
- Handles signal strength variations effectively

**Weaknesses:**
- Accuracy decreases in dynamic environments
- Requires accurate reference measurements
- Performance degrades with multipath effects

**Mathematical Model:**
```python
# Path Loss Model
PL(d) = PL(d0) + 10 * n * log10(d/d0) + X

# Distance Calculation
d = d0 * 10^((|RSSI| - |RSS0|)/(10 * n))
```

### Trilateration
**Use Cases:**
- Environments with 3+ well-distributed APs
- Open spaces with minimal signal interference
- High accuracy requirements

**Strengths:**
- High accuracy with good AP geometry
- Mathematically rigorous calculation
- Works well with strong, stable signals
- Provides accurate 3D positioning

**Weaknesses:**
- Requires at least 3 APs
- Sensitive to AP geometry
- Accuracy degrades with noisy signals
- Computationally intensive

**Mathematical Model:**
```python
# Distance Estimation
d = d0 * 10^((RSSI0 - RSSI)/(10 * n))

# Position Calculation (Least Squares)
(x - x1)² + (y - y1)² = d1²
(x - x2)² + (y - y2)² = d2²
(x - x3)² + (y - y3)² = d3²
```

### Maximum Likelihood
**Use Cases:**
- Environments with many APs (5+)
- Complex indoor environments with multipath
- When historical signal data is available
- High accuracy requirements

**Strengths:**
- Most accurate with sufficient data
- Handles noisy measurements robustly
- Incorporates historical patterns
- Provides realistic confidence estimates

**Weaknesses:**
- Computationally intensive
- Requires more measurements
- May converge slowly
- Higher memory usage

**Mathematical Model:**
```python
# Likelihood Function
L(pos) = Π P(measurement_i | pos)
P(RSSI | pos) = N(RSSI; μ(d), σ²)

# Log-Likelihood Maximization
LL(pos) = Σ log(P(measurement_i | pos))
∇LL(pos) = Σ (RSSI_i - μ(d_i))/σ² * ∇d_i
```

### Weighted Centroid
**Use Cases:**
- Environments with many APs (4+)
- When AP geometry is poor for trilateration
- Areas with high AP density but variable signal quality

**Strengths:**
- Simple and computationally efficient
- Robust to individual AP failures
- Works well with non-uniform AP distributions
- Handles overlapping coverage effectively

**Weaknesses:**
- Less accurate than trilateration in ideal conditions
- Sensitive to AP distribution geometry
- May be biased toward high AP density areas

**Mathematical Model:**
```python
# Weight Calculation
normalized = (RSSI - MAX_SIGNAL) / (MIN_SIGNAL - MAX_SIGNAL)
weight = 10^normalized

# Position Calculation
P = Σ(Pi * wi) / Σ(wi)
```

## Hybrid Algorithm

### Overview
The hybrid positioning system dynamically selects and combines multiple positioning algorithms based on environmental conditions and available data. It uses a rule-based approach to choose the most appropriate algorithms and weights their results based on confidence levels.

### Selection Rules
1. **AP Count Rule (Priority: 100)**
   ```python
   if ap_count == 1:
       use Proximity Detection
   elif ap_count == 2:
       use RSSI Ratio + Weighted Centroid
   elif 3 <= ap_count < 5:
       use Trilateration + Log Distance + Weighted Centroid + RSSI Ratio
   else:  # ap_count >= 5
       use all algorithms
   ```

2. **Geometry Rule**
   ```python
   if is_collinear:
       exclude Trilateration
   if is_clustered:
       boost Maximum Likelihood and RSSI Ratio
   ```

3. **Signal Quality Rule**
   ```python
   if weak_signals:
       boost Weighted Centroid and RSSI Ratio
   if variable_signals:
       boost Maximum Likelihood
   ```

4. **Location Certainty Rule**
   ```python
   if high_location_certainty:
       boost Trilateration
   if low_location_certainty:
       boost Weighted Centroid
   ```

### Algorithm Combination
```python
# Pseudo-code for hybrid algorithm
def calculate_position(wifi_scan, known_aps):
    # 1. Filter valid APs
    valid_scans = filter_valid_scans(wifi_scan, known_aps)
    
    # 2. Evaluate scenario characteristics
    context = build_context(valid_scans, known_aps)
    
    # 3. Select algorithms and weights
    weighted_algorithms = apply_selection_rules(algorithms, context)
    
    # 4. Calculate positions in parallel
    positions = parallel_map(
        lambda algo: algo.calculate_position(valid_scans, known_aps),
        weighted_algorithms
    )
    
    # 5. Combine results
    final_position = weighted_average_combine(positions)
    return final_position
```

## Performance Characteristics

### Accuracy vs. Computation Trade-off
- **High Accuracy (>95% confidence)**
  - Maximum Likelihood with 5+ APs
  - Trilateration with 3+ well-distributed APs
  - Processing time: 100-200ms

- **Medium Accuracy (80-95% confidence)**
  - RSSI Ratio with 2-3 APs
  - Weighted Centroid with 4+ APs
  - Processing time: 50-100ms

- **Low Accuracy (<80% confidence)**
  - Proximity Detection
  - Processing time: 10-50ms

### Resource Usage
- Memory: 10-50MB depending on active algorithms
- CPU: 1-4 cores for parallel processing
- Network: Minimal (only scan results and AP data)

### Scalability
- Supports up to 100 simultaneous APs
- Handles 1000+ positioning requests per second
- Linear scaling with number of APs for most algorithms