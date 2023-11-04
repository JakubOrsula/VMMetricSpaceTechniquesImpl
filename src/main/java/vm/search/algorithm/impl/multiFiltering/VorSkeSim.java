/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.algorithm.impl.multiFiltering;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.bounding.nopivot.impl.SecondaryFilteringWithSketches;
import vm.objTransforms.objectToSketchTransformators.AbstractObjectToSketchTransformator;
import vm.search.algorithm.SearchingAlgorithm;
import vm.search.algorithm.impl.VoronoiPartitionsCandSetIdentifier;
import vm.simRel.SimRelInterface;
import vm.simRel.impl.SimRelEuclideanPCAImplForTesting;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class VorSkeSim<T> extends SearchingAlgorithm<T> {

    public static final Boolean STORE_RESULTS = true;

    private static final Logger LOG = Logger.getLogger(VorSkeSim.class.getName());

    private final VoronoiPartitionsCandSetIdentifier voronoiFilter;
    private final int voronoiK;
    private final SecondaryFilteringWithSketches sketchSecondaryFilter;
    private final AbstractObjectToSketchTransformator sketchingTechnique;
    private final AbstractMetricSpace<long[]> hammingSpaceForSketches;

    private final SimRelInterface<float[]> simRelFunc;
    private final int simRelMinK;
    private final Map<Object, float[]> pcaPrefixesMap;

    private final Map<Object, T> fullObjectsStorage;

    private final DistanceFunctionInterface<T> fullDF;

    private long simRelEvalCounter;

    public VorSkeSim(VoronoiPartitionsCandSetIdentifier voronoiFilter, int voronoiK, SecondaryFilteringWithSketches sketchSecondaryFilter, AbstractObjectToSketchTransformator sketchingTechnique, AbstractMetricSpace<long[]> hammingSpaceForSketches, SimRelInterface<float[]> simRelFunc, int simRelMinK, Map<Object, float[]> pcaPrefixesMap, Map<Object, T> fullObjectsStorage, DistanceFunctionInterface<T> fullDF) {
        this.voronoiFilter = voronoiFilter;
        this.voronoiK = voronoiK;
        this.sketchSecondaryFilter = sketchSecondaryFilter;
        this.sketchingTechnique = sketchingTechnique;
        this.hammingSpaceForSketches = hammingSpaceForSketches;
        this.simRelFunc = simRelFunc;
        this.simRelMinK = simRelMinK;
        this.pcaPrefixesMap = pcaPrefixesMap;
        this.fullObjectsStorage = fullObjectsStorage;
        this.fullDF = fullDF;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> fullMetricSpace, Object fullQ, int k, Iterator<Object> ignored, Object... additionalParams) {
        long t = -System.currentTimeMillis();
        int distComps = 0;
        simRelEvalCounter = 0;

        TreeSet<Map.Entry<Object, Float>> currAnswer = null;
        int paramIDX = 0;
        if (additionalParams.length > 0 && additionalParams[0] instanceof TreeSet) {
            currAnswer = (TreeSet<Map.Entry<Object, Float>>) additionalParams[0];
            paramIDX++;
        }
        Object qId = fullMetricSpace.getIDOfMetricObject(fullQ);
        T fullQData = fullMetricSpace.getDataOfMetricObject(fullQ);
        TreeSet<Map.Entry<Object, Float>> ret = currAnswer == null ? new TreeSet<>(new Tools.MapByValueComparator()) : currAnswer;

        if (simRelFunc instanceof SimRelEuclideanPCAImplForTesting) {
            SimRelEuclideanPCAImplForTesting euclid = (SimRelEuclideanPCAImplForTesting) simRelFunc;
            euclid.resetEarlyStopsOnCoordsCounts();
        }

        AbstractMetricSpace<float[]> pcaMetricSpace = (AbstractMetricSpace<float[]>) additionalParams[paramIDX++];
        Object pcaQ = additionalParams[paramIDX++];

        float[] pcaQData = pcaMetricSpace.getDataOfMetricObject(pcaQ);

        // first phase: voronoi
        List candSetIDs = voronoiFilter.candSetKnnSearch(fullMetricSpace, fullQ, voronoiK, null);

        // simRel preparation
        List<Object> simRelAns = new ArrayList<>();
        Set<Object> objIdUnknownRelation = new HashSet<>();
        Map<Object, float[]> simRelCandidatesMap = new HashMap<>();

        // sketch preparation
        Object qSketch = sketchingTechnique.transformMetricObject(fullQ);
        long[] qSketchData = hammingSpaceForSketches.getDataOfMetricObject(qSketch);
        float range = Float.MAX_VALUE;
        for (int i = 0; i < candSetIDs.size(); i++) {
            Object candID = candSetIDs.get(i);
            boolean add;
            // zkusit skece pokud je ret plna
            if (ret.size() >= k) {
                if (ret.size() > k) {
                    range = adjustAndReturnSearchRadius(ret, k);
                }
                float lowerBound = sketchSecondaryFilter.lowerBound(qSketchData, candID, range);
                if (lowerBound == Float.MAX_VALUE) {
                    continue;
                }
                add = true;
            } else {
                //add to ret   
                distComps++;
                addToRet(ret, candID, fullQData);
                add = false;
            }
            //jinak simRel
            float[] oPCAData = pcaPrefixesMap.get(candID);
            boolean knownRelation = addOToSimRelAnswer(simRelMinK, pcaQData, oPCAData, candID, simRelAns, simRelCandidatesMap);
            if (!knownRelation && add) {
                objIdUnknownRelation.add(candID);
            }
            if (objIdUnknownRelation.size() > 10) {
                distComps += addToFullAnswerWithDists(ret, fullQData, objIdUnknownRelation.iterator());
                objIdUnknownRelation.clear();
            }
            if (i > 200 && (i < 1000 && i % 100 == 0)) {
                distComps += addToFullAnswerWithDists(ret, fullQData, simRelAns.iterator());
            }

        }
        simRelAns.addAll(objIdUnknownRelation);
        // check by sketches again
        for (Object candID : simRelAns) {
            float lowerBound = sketchSecondaryFilter.lowerBound(qSketchData, candID, range);
            if (lowerBound == Float.MAX_VALUE) {
                continue;
            }
            addToRet(ret, candID, fullQData);
            range = adjustAndReturnSearchRadius(ret, k);
        }

        t += System.currentTimeMillis();
        incDistsComps(qId, distComps);
        LOG.log(Level.INFO, "distancesCounter;{0}; simRelCounter;{1}", new Object[]{distComps, simRelEvalCounter});
        incTime(qId, t);
        LOG.log(Level.INFO, "Evaluated query {2} using {0} dist comps. Time: {1}", new Object[]{distComps, t, qId.toString()});
        return ret;

    }

    private boolean addOToSimRelAnswer(int k, float[] queryObjectData, float[] oData, Object idOfO, List<Object> ansOfSimRel, Map<Object, float[]> mapOfData) {
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

    private final Set checked = new HashSet();

    private int addToFullAnswerWithDists(TreeSet<Entry<Object, Float>> queryAnswer, T fullQData, Iterator<Object> iterator) {
        int distComps = 0;
        Set<Object> currKeys = new HashSet();
        for (Map.Entry<Object, Float> entry : queryAnswer) {
            currKeys.add(entry.getKey());
        }
        while (iterator.hasNext()) {
            Object key = iterator.next();
            if (!checked.contains(key) && !currKeys.contains(key)) {
                addToRet(queryAnswer, key, fullQData);
                distComps++;
                checked.add(key);
            }
        }
        return distComps;
    }

    @Override
    public List<Object> candSetKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    private void addToRet(TreeSet<Entry<Object, Float>> ret, Object candID, T fullQData) {
        T candData = fullObjectsStorage.get(candID);
        float distance = fullDF.getDistance(fullQData, candData);
        ret.add(new AbstractMap.SimpleEntry(candID, distance));
    }

    @Override
    public String getResultName() {
        return "VorSkeSim";
    }

}
