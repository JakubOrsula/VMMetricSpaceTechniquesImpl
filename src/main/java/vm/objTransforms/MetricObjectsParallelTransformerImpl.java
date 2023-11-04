package vm.objTransforms;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.javatools.Tools.MetricObjectArrayIterator;
import vm.metricSpace.AbstractMetricSpacesStorage;
import vm.metricSpace.AbstractMetricSpacesStorage.OBJECT_TYPE;
import vm.metricSpace.MainMemoryDatasetCache;

/**
 *
 * @author Vlada
 */
public class MetricObjectsParallelTransformerImpl {

    public static final Integer BATCH_SIZE = 1000000;
    private static final Logger LOG = Logger.getLogger(MetricObjectsParallelTransformerImpl.class.getName());

    private final MetricObjectTransformerInterface transformer;
    private final AbstractMetricSpacesStorage metricSpaceStorage;
    private final String resultDatasetName;
    private final String resultQuerysetName;
    private final String resultPivotsName;

    public MetricObjectsParallelTransformerImpl(MetricObjectTransformerInterface transformer, AbstractMetricSpacesStorage metricSpaceStorage, String resultDatasetName, String resultQuerysetName, String resultPivotsName) {
        this.transformer = transformer;
        this.metricSpaceStorage = metricSpaceStorage;
        this.resultDatasetName = resultDatasetName;
        this.resultQuerysetName = resultQuerysetName;
        this.resultPivotsName = resultPivotsName;
    }

    public void processIteratorSequentially(Iterator<Object> iterator, OBJECT_TYPE objType, Object... additionalParamsToStoreWithNewDataset) {
        this.processIteratorInParallel(iterator, objType, 1, additionalParamsToStoreWithNewDataset);
    }

    public void processIteratorInParallel(Iterator<Object> itOverMetricObjects, OBJECT_TYPE objType, int parallelisation, Object... additionalParamsToStoreWithNewDataset) {
        LOG.log(Level.INFO, "Start parallel transformation with parallelisation {0}", parallelisation);
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(parallelisation);
        int processedObjects = 0;
        boolean first = true;
        while (itOverMetricObjects.hasNext()) {
            List<Object> batch = Tools.getObjectsFromIterator(0, BATCH_SIZE, itOverMetricObjects);
            Object[] concatArrays = Tools.concatArrays(new Object[]{first}, additionalParamsToStoreWithNewDataset);
            processedObjects += processBatch(batch, objType, parallelisation, threadPool, concatArrays);
//            System.gc();
            LOG.log(Level.INFO, "Transformed and stored {0} metric objects", processedObjects);
            first = false;
        }
        threadPool.shutdown();
    }

    private int processBatch(List<Object> batch, OBJECT_TYPE objType, int parallelisation, ExecutorService threadPool, Object... additionalParamsToStoreWithNewDataset) {
        try {
            Object[][] objProcessedByThreads = splitBatchPerThreads(batch, parallelisation);
            CountDownLatch latch = new CountDownLatch(parallelisation);
            Object[] transformedMetricObjects = new Object[batch.size()];
            AtomicInteger retPos = new AtomicInteger(-1);
            for (int i = 0; i < parallelisation; i++) {
                final int iFinal = i;
                final Object[] objProcessedByThread = objProcessedByThreads[iFinal];
                threadPool.execute(() -> {
                    for (int index = 0; index < objProcessedByThread.length; index++) {
                        Object object = objProcessedByThread[index];
                        if (object != null) {
                            final Object transformMetricObject = transformer.transformMetricObject(object);
                            transformedMetricObjects[retPos.incrementAndGet()] = transformMetricObject;
                        }
                    }
                    latch.countDown();
                });
            }
            latch.await();
            if (retPos.get() + 1 != batch.size()) {
                LOG.log(Level.SEVERE, "Warning - wrong transformation position {0} vs. {1}", new Object[]{retPos.get() + 1, batch.size()});
                throw new Error();
            }
            MetricObjectArrayIterator it = new MetricObjectArrayIterator(transformedMetricObjects);
            MainMemoryDatasetCache cache = null;
            if (additionalParamsToStoreWithNewDataset.length != 0) {
                for (Object param : additionalParamsToStoreWithNewDataset) {
                    if (param instanceof MainMemoryDatasetCache) {
                        cache = (MainMemoryDatasetCache) param;
                    }
                }
            }
            switch (objType) {
                case DATASET_OBJECT: {
                    metricSpaceStorage.storeObjectsToDataset(it, -1, resultDatasetName, additionalParamsToStoreWithNewDataset);
                    if (cache != null) {
                        cache.addAllDataObjects(transformedMetricObjects);
                    }
                    break;
                }
                case PIVOT_OBJECT: {
                    List<Object> pivots = Tools.getObjectsFromIterator(it);
                    metricSpaceStorage.storePivots(pivots, resultPivotsName, additionalParamsToStoreWithNewDataset);
                    if (cache != null) {
                        cache.addPivots(transformedMetricObjects);
                    }
                    break;
                }
                case QUERY_OBJECT: {
                    List<Object> queryObjects = Tools.getObjectsFromIterator(it);
                    metricSpaceStorage.storeQueryObjects(queryObjects, resultQuerysetName, additionalParamsToStoreWithNewDataset);
                    if (cache != null) {
                        cache.addQueries(transformedMetricObjects);
                    }
                    break;
                }
            }
            LOG.log(Level.INFO, "Stored");
            return transformedMetricObjects.length;
        } catch (InterruptedException ex) {
            Logger.getLogger(MetricObjectsParallelTransformerImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return -1;
    }

    private Object[][] splitBatchPerThreads(List<Object> batch, int paralelisation) {
        int objProccessedPerThread = (int) Math.ceil(batch.size() / (float) paralelisation);
        Object[][] ret = new Object[paralelisation][objProccessedPerThread];
        Iterator<Object> it = batch.iterator();
        for (int j = 0; j < objProccessedPerThread && it.hasNext(); j++) {
            for (int i = 0; i < paralelisation && it.hasNext(); i++) {
                ret[i][j] = it.next();
            }
        }
        return ret;
    }

}
