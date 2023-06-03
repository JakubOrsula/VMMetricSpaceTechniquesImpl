/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.impl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.search.SearchingAlgorithm;
import vm.simRel.SimRelInterface;
import vm.simRel.impl.SimRelEuclideanPCAImplForTesting;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class SimRelInstantRefinement<T> extends SearchingAlgorithm<T> {

    private static final Logger LOG = Logger.getLogger(SimRelInstantRefinement.class.getName());
    private final SimRelInterface<float[]> simRelFunc;

    private int distCompsOfLastExecutedQuery;
    private long simRelEvalCounter;
    private int kPCA;
    private DistanceFunctionInterface<T> fullDF;

    public SimRelInstantRefinement(DistanceFunctionInterface<T> fullDF, SimRelInterface simRelFunc, int kPCA) {
        this.fullDF = fullDF;
        this.simRelFunc = simRelFunc;
        this.kPCA = kPCA;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> fullMetricSpace, Object fullQueryObject, int k, Iterator<Object> pcaObjects, Object... additionalParams) {
        long t = -System.currentTimeMillis();
        TreeSet<Map.Entry<Object, Float>> currAnswer = null;
        if (additionalParams.length > 0 && additionalParams[0] instanceof TreeSet) {
            currAnswer = (TreeSet<Map.Entry<Object, Float>>) additionalParams[0];
        }
        T qData = fullMetricSpace.getDataOfMetricObject(fullQueryObject);
        Object qId = fullMetricSpace.getIDOfMetricObject(fullQueryObject);
        TreeSet<Map.Entry<Object, Float>> ret = currAnswer == null ? new TreeSet<>(new Tools.MapByValueComparator()) : currAnswer;

        if (simRelFunc instanceof SimRelEuclideanPCAImplForTesting) {
            SimRelEuclideanPCAImplForTesting euclid = (SimRelEuclideanPCAImplForTesting) simRelFunc;
            euclid.resetEarlyStopsOnCoordsCounts();
        }

        AbstractMetricSpace<float[]> pcaMetricSpace = (AbstractMetricSpace<float[]>) additionalParams[0];
        Object pcaQueryObject = additionalParams[1];

        Map<Object, Object> fullObjectsMap = (Map<Object, Object>) additionalParams[2];

        float[] pcaQueryObjData = pcaMetricSpace.getDataOfMetricObject(pcaQueryObject);

        List<Object> ansOfSimRel = new ArrayList<>();
        Map<Object, float[]> candSetData = new HashMap<>();
        simRelEvalCounter = 0;

        for (int i = 1; pcaObjects.hasNext(); i++) {
            Object oPCA = pcaObjects.next();
            Object oPCAID = pcaMetricSpace.getIDOfMetricObject(oPCA);
            float[] oPCAData = pcaMetricSpace.getDataOfMetricObject(oPCA);
            boolean toBeAdded = addOToAnswer(k, pcaQueryObjData, oPCAData, oPCAID, ansOfSimRel, candSetData);
            if (toBeAdded) {
                T fullOData = (T) fullObjectsMap.get(oPCAID);
                float distance = fullDF.getDistance(qData, fullOData);
                distCompsOfLastExecutedQuery++;
                ret.add(new AbstractMap.SimpleEntry<>(oPCAID, distance));
            }
            adjustAndReturnSearchRadius(ret, k);
            t += System.currentTimeMillis();
            incTime(qId, t);
            LOG.log(Level.INFO, "Evaluated query {2} using {0} dist comps. Time: {1}", new Object[]{getDistCompsForQuery(qId), getTimeOfQuery(qId), qId.toString()});
            return ret;
        }

        Object qID = pcaMetricSpace.getIDOfMetricObject(pcaQueryObject);
        incDistsComps(qID, ansOfSimRel.size());
        LOG.log(Level.INFO, "distancesCounter;{0}; simRelCounter;{1}", new Object[]{distCompsOfLastExecutedQuery, simRelEvalCounter});
        return ret;
    }

    // differs from the method in SimRelSeqScanKNNCandSet!!!
    private boolean addOToAnswer(int k, float[] queryObjectData, float[] oData, Object idOfO, List<Object> ansOfSimRel, Map<Object, float[]> mapOfData) {
        if (ansOfSimRel.isEmpty()) {
            ansOfSimRel.add(idOfO);
            mapOfData.put(idOfO, oData);
            return true;
        }
        int idxWhereAdd = Integer.MAX_VALUE;
        List<Integer> indexesToRemove = new ArrayList<>();
        for (int i = ansOfSimRel.size() - 1; i >= 0; i--) {
            float[] oLastData = mapOfData.get(ansOfSimRel.get(i));
            simRelEvalCounter++;
            short sim = simRelFunc.getMoreSimilar(queryObjectData, oLastData, oData);
            if (sim == 1) {
                if (i < k - 1) {
                    deleteIndexes(ansOfSimRel, k, indexesToRemove, mapOfData);
                    ansOfSimRel.add(i + 1, idOfO);
                    mapOfData.put(idOfO, oData);
                    return true;
                }
                return false;
            }
            if (sim == 2) {
                idxWhereAdd = i;
                indexesToRemove.add(i);
            }
        }
        if (idxWhereAdd != Integer.MAX_VALUE) {
            deleteIndexes(ansOfSimRel, k, indexesToRemove, mapOfData);
            ansOfSimRel.add(idxWhereAdd, idOfO);
            mapOfData.put(idOfO, oData);
        }
        mapOfData.put(idOfO, oData);
        return true;
    }

    private void deleteIndexes(List<Object> ret, int k, List<Integer> indexesToRemove, Map<Object, float[]> retData) {
        while (ret.size() >= k && !indexesToRemove.isEmpty()) {
            Integer idx = indexesToRemove.get(0);
            Object id = ret.get(idx);
            retData.remove(id);
            ret.remove(id);
            indexesToRemove.remove(0);
        }
    }

    @Override
    public List<Object> candSetKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public Object getSimRelStatsOfLastExecutedQuery() {
        if (simRelFunc instanceof SimRelEuclideanPCAImplForTesting) {
            SimRelEuclideanPCAImplForTesting euclid = (SimRelEuclideanPCAImplForTesting) simRelFunc;
            return euclid.getEarlyStopsOnCoordsCounts();
        }
        throw new RuntimeException("No simRel stats for the last query");
    }

}
