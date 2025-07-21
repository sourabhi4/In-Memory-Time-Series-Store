package com.interview.timeseries;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single data point in the time series store.
 */
public class DataPoint {
    private final long timestamp;
    private final String metric;
    private final double value;
    private final Map<String, String> tags;

    /**
     * Creates a new data point.
     *
     * @param timestamp The Unix timestamp in milliseconds
     * @param metric The name of the metric
     * @param value The value of the data point
     * @param tags Optional tags as key-value pairs (can be null)
     */
    @JsonCreator
    public DataPoint(
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("metric") String metric,
            @JsonProperty("value") double value,
            @JsonProperty("tags") Map<String, String> tags
    ) {
        this.timestamp = timestamp;
        this.metric = metric;
        this.value = value;
        this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMetric() {
        return metric;
    }

    public double getValue() {
        return value;
    }

    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    @Override
    public String toString() {
        return "DataPoint{" +
                "timestamp=" + timestamp +
                ", metric='" + metric + '\'' +
                ", value=" + value +
                ", tags=" + tags +
                '}';
    }
}
