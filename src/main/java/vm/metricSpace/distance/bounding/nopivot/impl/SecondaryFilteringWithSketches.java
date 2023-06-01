package vm.metricSpace.distance.bounding.nopivot.impl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import vm.metricSpace.Dataset;
import vm.metricSpace.ToolsMetricDomain;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.impl.HammingDistanceLongs;
import vm.metricSpace.distance.bounding.nopivot.NoPivotFilter;
import vm.metricSpace.distance.bounding.nopivot.storeLearned.SecondaryFilteringWithSketchesStoreInterface;

/**
 *
 * @author Vlada
 */
public class SecondaryFilteringWithSketches extends NoPivotFilter {

    private final Map<Object, Object> sketches;
    private final DistanceFunctionInterface hamDistFunc;
    private final double[] primDistsThreshold;
    private final int[] hamDistsThresholds;

    public SecondaryFilteringWithSketches(String namePrefix, String fullDatasetName, Dataset<long[]> sketchingDataset, SecondaryFilteringWithSketchesStoreInterface storage, float thresholdPcum, int iDimSketchesSampleCount, int iDimDistComps, float distIntervalForPX) {
        super(namePrefix);
        this.hamDistFunc = new HammingDistanceLongs();
        SortedMap<Double, Integer> mapping = storage.loadMapping(thresholdPcum, fullDatasetName, sketchingDataset.getDatasetName(), iDimSketchesSampleCount, iDimDistComps, distIntervalForPX);
        primDistsThreshold = new double[mapping.size()];
        hamDistsThresholds = new int[mapping.size()];
        Iterator<Map.Entry<Double, Integer>> it = mapping.entrySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            Map.Entry<Double, Integer> entry = it.next();
            primDistsThreshold[i] = entry.getKey();
            hamDistsThresholds[i] = entry.getValue();
        }
        Iterator sketchesIt = sketchingDataset.getMetricObjectsFromDataset();
        sketches = ToolsMetricDomain.getMetricObjectsAsIdObjectMap(sketchingDataset.getMetricSpace(), sketchesIt, true);
    }

    public SecondaryFilteringWithSketches(String namePrefix, Dataset<long[]> sketchingDataset, double[] primDistsThreshold, int[] hamDistsThresholds) {
        super(namePrefix);
        this.hamDistFunc = new HammingDistanceLongs();
        this.primDistsThreshold = primDistsThreshold;
        this.hamDistsThresholds = hamDistsThresholds;
        Iterator sketchesIt = sketchingDataset.getMetricObjectsFromDataset();
        sketches = ToolsMetricDomain.getMetricObjectsAsIdObjectMap(sketchingDataset.getMetricSpace(), sketchesIt, true);
    }

    public SecondaryFilteringWithSketches(String namePrefix, Map<Object, Object> sketches, double[] primDistsThreshold, int[] hamDistsThresholds) {
        super(namePrefix);
        this.sketches = sketches;
        this.hamDistFunc = new HammingDistanceLongs();
        this.primDistsThreshold = primDistsThreshold;
        this.hamDistsThresholds = hamDistsThresholds;
    }

    private float lastSearchRadius = -1;
    private int lastPos = -1;

    public float lowerBound(long[] querySketch, Object oID, float searchRadius) {
        int pos;
        if (lastSearchRadius != searchRadius) {
            lastSearchRadius = searchRadius;
            lastPos = Arrays.binarySearch(primDistsThreshold, searchRadius);
        }
        pos = lastPos;
        if (pos < 0) {
            pos = -pos - 1;
        }
        if (pos < hamDistsThresholds.length) {
            long[] skData = (long[]) sketches.get(oID);
            if (skData != null) {
                float hamDist = hamDistFunc.getDistance(querySketch, skData);
                if (hamDist >= hamDistsThresholds[pos]) {
                    return Float.MAX_VALUE;
                }
            }
        }
        return 0;
    }

    @Override
    public float lowerBound(Object... args) {
        long[] querySketch = (long[]) args[0];
        Object oID = args[1];
        Float rad = (Float) args[2];
        return lowerBound(querySketch, oID, rad);
    }

    @Override
    public float upperBound(Object... args) {
        return Float.MAX_VALUE;
    }

    @Override
    protected String getTechName() {
        return "Secondary_filtering_with_sketches";
    }

}
