# Additional high-cardinality metrics
HIGH_CARDINALITY_METRICS = [
    {
        "name": "request.latency",
        "unit": "milliseconds",
        "min": 1,
        "max": 500,
        "pattern": "stable_with_spikes",
        "anomaly_chance": 0.02,
        "high_cardinality": True,  # Special flag for high cardinality
        "tag_keys": ["customer_id", "request_id"]  # Will add these high-cardinality tags
    },
    {
        "name": "user.activity",
        "unit": "actions_per_min",
        "min": 0,
        "max": 1000,
        "pattern": "daily_cycle",
        "anomaly_chance": 0.01,
        "high_cardinality": True,
        "tag_keys": ["customer_id"]
    },
    {
        "name": "transaction.value",
        "unit": "dollars",
        "min": 0.1,
        "max": 999.99,
        "pattern": "random_spikes",
        "anomaly_chance": 0.03,
        "high_cardinality": True,
        "tag_keys": ["customer_id", "request_id"]
    }
]#!/usr/bin/env python3
"""
Time Series Sample Data Generator

This script generates realistic sample data for testing a time series store.
It creates CSV files with timestamp, metric, value, and tags that can be imported
into your time series store implementation.

Features:
- Generates data for multiple metrics
- Creates realistic patterns (daily cycles, trends, anomalies)
- Adds random tags with configurable cardinality
- Supports different output formats (CSV, JSON)

Usage:
  python generate_sample_data.py [options]

Options:
  --start-time TIMESTAMP   Start time for data generation (default: 24 hours ago)
  --end-time TIMESTAMP     End time for data generation (default: now)
  --interval SECONDS       Interval between data points in seconds (default: 60)
  --metrics NUM            Number of metrics to generate (default: 10)
  --hosts NUM              Number of hosts to simulate (default: 5)
  --output FORMAT          Output format: csv or json (default: csv)
  --output-file PATH       Path to output file (default: time_series_data.csv/json)
  --seed NUM               Random seed for reproducibility (default: 42)
"""

import argparse
import csv
import datetime
import json
import math
import os
import random
import sys
import time
from typing import Dict, List, Tuple, Union

# Metric templates with reasonable value ranges and patterns
METRIC_TEMPLATES = [
    {
        "name": "cpu.usage",
        "unit": "percent",
        "min": 0,
        "max": 100,
        "pattern": "daily_cycle",  # Higher during business hours
        "anomaly_chance": 0.01,    # Occasional spikes
    },
    {
        "name": "memory.used",
        "unit": "percent",
        "min": 20,
        "max": 95,
        "pattern": "gradual_increase",  # Memory tends to grow until restart
        "anomaly_chance": 0.005,
    },
    {
        "name": "disk.io",
        "unit": "ops_per_sec",
        "min": 0,
        "max": 5000,
        "pattern": "bursty",  # Periods of high activity
        "anomaly_chance": 0.02,
    },
    {
        "name": "network.in.bytes",
        "unit": "bytes_per_sec",
        "min": 0,
        "max": 100000000,
        "pattern": "daily_cycle",
        "anomaly_chance": 0.015,
    },
    {
        "name": "network.out.bytes",
        "unit": "bytes_per_sec",
        "min": 0,
        "max": 80000000,
        "pattern": "daily_cycle",
        "anomaly_chance": 0.015,
    },
    {
        "name": "latency.ms",
        "unit": "milliseconds",
        "min": 1,
        "max": 500,
        "pattern": "stable_with_spikes",
        "anomaly_chance": 0.03,
    },
    {
        "name": "requests.count",
        "unit": "count_per_min",
        "min": 0,
        "max": 10000,
        "pattern": "daily_cycle",
        "anomaly_chance": 0.01,
    },
    {
        "name": "disk.free",
        "unit": "percent",
        "min": 5,
        "max": 80,
        "pattern": "gradual_decrease",
        "anomaly_chance": 0.002,
    },
    {
        "name": "errors.count",
        "unit": "count_per_min",
        "min": 0,
        "max": 100,
        "pattern": "random_spikes",
        "anomaly_chance": 0.05,
    },
    {
        "name": "temperature",
        "unit": "celsius",
        "min": 25,
        "max": 85,
        "pattern": "correlated_with_cpu",
        "anomaly_chance": 0.008,
    },
]

# Tag categories with possible values
TAG_CATEGORIES = {
    "host": ["server{:02d}".format(i) for i in range(1, 101)],  # Increased from 20 to 100
    "datacenter": ["us-east", "us-west", "eu-central", "ap-south", "ap-northeast"],
    "environment": ["prod", "staging", "dev", "test"],
    "service": ["api", "web", "db", "cache", "auth", "worker", "queue", "storage",
                "analytics", "search", "recommendation", "payment", "notification", "user", "admin"],  # Expanded
    "instance_type": ["t3.micro", "t3.small", "t3.medium", "m5.large", "c5.xlarge", "r5.2xlarge"],
    "os": ["linux", "windows"],
    "kernel_version": ["4.15.0", "5.4.0", "5.10.0"],
    "disk_type": ["ssd", "hdd"],
    "customer_id": ["cust{:06d}".format(i) for i in range(1, 10001)],  # High cardinality tag
    "request_id": ["req{:08d}".format(i) for i in range(1, 1001)],      # Additional high cardinality tag
}


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description='Generate sample time series data')

    # Time range settings
    now = int(time.time())
    one_day_ago = now - 3600 * 24  # 1 day ago

    parser.add_argument('--start-time', type=int, default=one_day_ago,
                        help='Start timestamp in Unix time (default: 1 day ago)')
    parser.add_argument('--end-time', type=int, default=now,
                        help='End timestamp in Unix time (default: now)')
    parser.add_argument('--interval', type=int, default=15,
                        help='Interval between data points in seconds (default: 15, 15 seconds)')

    # Data generation settings
    parser.add_argument('--metrics', type=int, default=25,
                        help='Number of metrics to generate (default: 25)')
    parser.add_argument('--hosts', type=int, default=35,
                        help='Number of hosts to simulate (default: 35)')

    # Output settings
    parser.add_argument('--output', type=str, choices=['csv', 'json'], default='csv',
                        help='Output format: csv or json (default: csv)')
    parser.add_argument('--output-file', type=str, default=None,
                        help='Path to output file (default: time_series_data.[format])')

    # Reproducibility
    parser.add_argument('--seed', type=int, default=42,
                        help='Random seed for reproducibility (default: 42)')

    return parser.parse_args()


def generate_metrics(num_metrics: int) -> List[Dict]:
    """
    Generate a list of metrics based on templates.

    Args:
        num_metrics: Number of metrics to generate

    Returns:
        List of metric dictionaries
    """
    # Include all high-cardinality metrics first
    metrics = HIGH_CARDINALITY_METRICS.copy()

    # Then add regular metrics
    metrics_needed = num_metrics - len(HIGH_CARDINALITY_METRICS)

    if metrics_needed <= len(METRIC_TEMPLATES):
        metrics.extend(METRIC_TEMPLATES[:metrics_needed])
        return metrics

    # If we need more than the templates, we'll add all templates
    metrics.extend(METRIC_TEMPLATES)

    # Then duplicate some with variations to reach the target count
    while len(metrics) < num_metrics:
        # Pick a template to duplicate
        template = random.choice(METRIC_TEMPLATES)

        # Create a variation
        variation = template.copy()
        base_name = template["name"].split(".")[0]
        suffix = random.choice(["rate", "count", "total", "max", "p95", "p99"])
        variation["name"] = f"{base_name}.{suffix}"

        # Adjust ranges
        variation["min"] = max(0, template["min"] * random.uniform(0.5, 1.5))
        variation["max"] = template["max"] * random.uniform(0.8, 1.2)

        metrics.append(variation)

    return metrics[:num_metrics]


def generate_host_configs(num_hosts: int) -> List[Dict]:
    """
    Generate host configurations with tags.

    Args:
        num_hosts: Number of hosts to generate

    Returns:
        List of host configuration dictionaries
    """
    hosts = []

    for i in range(1, num_hosts + 1):
        host_id = f"server{i:02d}"

        # Generate tags for this host
        tags = {"host": host_id}

        # Add random tags from categories
        for category, values in TAG_CATEGORIES.items():
            if category == "host":
                continue  # Already added

            # Skip high cardinality tags here - they'll be added per metric
            if category in ["customer_id", "request_id"]:
                continue

            # Not every host will have every tag
            if random.random() < 0.8:  # 80% chance to have this tag category
                tags[category] = random.choice(values)

        hosts.append({
            "id": host_id,
            "tags": tags
        })

    return hosts


def generate_value(timestamp: int, metric: Dict, last_value: float, host_id: str) -> float:
    """
    Generate a realistic metric value based on the pattern and timestamp.

    Args:
        timestamp: Unix timestamp
        metric: Metric configuration dictionary
        last_value: Previous value for this metric/host
        host_id: Host identifier

    Returns:
        A new metric value
    """
    min_val = metric["min"]
    max_val = metric["max"]
    value_range = max_val - min_val
    pattern = metric["pattern"]

    # Convert timestamp to hour of day (0-23) for daily patterns
    dt = datetime.datetime.fromtimestamp(timestamp)
    hour = dt.hour
    minute = dt.minute
    day_progress = (hour * 60 + minute) / (24 * 60)  # 0.0 to 1.0

    # Base value depends on the pattern
    if pattern == "daily_cycle":
        # Higher during work hours (9am-5pm)
        if 9 <= hour < 17:
            # Peak in the middle of the day
            work_hour = hour - 9  # 0 to 7
            work_progress = work_hour / 8.0  # 0.0 to 1.0
            daily_factor = 0.5 + 0.5 * math.sin(math.pi * (work_progress - 0.5))
            base_value = min_val + value_range * (0.6 + 0.4 * daily_factor)
        else:
            # Lower at night with some fluctuation
            base_value = min_val + value_range * random.uniform(0.1, 0.4)

    elif pattern == "gradual_increase":
        # If we have a last value, gradually increase it
        if last_value is not None:
            # Small increase with some noise
            change = value_range * random.uniform(0.001, 0.01)
            base_value = last_value + change

            # Cap at max value, and occasionally reset (like a service restart)
            if base_value > max_val or (base_value > (min_val + value_range * 0.7) and random.random() < 0.01):
                base_value = min_val + value_range * random.uniform(0.1, 0.3)
        else:
            base_value = min_val + value_range * random.uniform(0.2, 0.4)

    elif pattern == "gradual_decrease":
        # Similar to increase but decreasing
        if last_value is not None:
            change = value_range * random.uniform(0.001, 0.01)
            base_value = last_value - change

            # Floor at min value, and occasionally reset (like disk cleanup)
            if base_value < min_val or (base_value < (min_val + value_range * 0.3) and random.random() < 0.01):
                base_value = min_val + value_range * random.uniform(0.7, 0.9)
        else:
            base_value = min_val + value_range * random.uniform(0.6, 0.8)

    elif pattern == "bursty":
        # Mostly low with occasional bursts of activity
        if last_value is not None and last_value > (min_val + value_range * 0.5):
            # We're in a burst, 80% chance to continue burst
            if random.random() < 0.8:
                change = value_range * random.uniform(-0.1, 0.1)
                base_value = last_value + change
                base_value = max(min_val, min(max_val, base_value))
            else:
                # End of burst
                base_value = min_val + value_range * random.uniform(0.05, 0.2)
        else:
            # Low activity with 5% chance to start a burst
            if random.random() < 0.05:
                base_value = min_val + value_range * random.uniform(0.5, 0.8)
            else:
                base_value = min_val + value_range * random.uniform(0.05, 0.2)

    elif pattern == "stable_with_spikes":
        # Mostly stable with occasional spikes
        stable_value = min_val + value_range * 0.2

        # 10% chance of a spike
        if random.random() < 0.1:
            spike_height = random.uniform(0.3, 0.8)
            base_value = min_val + value_range * spike_height
        else:
            # Add some noise to the stable value
            noise = value_range * random.uniform(-0.05, 0.05)
            base_value = stable_value + noise

    elif pattern == "random_spikes":
        # Mostly very low with random spikes
        if random.random() < 0.15:  # 15% chance of a spike
            base_value = min_val + value_range * random.uniform(0.3, 1.0)
        else:
            base_value = min_val + value_range * random.uniform(0, 0.1)

    elif pattern == "correlated_with_cpu":
        # This would be correlated with CPU usage for the same host
        # Since we don't have that data here, we'll simulate it
        cpu_like = min_val + value_range * 0.4 * (1 + math.sin(day_progress * 2 * math.pi))
        noise = value_range * random.uniform(-0.1, 0.1)
        base_value = cpu_like + noise

    else:  # default to random
        base_value = min_val + value_range * random.random()

    # Add some noise
    noise = value_range * random.uniform(-0.02, 0.02)
    value = base_value + noise

    # Check for anomalies
    if random.random() < metric["anomaly_chance"]:
        # Generate an anomaly
        if random.random() < 0.5:
            # Spike up
            value = min_val + value_range * random.uniform(0.8, 1.2)
        else:
            # Drop down
            value = min_val + value_range * random.uniform(0, 0.2)

    # Ensure within bounds
    value = max(min_val, min(max_val, value))

    # Round to 2 decimal places for cleaner data
    return round(value, 2)


def generate_time_series_data(
        start_time: int,
        end_time: int,
        interval: int,
        metrics: List[Dict],
        hosts: List[Dict]
) -> List[Dict]:
    """
    Generate time series data points.

    Args:
        start_time: Start timestamp
        end_time: End timestamp
        interval: Interval between points in seconds
        metrics: List of metric configurations
        hosts: List of host configurations

    Returns:
        List of data points
    """
    data_points = []

    # Keep track of last values for continuity
    last_values = {}

    # For high-cardinality metrics, we'll create special customer_id maps
    # This ensures the same customer_id and request_id are used for the same timestamp
    high_cardinality_maps = {}

    # Generate data for each timestamp
    for timestamp in range(start_time, end_time + 1, interval):

        # For high-cardinality metrics, prepare a mapping of IDs for this timestamp
        timestamp_customer_id = random.choice(TAG_CATEGORIES["customer_id"])
        timestamp_request_id = random.choice(TAG_CATEGORIES["request_id"])

        # For each host
        for host in hosts:
            host_id = host["id"]
            host_tags = host["tags"]

            # For each metric
            for metric in metrics:
                metric_name = metric["name"]
                key = f"{host_id}:{metric_name}"

                # Get last value if available
                last_value = last_values.get(key)

                # Generate value
                value = generate_value(timestamp, metric, last_value, host_id)

                # Store as last value for next iteration
                last_values[key] = value

                # Create data point with base tags
                tags = host_tags.copy()

                # For high-cardinality metrics, add special tags
                if metric.get("high_cardinality", False):
                    for tag_key in metric.get("tag_keys", []):
                        if tag_key == "customer_id":
                            tags["customer_id"] = timestamp_customer_id
                        elif tag_key == "request_id":
                            tags["request_id"] = timestamp_request_id

                data_point = {
                    "timestamp": timestamp,
                    "metric": metric_name,
                    "value": value,
                    "tags": tags
                }

                data_points.append(data_point)

    return data_points


def write_csv(data_points: List[Dict], file_path: str):
    """
    Write data points to a CSV file.

    Args:
        data_points: List of data point dictionaries
        file_path: Output file path
    """
    with open(file_path, 'w', newline='') as csvfile:
        # Extract all possible tag keys
        tag_keys = set()
        for point in data_points:
            tag_keys.update(point["tags"].keys())

        # Sort tag keys for consistent columns
        tag_keys = sorted(tag_keys)

        # Create CSV field names
        fieldnames = ["timestamp", "metric", "value"] + tag_keys

        writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
        writer.writeheader()

        # Write each data point
        for point in data_points:
            row = {
                "timestamp": point["timestamp"],
                "metric": point["metric"],
                "value": point["value"]
            }

            # Add tags
            for key in tag_keys:
                row[key] = point["tags"].get(key, "")

            writer.writerow(row)


def write_json(data_points: List[Dict], file_path: str):
    """
    Write data points to a JSON file.

    Args:
        data_points: List of data point dictionaries
        file_path: Output file path
    """
    with open(file_path, 'w') as jsonfile:
        json.dump(data_points, jsonfile, indent=2)


def main():
    args = parse_args()

    # Set random seed for reproducibility
    random.seed(args.seed)

    print(f"Generating time series data from {args.start_time} to {args.end_time}")
    print(f"Interval: {args.interval} seconds")
    print(f"Metrics: {args.metrics}")
    print(f"Hosts: {args.hosts}")

    # Calculate expected data points
    expected_points = ((args.end_time - args.start_time) // args.interval) * args.metrics * args.hosts
    print(f"Expected data points: {expected_points:,} (approximately {expected_points/1000000:.2f} million)")

    # Generate metric configurations
    metrics = generate_metrics(args.metrics)
    print(f"Generated {len(metrics)} metric configurations")

    # Generate host configurations
    hosts = generate_host_configs(args.hosts)
    print(f"Generated {len(hosts)} host configurations")

    # Generate data points
    data_points = generate_time_series_data(
        args.start_time,
        args.end_time,
        args.interval,
        metrics,
        hosts
    )

    num_points = len(data_points)
    print(f"Generated {num_points} data points")

    # Determine output file
    if args.output_file:
        output_file = args.output_file
    else:
        output_file = f"time_series_data.{args.output}"

    # Write output
    if args.output == 'csv':
        write_csv(data_points, output_file)
    else:
        write_json(data_points, output_file)

    print(f"Data written to {output_file}")

    # Calculate stats for the generated data
    total_size_bytes = os.path.getsize(output_file)
    points_per_metric_host = num_points / (len(metrics) * len(hosts))
    time_range_days = (args.end_time - args.start_time) / 86400
    data_points_per_day = num_points / time_range_days if time_range_days > 0 else 0

    print("\nData Statistics:")
    print(f"Total size: {total_size_bytes / (1024*1024):.2f} MB")
    print(f"Total data points: {num_points:,}")
    print(f"Data points per metric per host: {points_per_metric_host:.1f}")
    print(f"Data points per day: {data_points_per_day:,.1f}")
    print(f"Time range: {args.end_time - args.start_time:,} seconds ({time_range_days:.1f} days)")

if __name__ == "__main__":
    main()