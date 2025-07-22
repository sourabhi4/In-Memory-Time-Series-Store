package com.interview.timeseries;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of the TimeSeriesStore interface.
 *
 * This is a skeleton implementation that needs to be completed.
 */
public class TimeSeriesStoreImpl implements TimeSeriesStore {

    private final long RETENTION_MS = 24 * 60 * 60 * 1000;
    private static final String WAL_FILE = "wal.log";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Long, List<DataPoint>>> store = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Timer retentionTimer = new Timer("RetentionCleaner", true);

    private BufferedWriter walWriter;
    private final Object walWriteLock = new Object();

    @Override
    public boolean insert(long timestamp, String metric, double value, Map<String, String> tags) {
        DataPoint dp = new DataPoint(timestamp, metric, value, tags != null ? new HashMap<>(tags) : new HashMap<>());

        lock.writeLock().lock();
        try {
            store
                    .computeIfAbsent(metric, m -> new ConcurrentSkipListMap<>())
                    .computeIfAbsent(timestamp, t -> Collections.synchronizedList(new ArrayList<>()))
                    .add(dp);
        } finally {
            lock.writeLock().unlock();
        }

        return writeToWAL(dp);
    }

    private boolean writeToWAL(DataPoint dp) {
        synchronized (walWriteLock) {
            try {
                if (walWriter == null) {
                    walWriter = Files.newBufferedWriter(Paths.get(WAL_FILE),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }

                String json = mapper.writeValueAsString(dp);
                walWriter.write(json);
                walWriter.newLine();
                walWriter.flush();
                return true;
            } catch (IOException e) {
                System.err.println("WAL write error: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }

    @Override
    public boolean initialize() {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(WAL_FILE);
            walWriter = Files.newBufferedWriter(path,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            Path walPath = path;
            if (Files.exists(walPath)) {
                List<String> lines = Files.readAllLines(walPath, StandardCharsets.UTF_8);
                System.out.println("Loading " + lines.size() + " entries from WAL");

                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        DataPoint dp = mapper.readValue(line, DataPoint.class);
                        store
                                .computeIfAbsent(dp.getMetric(), m -> new ConcurrentSkipListMap<>())
                                .computeIfAbsent(dp.getTimestamp(), t -> Collections.synchronizedList(new ArrayList<>()))
                                .add(dp);
                    } catch (Exception e) {
                        System.err.println("WAL parse error for line: " + line + " - " + e.getMessage());
                    }
                }
                System.out.println("Loaded data from WAL successfully");
            }

            retentionTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    cleanupOldData();
                }
            }, 60_000, 60_000);

            return true;
        } catch (IOException e) {
            System.err.println("Failed to initialize WAL writer: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean shutdown() {
        try {
            retentionTimer.cancel();

            synchronized (walWriteLock) {
                if (walWriter != null) {
                    try {
                        walWriter.close();
                        walWriter = null;
                    } catch (IOException e) {
                        System.err.println("Error closing WAL writer: " + e.getMessage());
                    }
                }
            }

            System.out.println("Store shutdown completed successfully");
            return true;
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<DataPoint> query(String metric, long timeStart, long timeEnd, Map<String, String> filters) {
        lock.readLock().lock();
        try {
            NavigableMap<Long, List<DataPoint>> metricData = store.get(metric);
            if (metricData == null) return Collections.emptyList();

            NavigableMap<Long, List<DataPoint>> range = metricData.subMap(timeStart, true, timeEnd, false);
            List<DataPoint> result = new ArrayList<>();

            for (List<DataPoint> points : range.values()) {
                for (DataPoint dp : points) {
                    if (matchesFilter(dp, filters)) {
                        result.add(dp);
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean matchesFilter(DataPoint dp, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (!entry.getValue().equals(dp.getTags().get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private void cleanupOldData() {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        lock.writeLock().lock();
        try {
            for (ConcurrentSkipListMap<Long, List<DataPoint>> timeMap : store.values()) {
                timeMap.headMap(cutoff, false).clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}