package vm.search.algorithm.impl;

import java.util.AbstractMap;
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
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.ToolsMetricDomain;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.objTransforms.perform.PCAMetricObjectTransformer;
import vm.objTransforms.storeLearned.SVDStoreInterface;
import vm.search.algorithm.SearchingAlgorithm;
import vm.simRel.impl.SimRelEuclideanPCAImpl;

/**
 * Experiments with this class did not outperform original proposal in
 * SimRelSeqScanKNNCandSetThenFullDistEval
 *
 * @author Vlada
 */
@Deprecated
public class RefineCandidateSetWithPCASimRel extends SearchingAlgorithm<float[]> {

    private static final Logger LOG = Logger.getLogger(RefineCandidateSetWithPCASimRel.class.getName());

    private final TreeSet<AbstractMap.SimpleEntry<Object, float[]>> sortedPCAPreffixesForDataset;
    private final Map<Object, float[]> mapIDpca;
    private final PCAMetricObjectTransformer pcaTransformer;
    private final int pcaPreffixLength;
    private final DistanceFunctionInterface<float[]> fullDistanceFunction;
    private final SimRelEuclideanPCAImpl simRel;
    private final float tZero;

    private int objectCheckedFromSet;

    public RefineCandidateSetWithPCASimRel(AbstractMetricSpace<float[]> originalMetricSpace, AbstractMetricSpace<float[]> pcaMetricSpace, DistanceFunctionInterface<float[]> fullDistanceFunction, SimRelEuclideanPCAImpl simRel, SVDStoreInterface svdStorage, Iterator<Object> pcaDatasetIterator, int pcaPreffixLength, int pcaFullLength) {
        this.pcaPreffixLength = pcaPreffixLength;
        this.fullDistanceFunction = fullDistanceFunction;
        this.simRel = simRel;
        tZero = 0.5f * simRel.getTOmega(0);
        pcaTransformer = initPCA(originalMetricSpace, pcaMetricSpace, svdStorage, pcaFullLength, pcaPreffixLength);
        sortedPCAPreffixesForDataset = initSortedMapOfPCAPrefixes();
        mapIDpca = new HashMap<>();
        loadPCAPrefixesForSimRel(pcaMetricSpace, sortedPCAPreffixesForDataset, mapIDpca, pcaDatasetIterator);
    }

    private static PCAMetricObjectTransformer initPCA(AbstractMetricSpace<float[]> originalMetricSpace, AbstractMetricSpace<float[]> pcaMetricSpace, SVDStoreInterface svdStorage, int pcaFullLength, int pcaPreffixLength) {
        LOG.log(Level.INFO, "Start loading instance of the PCA with length {0}", pcaPreffixLength);
        float[][] vtMatrixFull = svdStorage.getVTMatrix();
        float[][] vtMatrix = Tools.shrinkMatrix(vtMatrixFull, pcaPreffixLength, vtMatrixFull[0].length);
        return new PCAMetricObjectTransformer(vtMatrix, svdStorage.getMeansOverColumns(), originalMetricSpace, pcaMetricSpace);
    }

    private TreeSet<AbstractMap.SimpleEntry<Object, float[]>> initSortedMapOfPCAPrefixes() {
        Tools.FloatVectorComparator floatVectorComparator = new Tools.FloatVectorComparator();
        Tools.MapByValueComparatorWithOwnValueComparator<float[]> comp = new Tools.MapByValueComparatorWithOwnValueComparator<>(floatVectorComparator);
        return new TreeSet<>(comp);
    }

    private void loadPCAPrefixesForSimRel(AbstractMetricSpace<float[]> pcaMetricSpace, TreeSet<AbstractMap.SimpleEntry<Object, float[]>> sortedPCAPreffixesForDataset, Map<Object, float[]> mapIDpca, Iterator<Object> pcaDatasetIterator) {
        LOG.log(Level.INFO, "Start loading vector prefixes for simRel");
        for (int i = 1; pcaDatasetIterator.hasNext(); i++) {
            Object dataObject = pcaDatasetIterator.next();
            Object id = pcaMetricSpace.getIDOfMetricObject(dataObject);
            float[] data = pcaMetricSpace.getDataOfMetricObject(dataObject);
            float[] preffixData = Tools.vectorPreffix(data, pcaPreffixLength);
            AbstractMap.SimpleEntry entry = new AbstractMap.SimpleEntry(id, preffixData);
            sortedPCAPreffixesForDataset.add(entry);
            mapIDpca.put(id, preffixData);
            if (i % 50000 == 0) {
                LOG.log(Level.INFO, "Loaded {0} prefixes", i);
            }
        }
        LOG.log(Level.INFO, "Loaded {0} prefixes", mapIDpca.size());
    }

    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<float[]> fullObjectsMetricSpace, Object queryObject, int k, Map<Object, Object> candidatesToCheck, Object... additionalParams) {
        simRel.resetEarlyStopsOnCoordsCounts();
        long time = -System.currentTimeMillis();
        Object qID = fullObjectsMetricSpace.getIDOfMetricObject(queryObject);
        boolean involveObjWithUnknownRelation = true;
        int kPCA = 3 * k;
        if (additionalParams.length > 0 && additionalParams[0] instanceof Integer) {
            kPCA = (int) additionalParams[0];
            if (additionalParams.length > 1 && additionalParams[1] instanceof Boolean) {
                involveObjWithUnknownRelation = (boolean) additionalParams[1];
            }
        }

        AbstractMap.SimpleEntry<Object, float[]> qPCA = (AbstractMap.SimpleEntry<Object, float[]>) pcaTransformer.transformMetricObject(queryObject, pcaPreffixLength);
        float[] qPCAData = qPCA.getValue();
        float[] qData = fullObjectsMetricSpace.getDataOfMetricObject(queryObject);

        AbstractMap.SimpleEntry lowerEntry = sortedPCAPreffixesForDataset.floor(qPCA);
        Iterator<AbstractMap.SimpleEntry<Object, float[]>> lowerQSubset = sortedPCAPreffixesForDataset.subSet(sortedPCAPreffixesForDataset.first(), true, lowerEntry, true).descendingIterator();
        AbstractMap.SimpleEntry upperEntry = sortedPCAPreffixesForDataset.ceiling(qPCA);
        Iterator<AbstractMap.SimpleEntry<Object, float[]>> upperQSubset = sortedPCAPreffixesForDataset.subSet(upperEntry, true, sortedPCAPreffixesForDataset.last(), true).iterator();

        AbstractMap.SimpleEntry<Object, float[]> lCand = lowerQSubset.next();
        AbstractMap.SimpleEntry<Object, float[]> uCand = upperQSubset.next();

        Set<Object> checkedIDs = new HashSet<>();
        TreeSet<Map.Entry<Object, Float>> ret = new TreeSet<>(new Tools.MapByValueComparator());
        List<Object> ansOfSimRel = new ArrayList<>();
        float deltaZeroQLast = Float.MAX_VALUE;
        boolean addedFromAnsSimRel = false;
        for (objectCheckedFromSet = 0; true; objectCheckedFromSet++) {
            Object[] candAndZeroDist = getBetterCandidate(qPCAData, lCand, uCand);
            AbstractMap.SimpleEntry<Object, float[]> cand = (AbstractMap.SimpleEntry<Object, float[]>) candAndZeroDist[0];
            if (cand == null) {
                break;
            }
            float deltaZeroQCand = (float) candAndZeroDist[1];
            deltaZeroQCand *= deltaZeroQCand;
            if (deltaZeroQCand > tZero && !addedFromAnsSimRel) {
                addedFromAnsSimRel = true;
//                for (int x = 0; x < Math.min(5, ansOfSimRel.size()); x++) {
//                    Object firstID = ansOfSimRel.get(x);
//                    float[] firstPCA = mapIDpca.get(firstID);
//                    addOToSimRelAnswer(kPCA, qPCAData, firstPCA, firstID, ansOfSimRel);
//                }
                for (Object oID : ansOfSimRel) {
                    if (!checkedIDs.contains(oID)) {
                        refineAndAddObjToAnswer(qID, qData, oID, candidatesToCheck, ret);
                        checkedIDs.add(oID);
                    }
                }
                if (ret.size() >= k) {
                    Object lastKey = adjustAndReturnLastEntry(ret, k).getKey();
                    deltaZeroQLast = Math.abs(mapIDpca.get(lastKey)[0] - qPCAData[0]);
                    deltaZeroQLast *= deltaZeroQLast;
                }
            }
            if (deltaZeroQCand - deltaZeroQLast > tZero) {
                break;
            }
            if (lCand != null && lCand.equals(cand)) {
                lCand = lowerQSubset.hasNext() ? lowerQSubset.next() : null;
            } else {
                uCand = upperQSubset.hasNext() ? upperQSubset.next() : null;
            }
            Object candID = cand.getKey();
            if (candidatesToCheck != null && !candidatesToCheck.containsKey(candID)) {
                continue;
            }
            if (ansOfSimRel.size() < kPCA) {
                ansOfSimRel.add(candID);
                continue;
            }
            short simRelAdded = addOToSimRelAnswer(kPCA, qPCAData, cand.getValue(), candID, ansOfSimRel);
            if (simRelAdded == 0 && involveObjWithUnknownRelation) {
                refineAndAddObjToAnswer(qID, qData, candID, candidatesToCheck, ret);
                if (ret.size() >= k) {
                    Object lastKey = adjustAndReturnLastEntry(ret, k).getKey();
                    deltaZeroQLast = Math.abs(mapIDpca.get(lastKey)[0] - qPCAData[0]);
                    deltaZeroQLast *= deltaZeroQLast;
                }
            }
        }
        for (Object oID : ansOfSimRel) {
            if (!checkedIDs.contains(oID)) {
                refineAndAddObjToAnswer(qID, qData, oID, candidatesToCheck, ret);
                checkedIDs.add(oID);
            }
        }
        adjustAndReturnLastEntry(ret, k);
        time += System.currentTimeMillis();
        incTime(qID, time);
        LOG.log(Level.INFO, "Finished evaluation of query obj {0}", qID);
        return ret;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<float[]> metricSpace, Object queryObject, int k, Iterator<Object> candidatesToCheck, Object... additionalParams) {
        Map<Object, Object> fullObjectsWithIDsToCheck = null;
        if (candidatesToCheck != null) {
            fullObjectsWithIDsToCheck = ToolsMetricDomain.getMetricObjectsAsIdObjectMap(metricSpace, candidatesToCheck, true);
        }
        return completeKnnSearch(metricSpace, queryObject, 0, fullObjectsWithIDsToCheck, additionalParams);
    }

    @Override
    public List<Object> candSetKnnSearch(AbstractMetricSpace<float[]> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private Object[] getBetterCandidate(float[] qPCAData, AbstractMap.SimpleEntry<Object, float[]> o1, AbstractMap.SimpleEntry<Object, float[]> o2) {
        float diff1 = o1 == null ? Float.MAX_VALUE : Math.abs(qPCAData[0] - o1.getValue()[0]);
        float diff2 = o2 == null ? Float.MAX_VALUE : Math.abs(qPCAData[0] - o2.getValue()[0]);
        if (diff1 <= diff2) {
            return new Object[]{o1, diff1};
        }
        return new Object[]{o2, diff2};
    }

    private void refineAndAddObjToAnswer(Object qID, float[] qData, Object candID, Map<Object, Object> objectsToCheck, TreeSet<Map.Entry<Object, Float>> queryAnswer) {
        incDistsComps(qID);
        float[] candFullData = (float[]) objectsToCheck.get(candID);
        float dist = fullDistanceFunction.getDistance(qData, candFullData);
        AbstractMap.SimpleEntry<Object, Float> entry = new AbstractMap.SimpleEntry(candID, dist);
        queryAnswer.add(entry);
    }

    /**
     * @param kPCA
     * @param qPCA
     * @param candData
     * @param oID
     * @param ansOfSimRel
     * @return 2 is object was added to the answer, 1 if it is less similar, and
     * 0 if the relation is unknown
     */
    private short addOToSimRelAnswer(int kPCA, float[] qPCA, float[] candData, Object oID, List<Object> ansOfSimRel) {
        if (ansOfSimRel.isEmpty()) {
            ansOfSimRel.add(oID);
            return 2;
        }
        int idxWhereAdd = Integer.MAX_VALUE;
        List<Integer> indexesToRemove = new ArrayList<>();
        for (int i = ansOfSimRel.size() - 1; i >= 0; i--) {
            float[] oLastData = mapIDpca.get(ansOfSimRel.get(i));
            short sim = simRel.getMoreSimilar(qPCA, oLastData, candData);
            if (sim == 1) {
                if (i < kPCA - 1) {
                    deleteIndexes(ansOfSimRel, kPCA, indexesToRemove);
                    ansOfSimRel.add(i + 1, oID);
                    return 2;
                }
                return 1;
            }
            if (sim == 2) {
                idxWhereAdd = i;
                indexesToRemove.add(i);
            }
        }
        if (idxWhereAdd != Integer.MAX_VALUE) {
            deleteIndexes(ansOfSimRel, kPCA, indexesToRemove);
            ansOfSimRel.add(idxWhereAdd, oID);
            return 2;
        }
        return 0;
    }

    private void deleteIndexes(List<Object> ret, int k, List<Integer> indexesToRemove) {
        while (ret.size() >= k && !indexesToRemove.isEmpty()) {
            Integer idx = indexesToRemove.get(0);
            Object id = ret.get(idx);
            ret.remove(id);
            indexesToRemove.remove(0);
        }
    }

    public int getAndResetObjCheckedCount() {
        int ret = objectCheckedFromSet;
        objectCheckedFromSet = 0;
        return ret;
    }

    @Override
    public String getResultName() {
        return "RefineCandidateSetWithPCASimRel";
    }

}
