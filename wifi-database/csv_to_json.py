#!/usr/bin/env python3
import csv
import json
from datetime import datetime
import geohash

def transform_csv_to_json(input_csv, output_json):
    # Read CSV and transform data
    transformed_data = []
    
    with open(input_csv, 'r') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            # Calculate geohash
            gh = geohash.encode(float(row['latitude']), float(row['longitude']))
            
            # Transform data according to mapping rules
            transformed_row = {
                "mac_addr": row['bssid'],
                "version": datetime.now().strftime("%Y%m%d-%H%M%S"),
                "latitude": float(row['latitude']),
                "longitude": float(row['longitude']),
                "altitude": float(row['altitude']),
                "horizontal_accuracy": float(row['accuracy']),
                "vertical_accuracy": float(row['accuracy']),
                "confidence": float(row['locweight']) / 100.0,
                "ssid": "",
                "frequency": 0,
                "vendor": "",
                "geohash": gh,
                "status": "wifi-hotspot" if int(row['ishotspot']) == 1 else "imported"
            }
            transformed_data.append(transformed_row)
    
    # Write to JSON file
    with open(output_json, 'w') as jsonfile:
        json.dump(transformed_data, jsonfile, indent=2)

if __name__ == "__main__":
    input_csv = "import-data.csv"
    output_json = "wifi-access-points-test-data.json"
    transform_csv_to_json(input_csv, output_json) 