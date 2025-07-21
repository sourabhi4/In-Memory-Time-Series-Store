# Time Series Store Interview Project

This is a skeleton project for implementing an in-memory time series data store with persistence capabilities.

## Project Structure

- `TimeSeriesStore.java`: Interface defining the store's operations
- `DataPoint.java`: Class representing a single data point
- `TimeSeriesStoreImpl.java`: Skeleton implementation to be completed
- `TimeSeriesStoreTest.java`: Basic test cases for the implementation

## Requirements

The implementation should:

1. Support inserting data points with timestamps, metric names, values, and tags
2. Allow querying data points by time range and tag filters
3. Persist data to survive process restarts
4. Be thread-safe for concurrent reads and writes
5. Handle the specified scale requirements (see problem statement)

## Getting Started

1. Review the interfaces and skeleton implementation
2. Think about the data structures you'll need for efficient storage and querying
3. Implement the TODOs in `TimeSeriesStoreImpl.java`
4. Run the tests to verify your implementation

## Performance Considerations

- Optimize for fast writes (insert operation)
- Ensure efficient time range queries
- Support tag-based filtering without full scans
- Minimize memory usage
- Implement effective persistence strategy


### Building and Testing

```bash
# On Linux/Mac
./gradlew build

# On Windows
gradlew.bat build

# Running tests
./gradlew test
```

## Interview Tips

- Focus on designing efficient data structures before writing code
- Consider time complexity for all operations
- Think about memory-performance tradeoffs
- Ensure thread safety without sacrificing performance

Good luck!



# Generating Sample Data

The project includes a Python script for generating realistic time series data to test your implementation. This script creates a CSV file with timestamp, metric, value, and tags that match the format needed by the time series store.

## Prerequisites

- Python 3.6 or higher

## Setup


```

## Basic Usage

Generate sample data with default settings (approximately 5 million data points):

# Make it executable
chmod +x generate_sample_data.py
python generate_sample_data.py
```

This will create a file named `time_series_data.csv` in the current directory.

## Default Configuration

The default configuration is set to generate approximately 5 million rows of data:
- Time range: Last 24 hours
- Interval: 15 seconds between data points
- 25 different metrics (including high-cardinality metrics)
- 35 different hosts
- Total rows: ~5,040,000 data points (25 metrics × 35 hosts × 24 hours × 240 points/hour)

This high-resolution dataset with 15-second intervals provides a thorough test for a time series database.

## Special High-Cardinality Metrics

The generated data includes special high-cardinality metrics to test tag filtering performance:

1. **request.latency**: Contains `customer_id` (10,000 unique values) and `request_id` (1,000 unique values) tags
2. **user.activity**: Contains `customer_id` tags with 10,000 unique values
3. **transaction.value**: Contains both `customer_id` and `request_id` with high cardinality

These metrics are excellent for testing:
- Performance with high-cardinality tag filters
- Memory usage with many unique tag values
- Index efficiency for tag lookup

## Examples

### Example 1: Generate a smaller dataset for quick tests

```bash
# Generate 1 hour of 15-second data
python generate_sample_data.py --start-time $(date -d "1 hour ago" +%s) --metrics 10 --hosts 15
```

This generates ~36,000 rows, good for quick testing during development.

### Example 2: Generate a massive dataset for extreme stress testing

```bash
# Generate 3 days of 15-second data
python generate_sample_data.py --start-time $(date -d "3 days ago" +%s) --metrics 30 --hosts 50
```

This generates ~15.1 million rows, suitable for:
- Testing memory efficiency at scale
- Performance with extremely high volumes
- System stability under heavy load