package com.interview.timeseries;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Interface for the Time Series Store.
 * 
 * This store provides functionality for storing and querying time series data.
 * The implementation should be thread-safe and provide persistence.
 */
public interface TimeSeriesStore {

    /**
     * Inserts a new data point into the store.
     * 
     * @param timestamp The Unix timestamp in milliseconds
     * @param metric The name of the metric
     * @param value The value of the data point
     * @param tags Optional tags as key-value pairs (can be null or empty)
     * @return true if the insert was successful, false otherwise
     */
    boolean insert(long timestamp, String metric, double value, Map<String, String> tags);
    
    /**
     * Queries data points for a metric within a time range with optional filters.
     * 
     * @param metric The name of the metric to query
     * @param timeStart The inclusive start time in milliseconds
     * @param timeEnd The exclusive end time in milliseconds
     * @param filters Optional tag filters as key-value pairs (can be null or empty)
     * @return A list of matching data points
     */
    List<DataPoint> query(String metric, long timeStart, long timeEnd, Map<String, String> filters);
    
    /**
     * Initializes the store, including recovery from persistent storage if available.
     * This method should be called before any other operations.
     * 
     * @return true if initialization was successful, false otherwise
     */
    boolean initialize();
    
    /**
     * Properly shuts down the store, ensuring data is persisted.
     * This method should be called before application exit.
     * 
     * @return true if shutdown was successful, false otherwise
     */
    boolean shutdown();
}
