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
import vm.metricSpace.distance.bounding.onepivot.OnePivotFilter;

/**
 * takes pivot pairs in a linear way, i.e., [0], [1], then [2], [3], etc.
 *
 * @author Vlada
 * @param <T>
 */
public class KNNSearchWithOnePivotFiltering<T> extends SearchingAlgorithm<T> {

    private static final Logger LOG = Logger.getLogger(KNNSearchWithTwoPivotFiltering.class.getName());

    private final OnePivotFilter filter;
    private final String[] pivotIDs;
    private final List<T> pivotsData;
    private final float[][] poDists;
    private final Map<String, Integer> rowHeaders;
    private final Map<String, Integer> columnHeaders;
    private final DistanceFunctionInterface<T> df;

    public KNNSearchWithOnePivotFiltering(AbstractMetricSpace<T> metricSpace, OnePivotFilter filter, List<Object> pivots, float[][] poDists, Map<String, Integer> rowHeaders, Map<String, Integer> columnHeaders, DistanceFunctionInterface<T> df) {
        this.filter = filter;
        List<Object> pivotIDsList = metricSpace.getIDsOfMetricObjects(pivots);
        this.pivotIDs = DataTypeConvertor.objectsToStrings(pivotIDsList);
        this.pivotsData = metricSpace.getDataOfMetricObjects(pivots);
        this.poDists = poDists;
        this.df = df;
        this.rowHeaders = rowHeaders;
        this.columnHeaders = columnHeaders;
    }

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
//            float maxLB = 0;
//            float minUB = Float.MAX_VALUE;
            if (range < Float.MAX_VALUE && range > 0) {
                for (int p = 0; p < pivotIDs.length; p++) {
                    String pId = pivotIDs[p];
                    if (!columnHeaders.containsKey(pId)) {
                        throw new RuntimeException("Precomputed distances dost not contain pivot " + pId);
                    }
                    int pIdx = columnHeaders.get(pId);
                    float distQP = qpDists[pIdx];
                    float distPO = poDists[oIdx][pIdx];
                    float lowerBound = filter.lowerBound(distQP, distPO, pId);
//                    System.out.print("XXX range;" + range + ";realDist;" + df.getDistance(qData, oData) + ";lower bound;" + lowerBound);
//                    maxLB = Math.max(maxLB, lowerBound);
                    if (lowerBound > range) {
                        skip = true;
//                        System.out.println();
                        break;
                    }
                    float upperBound = filter.upperBound(distQP, distPO, pId);
//                    minUB = Math.min(minUB, upperBound);
//                    System.out.println(";upper bound;" + upperBound + "   extremes:;" + maxLB + ";" + minUB);
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
