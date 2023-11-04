package vm.metricSpace.distance.bounding.twopivots.learning;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.ToolsMetricDomain;
import vm.metricSpace.distance.DistanceFunctionInterface;
import static vm.metricSpace.distance.bounding.twopivots.impl.PtolemaiosFilteringWithLimitedAnglesSimpleCoef.CONSTANT_FOR_PRECISION;
import vm.metricSpace.distance.bounding.twopivots.storeLearned.PtolemyInequalityWithLimitedAnglesCoefsStoreInterface;

/**
 *
 * @author xmic
 * @param <T>
 */
public class LearningPtolemyInequalityWithLimitedAngles<T> {

    public static final Logger LOG = Logger.getLogger(LearningPtolemyInequalityWithLimitedAngles.class.getName());

    public static final Boolean ALL_PIVOT_PAIRS = true;

    private final String resultName;
    private final AbstractMetricSpace<T> metricSpace;
    private final DistanceFunctionInterface<T> df;
    private final List<Object> pivots;
    private final List<Object> sampleObjectsAndQueries;
    private final PtolemyInequalityWithLimitedAnglesCoefsStoreInterface storage;
    private final TreeSet<Map.Entry<String, Float>> smallDistsOfSampleObjectsAndQueries;

    public LearningPtolemyInequalityWithLimitedAngles(AbstractMetricSpace<T> metricSpace, DistanceFunctionInterface<T> df, List<Object> pivots, List<Object> sampleObjectsAndQueries, int objectsCount, int queriesCount, TreeSet<Map.Entry<String, Float>> smallDistsOfSampleObjectsAndQueries, PtolemyInequalityWithLimitedAnglesCoefsStoreInterface storage, String datasetName) {
        this.metricSpace = metricSpace;
        this.df = df;
        this.pivots = pivots;
        this.sampleObjectsAndQueries = sampleObjectsAndQueries;
        this.storage = storage;
        this.smallDistsOfSampleObjectsAndQueries = smallDistsOfSampleObjectsAndQueries;
        this.resultName = storage.getResultDescription(datasetName, smallDistsOfSampleObjectsAndQueries.size(), objectsCount, queriesCount, pivots.size(), ALL_PIVOT_PAIRS);
    }

    public Map<Object, float[]> execute() {
        Map<Object, float[]> results = new HashMap<>();
        ExecutorService threadPool = vm.javatools.Tools.initExecutor(1);
        CountDownLatch latch = new CountDownLatch(pivots.size());
        try {
            Map<Object, Object> metricObjectsAsIdObjectMap = ToolsMetricDomain.getMetricObjectsAsIdObjectMap(metricSpace, sampleObjectsAndQueries, false);
            for (int p1 = 0; p1 < pivots.size(); p1++) {
                int finalP1 = p1;
                threadPool.execute(() -> {
                    Object[] fourObjects = new Object[4];
                    Object[] fourObjectsData = new Object[4];
                    fourObjects[0] = pivots.get(finalP1);
                    fourObjectsData[0] = metricSpace.getDataOfMetricObject(fourObjects[0]);

                    if (ALL_PIVOT_PAIRS) {
                        if (finalP1 != pivots.size() - 1) {
                            for (int p2 = finalP1 + 1; p2 < pivots.size(); p2++) {
                                fourObjects[1] = pivots.get(p2);
                                fourObjectsData[1] = metricSpace.getDataOfMetricObject(fourObjects[1]);
                                float[] extremes = learnForPivots(fourObjects, fourObjectsData, metricObjectsAsIdObjectMap);
                                synchronized (LearningPtolemyInequalityWithLimitedAngles.class) {
                                    String pivotPairsID = metricSpace.getIDOfMetricObject(fourObjects[0]) + "-" + metricSpace.getIDOfMetricObject(fourObjects[1]);
                                    results.put(pivotPairsID, extremes);
                                }
//                            LOG.log(Level.INFO, "Evaluated coefs for pivot pairs {0} with the starting pivot {6}. Results: {1}; {2}; {3}; {4}. Notice first two numbers multiplied by {5} for a sake of numerical precision.", new Object[]{pivotPairsID, extremes[0], extremes[1], extremes[2], extremes[3], CONSTANT_FOR_PRECISION, finalP1});
                            }
                        }
                    } else {
                        fourObjects[1] = pivots.get((finalP1 + 1) % pivots.size());
                        fourObjectsData[1] = metricSpace.getDataOfMetricObject(fourObjects[1]);
                        float[] extremes = learnForPivots(fourObjects, fourObjectsData, metricObjectsAsIdObjectMap);
                        String pivotPairsID = metricSpace.getIDOfMetricObject(fourObjects[0]) + "-" + metricSpace.getIDOfMetricObject(fourObjects[1]);
                        results.put(pivotPairsID, extremes);
//                        LOG.log(Level.INFO, "Evaluated coefs for pivot pairs {0} with the starting pivot {6}. Results: {1}; {2}; {3}; {4}. Notice first two numbers multiplied by {5} for a sake of numerical precision.", new Object[]{pivotPairsID, extremes[0], extremes[1], extremes[2], extremes[3], CONSTANT_FOR_PRECISION, finalP1});
                    }
                    latch.countDown();
                    LOG.log(Level.INFO, "Remains {0} primary pivots to check. Results size: {1}", new Object[]{latch.getCount(), results.size()});
                });
            }
            latch.await();
        } catch (InterruptedException ex) {
            Logger.getLogger(LearningPtolemyInequalityWithLimitedAngles.class.getName()).log(Level.SEVERE, null, ex);
        }
        threadPool.shutdown();
        storage.storeCoefficients(results, resultName);
        return results;
    }

    private float[] learnForPivots(Object[] fourObjects, Object[] fourObjectsData, Map metricObjectsAsIdObjectMap) {
        float[] extremes = new float[4]; // minSum, maxSum, minDiff, maxDiff
        extremes[0] = Float.MAX_VALUE;
        extremes[2] = Float.MAX_VALUE;
        for (Map.Entry<String, Float> smallDist : smallDistsOfSampleObjectsAndQueries) {
            String[] qoIDs = smallDist.getKey().split(";");
            fourObjects[2] = metricObjectsAsIdObjectMap.get(qoIDs[0]);
            fourObjects[3] = metricObjectsAsIdObjectMap.get(qoIDs[1]);
            fourObjectsData[2] = ((AbstractMap.SimpleEntry) fourObjects[2]).getValue();
            fourObjectsData[3] = ((AbstractMap.SimpleEntry) fourObjects[3]).getValue();
            float[] sixDists = ToolsMetricDomain.getPairwiseDistsOfFourObjects(df, true, fourObjectsData);
            if (sixDists == null || Tools.isZeroInArray(sixDists)) {
                continue;
            }
            float ac = Math.abs(sixDists[0] * sixDists[2]);
            float ef = Math.abs(sixDists[4] * sixDists[5]);
            float bd = Math.abs(sixDists[1] * sixDists[3]);
            float fractionSum = CONSTANT_FOR_PRECISION * ac / (bd + ef);
            float fractionDiff = ac / (ef - bd);
            if (fractionDiff < 1) {
                String s = "";
            }
            if (fractionSum > CONSTANT_FOR_PRECISION) {
                String s = "";
            }
            // minSum, maxSum, minDiff, maxDiff
            extremes[0] = Math.min(extremes[0], fractionSum);
            extremes[1] = Math.max(extremes[1], fractionSum);
            extremes[2] = Math.min(extremes[2], fractionDiff);
            extremes[3] = Math.max(extremes[3], fractionDiff);
        }
        return extremes;
    }

}
