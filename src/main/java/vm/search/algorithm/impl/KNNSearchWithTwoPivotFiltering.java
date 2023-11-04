package vm.search.algorithm.impl;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.DataTypeConvertor;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.search.algorithm.SearchingAlgorithm;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.bounding.twopivots.TwoPivotsFilter;

/**
 * takes pivot pairs in a linear way, i.e., [0], [1], then [2], [3], etc.
 *
 * @author Vlada
 * @param <T>
 */
public class KNNSearchWithTwoPivotFiltering<T> extends SearchingAlgorithm<T> {

    private static final Logger LOG = Logger.getLogger(KNNSearchWithTwoPivotFiltering.class.getName());

    private final TwoPivotsFilter filter;
    private final String[] pivotIDs;
    private final List<T> pivotsData;
    private final float[][] poDists;
    private final Map<String, Integer> rowHeaders;
    private final Map<String, Integer> columnHeaders;
    private final float[][] pivotPivotDists;
    private final DistanceFunctionInterface<T> df;
    private final boolean pivotPairsFromFilter;

    public KNNSearchWithTwoPivotFiltering(AbstractMetricSpace<T> metricSpace, TwoPivotsFilter filter, List<Object> pivots, float[][] poDists, Map<String, Integer> rowHeaders, Map<String, Integer> columnHeaders, float[][] pivotPivotDists, DistanceFunctionInterface<T> df) {
        this(metricSpace, filter, pivots, poDists, rowHeaders, columnHeaders, pivotPivotDists, df, false);
    }

    public KNNSearchWithTwoPivotFiltering(AbstractMetricSpace<T> metricSpace, TwoPivotsFilter filter, List<Object> pivots, float[][] poDists, Map<String, Integer> rowHeaders, Map<String, Integer> columnHeaders, float[][] pivotPivotDists, DistanceFunctionInterface<T> df, boolean createAllPivotPairs) {
        this.filter = filter;
        this.pivotsData = metricSpace.getDataOfMetricObjects(pivots);// correct!
        this.pivotPairsFromFilter = createAllPivotPairs;
        if (createAllPivotPairs) {
            pivots = Tools.createAllPairs(pivots);
        }
        List<Object> pivotIDsList = metricSpace.getIDsOfMetricObjects(pivots);
        this.pivotIDs = DataTypeConvertor.objectsToStrings(pivotIDsList);
        this.poDists = poDists;
        this.pivotPivotDists = pivotPivotDists;
        this.df = df;
        this.rowHeaders = rowHeaders;
        this.columnHeaders = columnHeaders;
    }

    public static boolean PRINT_DETAILS = false;

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> metricSpace, Object q, int k, Iterator<Object> objects, Object... params) {
        long t = -System.currentTimeMillis();
        TreeSet<Map.Entry<Object, Float>> currAnswer = null;
        if (params.length > 0) {
            currAnswer = (TreeSet<Map.Entry<Object, Float>>) params[0];
        }
        T qData = metricSpace.getDataOfMetricObject(q);
        Object qId = metricSpace.getIDOfMetricObject(q);
        float[] qpDists = new float[pivotsData.size()];
        for (int i = 0; i < qpDists.length; i++) {
            T pData = pivotsData.get(i);
            qpDists[i] = df.getDistance(qData, pData);
        }
        TreeSet<Map.Entry<Object, Float>> ret = currAnswer == null ? new TreeSet<>(new Tools.MapByValueComparator()) : currAnswer;
        int step = pivotPairsFromFilter ? 2 : 1;
        while (objects.hasNext()) {
            boolean skip = false;
            Object o = objects.next();
            Object oId = metricSpace.getIDOfMetricObject(o);
            if (!rowHeaders.containsKey(oId.toString())) {
                throw new RuntimeException("Precomputed distances dost not contain object " + oId.toString());
            }
            int oIdx = rowHeaders.get(oId.toString());
            T oData = metricSpace.getDataOfMetricObject(o);
            float range = adjustAndReturnSearchRadius(ret, k);
//            float distanceCheck = df.getDistance(qData, oData);;
            if (range < Float.MAX_VALUE) {
                for (int p = 0; p < pivotIDs.length; p += step) {
                    PRINT_DETAILS = false;
                    String p1ID = pivotIDs[p];
                    String p2ID = pivotIDs[(p + 1) % pivotIDs.length];
                    if (!columnHeaders.containsKey(p1ID)) {
                        throw new RuntimeException("Precomputed distances dost not contain pivot " + p1ID);
                    }
                    if (!columnHeaders.containsKey(p2ID)) {
                        throw new RuntimeException("Precomputed distances dost not contain pivot " + p2ID);
                    }
                    int p1 = columnHeaders.get(p1ID);
                    int p2 = columnHeaders.get(p2ID);
                    float distP1P2 = pivotPivotDists[p1][p2];
                    float distP2O = poDists[oIdx][p2];
                    float distQP1 = qpDists[p1];
                    float distP1O = poDists[oIdx][p1];
                    float distP2Q = qpDists[p2];
                    float lowerBound = filter.lowerBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID);
//                    if (distanceCheck < lowerBound) {
//                        PRINT_DETAILS = false;
////                        System.out.print("XXX range;" + range + ";realDist;" + distanceCheck + ";");
//                        lowerBound = filter.lowerBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID);
//                    }
                    if (lowerBound > range) {
//                        PRINT_DETAILS = true;
//                        if (PRINT_DETAILS) {
//                            System.out.println("Skipped. Radius: " + range + ", lb: " + lowerBound);
//                            filter.lowerBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID);
//                        }
//                        PRINT_DETAILS = false;
                        skip = true;
                        break;
                    }
                    float upperBound = filter.upperBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID);
//                    if (distanceCheck > upperBound) {
//                        PRINT_DETAILS = false;
////                        System.out.print("XXX range;" + range + ";realDist;" + distanceCheck + ";");
//                        upperBound = filter.upperBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID);
//                    }
                    if (upperBound < range) {
                        skip = true;
                        float distance = df.getDistance(qData, oData);
                        ret.add(new AbstractMap.SimpleEntry<>(oId, distance));
                        break;
                    }
                }
            }
            if (skip) {
                continue;
            }
            incDistsComps(qId);
            float distance = df.getDistance(qData, oData);
            ret.add(new AbstractMap.SimpleEntry<>(oId, distance));
        }
        adjustAndReturnSearchRadius(ret, k);
        t += System.currentTimeMillis();
        incTime(qId, t);
        LOG.log(Level.INFO, "Evaluated query {2} using {0} dist comps. Time: {1}", new Object[]{getDistCompsForQuery(qId), getTimeOfQuery(qId), qId.toString()});
        return ret;
    }

    @Override
    public List<Object> candSetKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getResultName() {
        return filter.getTechFullName();
    }

}
