package vm.evaluatorsToBeUsed;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.queryResults.QueryNearestNeighboursStoreInterface;

/**
 *
 * @author Vlada
 */
public class GroundTruthEvaluator {

    private static final Logger LOG = Logger.getLogger(GroundTruthEvaluator.class.getName());
    public static final Integer BATCH_SIZE = 5000000;

    private final AbstractMetricSpace metricSpace;
    private final DistanceFunctionInterface distanceFunction;
    private final QueryNearestNeighboursStoreInterface storage;
    private final List<Object> queryObjectsIDs;
    private final List<Object> queryObjectsData;
    private final int k;
    private float range;

    public GroundTruthEvaluator(AbstractMetricSpace metricSpace, DistanceFunctionInterface distanceFunction, List<Object> queryObjects, int k, QueryNearestNeighboursStoreInterface storage) {
        this(metricSpace, distanceFunction, queryObjects, k, Float.MAX_VALUE, storage);
    }

    public GroundTruthEvaluator(AbstractMetricSpace metricSpace, DistanceFunctionInterface distanceFunction, List<Object> queryObjects, float range, QueryNearestNeighboursStoreInterface storage) {
        this(metricSpace, distanceFunction, queryObjects, Integer.MAX_VALUE, range, storage);
    }

    /**
     *
     * @param metricSpace implementation of your matric space
     * @param distanceFunction
     * @param queryObjects
     * @param k
     * @param range
     * @param storage Interface used to storeMetricObject the computed PCA
     * transformation
     */
    public GroundTruthEvaluator(AbstractMetricSpace metricSpace, DistanceFunctionInterface distanceFunction, List<Object> queryObjects, int k, float range, QueryNearestNeighboursStoreInterface storage) {
        this.metricSpace = metricSpace;
        this.storage = storage;
        this.queryObjectsIDs = getIDsOfObjects(queryObjects);
        this.queryObjectsData = getDataOfObjects(queryObjects);
        this.k = k;
        this.range = range;
        this.distanceFunction = distanceFunction;
    }

    public TreeSet<Entry<Object, Float>>[] evaluateIteratorSequentially(Iterator<Object> itOverMetricObjects, Object... paramsToStoreWithGroundTruth) {
        return evaluateIterator(null, itOverMetricObjects, paramsToStoreWithGroundTruth);
    }

    public TreeSet<Entry<Object, Float>>[] evaluateIteratorInParallel(Iterator<Object> itOverMetricObjects, Object... paramsToStoreWithGroundTruth) {
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(vm.javatools.Tools.PARALELISATION);
        return evaluateIterator(threadPool, itOverMetricObjects, paramsToStoreWithGroundTruth);
    }

    public TreeSet<Entry<Object, Float>>[] evaluateIterator(ExecutorService threadPool, Iterator<Object> itOverMetricObjects, Object... paramsToStoreWithGroundTruth) {
        TreeSet<Entry<Object, Float>>[] queryResults = initKNNResultSets(queryObjectsData.size());
        int counter = 0;
        while (itOverMetricObjects.hasNext()) {
            List<Object> batch = Tools.getObjectsFromIterator(0, BATCH_SIZE, itOverMetricObjects);
            queryResults = processBatch(batch, queryResults, threadPool);
            counter += batch.size();
            LOG.log(Level.INFO, "Evaluated queries for {0} objects from the dataset", counter);
            System.gc();
        }
        if (storage != null) {
            String datasetName = null;
            String querySetName = null;
            if (paramsToStoreWithGroundTruth.length > 1) {
                datasetName = paramsToStoreWithGroundTruth[0].toString();
                querySetName = paramsToStoreWithGroundTruth[1].toString();
            }
            storage.storeQueryResults(queryObjectsIDs, queryResults, datasetName, querySetName, "ground_truth");
        }
        if (threadPool != null) {
            threadPool.shutdown();
        }
        return queryResults;
    }

    private TreeSet<Entry<Object, Float>>[] processBatch(List<Object> batch, TreeSet<Entry<Object, Float>>[] queryResults, ExecutorService threadPool) {
        try {
            LOG.log(Level.INFO, "Start parallel evaluation of queries. Batch size: {0} metric objects", batch.size());
            CountDownLatch latch = new CountDownLatch(queryObjectsData.size());
            for (int i = 0; i < queryObjectsData.size(); i++) {
                final Object queryObjectData = queryObjectsData.get(i);
                final Object qID = queryObjectsIDs.get(i);
                final TreeSet<Entry<Object, Float>> queryResult = queryResults[i];
                UpdateAnswers updateAnswers = new UpdateAnswers(batch, latch, queryObjectData, qID, queryResult);
                if (threadPool != null) {
                    threadPool.execute(updateAnswers);
                } else {
                    updateAnswers.run();
                }
            }
            latch.await();
            LOG.log(Level.INFO, "Batch processed");
            System.gc();
            return queryResults;
        } catch (Throwable ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static TreeSet<Entry<Object, Float>>[] initKNNResultSets(int numberOfQueries) {
        TreeSet<Entry<Object, Float>>[] ret = new TreeSet[numberOfQueries];
        for (int i = 0; i < numberOfQueries; i++) {
            ret[i] = new TreeSet<>(new Tools.MapByValueComparator());
        }
        return ret;
    }

    private void updateSimQueryAnswer(Object queryObjectData, Object obj, TreeSet<Entry<Object, Float>> queryResult) {
        Object datasetObjData = metricSpace.getDataOfMetricObject(obj);
        float distance = distanceFunction.getDistance(queryObjectData, datasetObjData);
        if (distance > range) {
            return;
        }
        Object idOfMetricObject = metricSpace.getIDOfMetricObject(obj);
        Entry entry = new AbstractMap.SimpleEntry<>(idOfMetricObject, distance);
        if (queryResult.size() < k) {
            queryResult.add(entry);
            return;
        }
        float distThreshold = queryResult.last().getValue();
        if (distance < distThreshold) {
            queryResult.add(entry);
            queryResult.remove(queryResult.last());
        }
    }

    private List<Object> getDataOfObjects(List<Object> queryObjects) {
        List<Object> ret = new ArrayList<>();
        for (Object queryObject : queryObjects) {
            ret.add(metricSpace.getDataOfMetricObject(queryObject));
        }
        return ret;
    }

    private List<Object> getIDsOfObjects(List<Object> queryObjects) {
        List<Object> ret = new ArrayList<>();
        for (Object queryObject : queryObjects) {
            ret.add(metricSpace.getIDOfMetricObject(queryObject));
        }
        return ret;
    }

    private class UpdateAnswers implements Runnable {

        private final List<Object> batch;
        private final CountDownLatch latch;
        private final Object qData;
        private final Object qID;
        private final TreeSet<Entry<Object, Float>> queryResult;

        public UpdateAnswers(List<Object> batch, CountDownLatch latch, Object qData, Object qID, TreeSet<Entry<Object, Float>> queryResult) {
            this.batch = batch;
            this.latch = latch;
            this.qData = qData;
            this.qID = qID;
            this.queryResult = queryResult;
        }

        @Override
        public void run() {
            long t = -System.currentTimeMillis();
            for (int i1 = 0; i1 < batch.size(); i1++) {
                final Object obj = batch.get(i1);
                updateSimQueryAnswer(qData, obj, queryResult);
            }
            t += System.currentTimeMillis();
            if (t > 10000) {
                LOG.log(Level.INFO, "Latch: {0}, time: {1}, {2}", new Object[]{latch.getCount(), t, qID});
            } else {
                LOG.log(Level.INFO, "Latch: {0}, time: {1}, {2}", new Object[]{latch.getCount(), t, qID});
            }
            latch.countDown();
        }

    }

}
