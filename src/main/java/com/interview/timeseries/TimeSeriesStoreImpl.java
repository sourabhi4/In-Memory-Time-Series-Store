package com.interview.timeseries;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private final long RETENTION_MS = 24 * 60 * 60 * 1000; // 24 hours
    private static final String WAL_FILE = "wal.log";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Long, List<DataPoint>>> store = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final BlockingQueue<DataPoint> walQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private final Timer retentionTimer = new Timer("RetentionCleaner", true);

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

        walQueue.offer(dp);
        return true;
    }

    @Override
    public boolean initialize() {
        lock.writeLock().lock();
        try {
            Path walPath = Paths.get(WAL_FILE);
            if (Files.exists(walPath)) {
                List<String> lines = Files.readAllLines(walPath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    try {
                        DataPoint dp = mapper.readValue(line, DataPoint.class);
                        insert(dp.getTimestamp(), dp.getMetric(), dp.getValue(), dp.getTags());
                    } catch (Exception e) {
                        System.err.println("WAL parse error: " + line);
                    }
                }
            }

            startWALWriter();

            retentionTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    cleanupOldData();
                }
            }, 60_000, 60_000);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean shutdown() {
        lock.writeLock().lock();
        try {
            running = false;
            retentionTimer.cancel();

            List<String> allPoints = new ArrayList<>();
            for (Map.Entry<String, ConcurrentSkipListMap<Long, List<DataPoint>>> entry : store.entrySet()) {
                for (List<DataPoint> points : entry.getValue().values()) {
                    for (DataPoint dp : points) {
                        allPoints.add(mapper.writeValueAsString(dp));
                    }
                }
            }

            Files.write(Paths.get(WAL_FILE), allPoints, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void startWALWriter() {
        Thread walThread = new Thread(() -> {
            Path path = Paths.get(WAL_FILE);
            while (running || !walQueue.isEmpty()) {
                try {
                    DataPoint dp = walQueue.poll(1, TimeUnit.SECONDS);
                    if (dp != null) {
                        String json = mapper.writeValueAsString(dp);
                        Files.writeString(path, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "WAL-Writer");

        walThread.setDaemon(true);
        walThread.start();
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
