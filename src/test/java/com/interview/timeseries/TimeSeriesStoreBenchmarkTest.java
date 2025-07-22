package com.interview.timeseries;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class TimeSeriesStoreBenchmarkTest {

    private static TimeSeriesStoreImpl store;

    @BeforeClass
    public static void setup() throws Exception {
        store = new TimeSeriesStoreImpl();
        store.initialize();

        String smallDataFile = "small_data.json";
        if (Files.exists(Paths.get(smallDataFile))) {
            loadFromJsonFile(smallDataFile, store);
        }
    }

    @AfterClass
    public static void teardown() {
        store.shutdown();
    }

    @Test
    public void testInsertThroughput() throws InterruptedException {
        int threads = 10;
        int insertsPerThread = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        long start = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                long baseTimestamp = System.currentTimeMillis();
                for (int j = 0; j < insertsPerThread; j++) {
                    String metric = "metric_" + ThreadLocalRandom.current().nextInt(100000);
                    Map<String, String> tags = Map.of("host", "host_" + ThreadLocalRandom.current().nextInt(100));
                    store.insert(baseTimestamp + j, metric, Math.random() * 100, tags);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(60, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - start;

        long totalInserts = (long) threads * insertsPerThread;
        double insertsPerSec = totalInserts / (duration / 1000.0);

        System.out.printf("Insert throughput: %.2f inserts/sec\n", insertsPerSec);
        assertTrue("Insert threads did not finish in time", finished);
        assertTrue("Insert throughput atLeast 10,000 inserts/sec", insertsPerSec >= 10000);
    }

    @Test
    public void testQueryThroughput() throws InterruptedException {
        int preInsertThreads = 5;
        int preInsertsPerThread = 5000;
        ExecutorService preInsertExecutor = Executors.newFixedThreadPool(preInsertThreads);
        for (int i = 0; i < preInsertThreads; i++) {
            preInsertExecutor.submit(() -> {
                long baseTimestamp = System.currentTimeMillis();
                for (int j = 0; j < preInsertsPerThread; j++) {
                    String metric = "metric_" + ThreadLocalRandom.current().nextInt(100000);
                    Map<String, String> tags = Map.of("node", "node_" + ThreadLocalRandom.current().nextInt(100));

                    store.insert(baseTimestamp + j, metric, Math.random() * 100, tags);
                }
            });
        }
        preInsertExecutor.shutdown();
        preInsertExecutor.awaitTermination(60, TimeUnit.SECONDS);

        int threads = 10;
        int queriesPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        long queryStart = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < queriesPerThread; j++) {
                    String metric = "metric_" + ThreadLocalRandom.current().nextInt(100000);
                    Map<String, String> tags = Map.of("node", "node_" + ThreadLocalRandom.current().nextInt(100));

                    long now = System.currentTimeMillis();
                    store.query(metric, now - 3600_000, now, tags);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(60, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - queryStart;

        long totalQueries = (long) threads * queriesPerThread;
        double queriesPerSec = totalQueries / (duration / 1000.0);

        System.out.printf("Query throughput: %.2f queries/sec\n", queriesPerSec);
        assertTrue("Query threads did not finish in time", finished);
        assertTrue("Query throughput below 1,000 queries/sec", queriesPerSec >= 1000);
    }


    @Test
    public void testRetentionCleanup() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        long now = System.currentTimeMillis();
        String metric = "retention_test_metric";

        for (long ts = now - 25 * 3600_000; ts < now - 24 * 3600_000; ts += 60_000) {
            store.insert(ts, metric, Math.random() * 100, Map.of("host", "old_host"));
        }

        for (long ts = now - 23 * 3600_000; ts < now; ts += 60_000) {
            store.insert(ts, metric, Math.random() * 100, Map.of("host", "new_host"));
        }

        Method cleanupMethod = store.getClass().getDeclaredMethod("cleanupOldData");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(store);

        List<DataPoint> results = store.query(metric, now - 26 * 3600_000, now, Map.of());

        boolean hasOldData = results.stream().anyMatch(dp -> dp.getTimestamp() < now - 24 * 3600_000);
        boolean hasNewData = results.stream().anyMatch(dp -> dp.getTimestamp() >= now - 24 * 3600_000);

        System.out.printf("Retention cleanup: hasOldData=%b, hasNewData=%b\n", hasOldData, hasNewData);
        assertFalse("Old data should be removed by retention cleanup", hasOldData);
        assertTrue("Recent data should be retained", hasNewData);
    }

    private static void loadFromJsonFile(String filePath, TimeSeriesStoreImpl store) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<DataPoint> points = mapper.readValue(
                new File(filePath),
                new TypeReference<>() {
                }
        );
        for (DataPoint dp : points) {
            store.insert(dp.getTimestamp(), dp.getMetric(), dp.getValue(), dp.getTags());
        }
    }

}
