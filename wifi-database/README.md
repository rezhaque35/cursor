# WiFi Data CSV to JSON Converter

This script converts WiFi access point data from CSV format to JSON format with specific mapping rules.

## Prerequisites

- Python 3.x
- pip (Python package installer)

## Installation Steps

1. **Clone or download the repository**
   - Make sure you have the following files:
     - `csv_to_json.py`
     - `requirements.txt`
     - `import-data.csv` (your input CSV file)

2. **Install Python dependencies**
   ```bash
   python3 -m pip install -r requirements.txt
   ```

## Usage

1. **Prepare your input file**
   - Ensure your CSV file is named `import-data.csv`
   - The CSV should have the following columns:
     - bssid
     - latitude
     - longitude
     - accuracy
     - altitude
     - rssi
     - cellid
     - ishotspot
     - timestamp
     - locweight
     - eventtype
     - pressure
     - pressure_accuracy

2. **Run the script**
   ```bash
   python3 csv_to_json.py
   ```

3. **Check the output**
   - The script will generate `wifi-access-points-test-data.json`
   - You can verify the output using:
     ```bash
     cat wifi-access-points-test-data.json
     ```

## Output Format

The script will generate a JSON file with the following mapping:
- bssid → mac_addr
- Current timestamp → version (format: yyyymmdd-hhmmss)
- latitude → latitude (as number)
- longitude → longitude (as number)
- altitude → altitude (as number)
- accuracy → horizontal_accuracy and vertical_accuracy (as numbers)
- locweight/100.0 → confidence (as number)
- Empty string → ssid
- 0 → frequency
- Empty string → vendor
- Calculated geohash → geohash
- ishotspot → status ("wifi-hotspot" if 1, "imported" if 0)

## Troubleshooting

If you encounter any issues:

1. **Python not found**
   - Make sure Python 3 is installed
   - Try running `python3 --version` to verify

2. **pip not found**
   - Use `python3 -m pip` instead of just `pip`

3. **Module not found errors**
   - Ensure you've run the pip install command
   - Try running `python3 -m pip install -r requirements.txt` again

4. **File not found errors**
   - Verify that `import-data.csv` is in the same directory as the script
   - Check file permissions 