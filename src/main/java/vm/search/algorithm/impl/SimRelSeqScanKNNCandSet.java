package vm.search.algorithm.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.metricSpace.AbstractMetricSpace;
import vm.search.algorithm.SearchingAlgorithm;
import vm.simRel.SimRelInterface;
import vm.simRel.impl.SimRelEuclideanPCAImplForTesting;

/**
 *
 * @author Vlada
 */
public class SimRelSeqScanKNNCandSet extends SearchingAlgorithm<float[]> {

    private static final Logger LOG = Logger.getLogger(SimRelSeqScanKNNCandSet.class.getName());
    private final SimRelInterface<float[]> simRelFunc;

    private int distCompsOfLastExecutedQuery;
    private long simRelEvalCounter;
    private int kPCA;
    private boolean involveObjWithUnknownRelation;

    public SimRelSeqScanKNNCandSet(SimRelInterface simRelFunc, int kPCA) {
        this(simRelFunc, kPCA, true);
    }

    public SimRelSeqScanKNNCandSet(SimRelInterface simRelFunc, int kPCA, boolean involveObjWithUnknownRelation) {
        this.simRelFunc = simRelFunc;
        this.kPCA = kPCA;
        this.involveObjWithUnknownRelation = involveObjWithUnknownRelation;
    }

    @Override
    public List<Object> candSetKnnSearch(AbstractMetricSpace<float[]> pcaMetricSpace, Object pcaQueryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        if (simRelFunc instanceof SimRelEuclideanPCAImplForTesting) {
            SimRelEuclideanPCAImplForTesting euclid = (SimRelEuclideanPCAImplForTesting) simRelFunc;
            euclid.resetEarlyStopsOnCoordsCounts();
        }
        float[] pcaQueryObjData = pcaMetricSpace.getDataOfMetricObject(pcaQueryObject);
        List<Object> ansOfSimRel = new ArrayList<>();
        Set<Object> objIdUnknownRelation = new HashSet<>();
        Map<Object, float[]> candSet = new HashMap<>();
        distCompsOfLastExecutedQuery = 0;
        simRelEvalCounter = 0;
        sequentilScanWithSimRel(pcaMetricSpace, objects, k, pcaQueryObjData, ansOfSimRel, candSet, objIdUnknownRelation);
        if (involveObjWithUnknownRelation) {
            ansOfSimRel.addAll(objIdUnknownRelation);
        }
        distCompsOfLastExecutedQuery = ansOfSimRel.size();
        Object qID = pcaMetricSpace.getIDOfMetricObject(pcaQueryObject);
        incDistsComps(qID, ansOfSimRel.size());
        LOG.log(Level.INFO, "distancesCounter;{0}; simRelCounter;{1}", new Object[]{distCompsOfLastExecutedQuery, simRelEvalCounter});
        return ansOfSimRel;
    }

    private void sequentilScanWithSimRel(AbstractMetricSpace<float[]> metricSpace, Iterator<Object> objects, int k, float[] queryObjectData, List<Object> ansOfSimRel, Map<Object, float[]> mapOfData, Set<Object> objIdUnknownRelation, Object... paramsToExtractDataFromMetricObject) {
        for (int i = 1; objects.hasNext(); i++) {
            Object metricObject = objects.next();
            Object oID = metricSpace.getIDOfMetricObject(metricObject);
            float[] oData = metricSpace.getDataOfMetricObject(metricObject);
            boolean knownRelation = addOToAnswer(k, queryObjectData, oData, oID, ansOfSimRel, mapOfData);
            if (!knownRelation) {
                objIdUnknownRelation.add(oID);
            }
        }
    }

    public Long getSimRelEvalCounter() {
        return simRelEvalCounter;
    }

    public Object getSimRelStatsOfLastExecutedQuery() {
        if (simRelFunc instanceof SimRelEuclideanPCAImplForTesting) {
            SimRelEuclideanPCAImplForTesting euclid = (SimRelEuclideanPCAImplForTesting) simRelFunc;
            return euclid.getEarlyStopsOnCoordsCounts();
        }
        throw new RuntimeException("No simRel stats for the last query");
    }

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
                }
                return true;
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
            return true;
        }
        mapOfData.put(idOfO, oData);
        return false;
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

    public int getkPCA() {
        return kPCA;
    }

    public void setkPCA(int kPCA) {
        this.kPCA = kPCA;
    }

    public boolean isInvolveObjWithUnknownRelation() {
        return involveObjWithUnknownRelation;
    }

    public void setInvolveObjWithUnknownRelation(boolean involveObjWithUnknownRelation) {
        this.involveObjWithUnknownRelation = involveObjWithUnknownRelation;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<float[]> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... paramsToExtractDataFromMetricObject) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getResultName() {
        return "SimRelSeqScanKNNCandSet";
    }

}
