package vm.search.algorithm;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.metricSpace.distance.DistanceFunctionInterface;

/**
 *
 * @author Vlada
 * @param <T>
 */
public abstract class SearchingAlgorithm<T> {

    private static final Logger LOG = Logger.getLogger(SearchingAlgorithm.class.getName());
    public static final Integer BATCH_SIZE = 100000;

    protected final ConcurrentHashMap<Object, AtomicInteger> distCompsPerQueries = new ConcurrentHashMap();
    protected final ConcurrentHashMap<Object, AtomicLong> timesPerQueries = new ConcurrentHashMap();

    public abstract List<Object> candSetKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams);

    /**
     * Implicit implementation that just reranks the candidate set defined in
     * this algorithm. Feel free to override with another complete search.
     *
     * @param metricSpace
     * @param queryObject
     * @param k
     * @param objects
     * @param additionalParams
     * @return
     */
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        List<Object> candSet = candSetKnnSearch(metricSpace, queryObject, k, objects, additionalParams);
        Dataset dataset = (Dataset) additionalParams[0];
        return rerankCandidateSet(metricSpace, queryObject, k, dataset.getDatasetName(), dataset.getKeyValueStorage(), candSet);
    }

    public Map.Entry<Object, Float> adjustAndReturnLastEntry(TreeSet<Map.Entry<Object, Float>> currAnswer, int k) {
        int size = currAnswer.size();
        if (size < k) {
            return null;
        }
        while (currAnswer.size() > k) {
            currAnswer.remove(currAnswer.last());
        }
        return currAnswer.last();
    }

    public float adjustAndReturnSearchRadius(TreeSet<Map.Entry<Object, Float>> currAnswer, int k) {
        int size = currAnswer.size();
        if (size < k) {
            return Float.MAX_VALUE;
        }
        while (currAnswer.size() > k) {
            currAnswer.remove(currAnswer.last());
        }
        return currAnswer.last().getValue();
    }

    public TreeSet<Map.Entry<Object, Float>> rerankCandidateSet(AbstractMetricSpace<T> metricSpace, Object queryObj, int k, String datasetName, Map<Object, Object> mapOfAllFullObjects, List<Object> candsIDs) {
        DistanceFunctionInterface df = metricSpace.getDistanceFunctionForDataset(datasetName);
        T queryObjData = metricSpace.getDataOfMetricObject(queryObj);
        TreeSet<Map.Entry<Object, Float>> ret = new TreeSet<>(new Tools.MapByValueComparator());
        if (mapOfAllFullObjects == null) {
            for (int i = 0; i < Math.min(candsIDs.size(), k); i++) {
                Object id = candsIDs.get(i);
                ret.add(new AbstractMap.SimpleEntry<>(id, (float) i));
            }
            return ret;
        }
        for (Object candID : candsIDs) {
            T metricObjectData;
            try {
                metricObjectData = (T) mapOfAllFullObjects.get(candID);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Something wrong happened in the data map. Trying to repeat it. CandID: " + candID, ex);
                continue;
            }
            float distance = df.getDistance(queryObjData, metricObjectData);
            ret.add(new AbstractMap.SimpleEntry<>(candID, distance));
            if (ret.size() > k) {
                ret.remove(ret.last());
            }
        }
        return ret;
    }

    /**
     * @param metricSpace
     * @param queryObjects
     * @param k
     * @param objects
     * @param additionalParams
     * @return evaluates all query objects in parallel. Parallelisation is done
     * over the query objects
     */
    public TreeSet<Map.Entry<Object, Float>>[] completeKnnFilteringWithQuerySet(AbstractMetricSpace<T> metricSpace, List<Object> queryObjects, int k, Iterator<Object> objects, Object... additionalParams) {
        final TreeSet<Map.Entry<Object, Float>>[] ret = new TreeSet[queryObjects.size()];
        final List<Object> batch = new ArrayList<>();
        for (int i = 0; i < queryObjects.size(); i++) {
            Object qID = metricSpace.getIDOfMetricObject(queryObjects.get(i));
            timesPerQueries.put(qID, new AtomicLong());
            ret[i] = new TreeSet<>(new Tools.MapByValueComparator());
        }
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(vm.javatools.Tools.PARALELISATION);
        int batchCounter = 0;
        while (objects.hasNext()) {
            try {
                batch.clear();
                long t = -System.currentTimeMillis();
                for (int i = 0; i < BATCH_SIZE && objects.hasNext(); i++) {
                    batch.add(objects.next());
                    if (i % 50000 == 0 && t + System.currentTimeMillis() > 5000) {
                        LOG.log(Level.INFO, "Loading objects into the batch. {0} Loaded", i);
                        t = -System.currentTimeMillis();
                    }
                }
                if (batch.isEmpty()) {
                    break;
                }
                batchCounter++;
                System.gc();
                CountDownLatch latch = new CountDownLatch(queryObjects.size());
                final AbstractMetricSpace<T> metricSpaceFinal = metricSpace;
                final int batchFinal = batchCounter;
                for (int i = 0; i < queryObjects.size(); i++) {
                    final Object queryObject = queryObjects.get(i);
                    final TreeSet<Map.Entry<Object, Float>> answerToQuery = ret[i];
                    final int iFinal = i + 1;
                    threadPool.execute(() -> {
                        TreeSet<Map.Entry<Object, Float>> completeKnnSearch = completeKnnSearch(metricSpaceFinal, queryObject, k, batch.iterator(), answerToQuery, additionalParams);
                        answerToQuery.addAll(completeKnnSearch);
                        latch.countDown();
                        adjustAndReturnSearchRadius(answerToQuery, k);
                        LOG.log(Level.INFO, "Query obj {0} evaluated in the batch {1} (batch size: {2})", new Object[]{iFinal, batchFinal, BATCH_SIZE});
                    });
                }
                latch.await();
                LOG.log(Level.INFO, "Batch {0} processed", batchCounter);
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        threadPool.shutdown();
        return ret;
    }

    /**
     * @param metricSpace
     * @param queryObjects
     * @param k
     * @param kCandSetMaxSize
     * @param keyValueStorage
     * @param additionalParams
     * @return
     */
    public TreeSet<Map.Entry<Object, Float>>[] completeKnnSearchWithPartitioningForQuerySet(AbstractMetricSpace<T> metricSpace, List<Object> queryObjects, int k, int kCandSetMaxSize, Map<Object, T> keyValueStorage, Object... additionalParams) {
        final TreeSet<Map.Entry<Object, Float>>[] ret = new TreeSet[queryObjects.size()];
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(vm.javatools.Tools.PARALELISATION);
        try {
            CountDownLatch latch = new CountDownLatch(queryObjects.size());
            final AbstractMetricSpace<T> metricSpaceFinal = metricSpace;
            for (int i = 0; i < queryObjects.size(); i++) {
                final Object queryObject = queryObjects.get(i);
                final int iFinal = i;
                threadPool.execute(() -> {
                    Object[] params = Tools.concatArrays(new Object[]{keyValueStorage, kCandSetMaxSize}, additionalParams);
                    ret[iFinal] = completeKnnSearch(metricSpaceFinal, queryObject, k, null, params);
                    latch.countDown();
                });
            }
            latch.await();
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        threadPool.shutdown();
        return ret;
    }

    public void incTime(Object qId, long time) {
        AtomicLong ai = timesPerQueries.get(qId);
        if (ai != null) {
            ai.addAndGet(time);
        } else {
            timesPerQueries.put(qId, new AtomicLong(time));
        }
    }

    protected void incDistsComps(Object qId) {
        AtomicInteger ai = distCompsPerQueries.get(qId);
        if (ai != null) {
            ai.incrementAndGet();
        } else {
            distCompsPerQueries.put(qId, new AtomicInteger(1));
        }
    }

    protected void incDistsComps(Object qId, int byValue) {
        AtomicInteger ai = distCompsPerQueries.get(qId);
        if (ai != null) {
            ai.addAndGet(byValue);
        } else {
            ai = new AtomicInteger(byValue);
            distCompsPerQueries.put(qId, ai);
        }
    }

    public Map<Object, AtomicInteger> getDistCompsPerQueries() {
        return Collections.unmodifiableMap(distCompsPerQueries);
    }

    public Map<Object, AtomicLong> getTimesPerQueries() {
        return Collections.unmodifiableMap(timesPerQueries);
    }

    public int getDistCompsForQuery(Object qId) {
        return distCompsPerQueries.get(qId).get();
    }

    public long getTimeOfQuery(Object qId) {
        return timesPerQueries.get(qId).get();
    }

    public Map<Object, AtomicLong>[] getAddditionalStats() {
        return null;
    }

    public abstract String getResultName();

}
