package vm.metricSpace.datasetPartitioning.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.ToolsMetricDomain;
import vm.metricSpace.datasetPartitioning.AbstractDatasetPartitioning;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.impl.CosineDistance;
import vm.metricSpace.datasetPartitioning.StorageDatasetPartitionsInterface;

/**
 *
 * @author Vlada
 */
public class VoronoiPartitioning extends AbstractDatasetPartitioning {

    public static final Logger LOG = Logger.getLogger(VoronoiPartitioning.class.getName());

    protected final DistanceFunctionInterface df;
    protected final Map<Object, Object> pivots;
    protected final List<Object> pivotsList;

    public VoronoiPartitioning(AbstractMetricSpace metricSpace, DistanceFunctionInterface df, List<Object> pivots) {
        super(metricSpace);
        this.df = df;
        this.pivots = ToolsMetricDomain.getMetricObjectsAsIdObjectMap(metricSpace, pivots, true);
        this.pivotsList = pivots;
    }

    @Override
    public Map<Object, SortedSet<Object>> partitionObjects(Iterator<Object> dataObjects, String datasetName, StorageDatasetPartitionsInterface storage, Object... params) {
        Integer pivotCountUsedInTheFileName = (Integer) params[0];
        Map<Object, SortedSet<Object>> ret = new HashMap<>();
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(vm.javatools.Tools.PARALELISATION);
        int batchCounter = 0;
        long size = 0;
        Map<Object, Float> lengthOfPivotVectors = null;
        if (df instanceof CosineDistance) {
            lengthOfPivotVectors = ToolsMetricDomain.getVectorsLength(pivotsList, metricSpace);
        }

        while (dataObjects.hasNext()) {
            try {
                CountDownLatch latch = new CountDownLatch(vm.javatools.Tools.PARALELISATION);
                AbstractDatasetPartitioning.BatchProcessor[] processes = new AbstractDatasetPartitioning.BatchProcessor[vm.javatools.Tools.PARALELISATION];
                for (int j = 0; j < vm.javatools.Tools.PARALELISATION; j++) {
                    batchCounter++;
                    List batch = Tools.getObjectsFromIterator(dataObjects, BATCH_SIZE);
                    size += batch.size();
                    Map<Object, Float> lengthOfBatchVectors = null;
                    if (df instanceof CosineDistance) {
                        lengthOfBatchVectors = ToolsMetricDomain.getVectorsLength(batch, metricSpace);
                    }
                    processes[j] = getBatchProcesor(batch, metricSpace, latch, lengthOfPivotVectors, lengthOfBatchVectors);
                    threadPool.execute(processes[j]);
                }
                latch.await();
                for (int j = 0; j < vm.javatools.Tools.PARALELISATION; j++) {
                    Map<Object, SortedSet<Object>> partial = processes[j].getRet();
                    for (Map.Entry<Object, SortedSet<Object>> partialEntry : partial.entrySet()) {
                        Object key = partialEntry.getKey();
                        if (!ret.containsKey(key)) {
                            SortedSet<Object> set = new TreeSet<>();
                            ret.put(key, set);
                        }
                        ret.get(key).addAll(partialEntry.getValue());
                    }
                }
                LOG.log(Level.INFO, "Voronoi partitioning done for {0} objects", size);
            } catch (InterruptedException ex) {
                Logger.getLogger(VoronoiPartitioning.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        threadPool.shutdown();
        if (storage != null) {
            storage.store(ret, datasetName, pivotCountUsedInTheFileName);
        }
        return ret;
    }

    protected AbstractDatasetPartitioning.BatchProcessor getBatchProcesor(List batch, AbstractMetricSpace metricSpace, CountDownLatch latch, Map<Object, Float> pivotLengths, Map<Object, Float> objectsLengths) {
        return new ProcessBatch(batch, metricSpace, latch, pivotLengths, objectsLengths);
    }

    private class ProcessBatch extends AbstractDatasetPartitioning.BatchProcessor {

        public ProcessBatch(List batch, AbstractMetricSpace metricSpace, CountDownLatch latch, Map<Object, Float> pivotLengths, Map<Object, Float> objectsLengths) {
            super(batch, metricSpace, latch, pivotLengths, objectsLengths);
        }

        @Override
        public void run() {
            long t = -System.currentTimeMillis();
            Iterator dataObjects = batch.iterator();
            for (int i = 0; dataObjects.hasNext(); i++) {
                Object o = dataObjects.next();
                Object oData = metricSpace.getDataOfMetricObject(o);
                Object oID = metricSpace.getIDOfMetricObject(o);
                float minDist = Float.MAX_VALUE;
                Object pivotWithMinDist = null;
                Float oLength = objectsLengths.get(oID);
                for (Map.Entry<Object, Object> pivot : pivots.entrySet()) {
                    Object pivotID = pivot.getKey();
                    float dist;
                    dist = df.getDistance(oData, pivot.getValue(), oLength, pivotLengths.get(pivotID));
                    if (dist < minDist) {
                        minDist = dist;
                        pivotWithMinDist = pivotID;
                    }
                }
                if (!ret.containsKey(pivotWithMinDist)) {
                    ret.put(pivotWithMinDist, new TreeSet<>());
                }
                ret.get(pivotWithMinDist).add(oID);
            }
            latch.countDown();
            t += System.currentTimeMillis();
            LOG.log(Level.INFO, "Batch finished in {0} ms", t);
        }
    }
}
