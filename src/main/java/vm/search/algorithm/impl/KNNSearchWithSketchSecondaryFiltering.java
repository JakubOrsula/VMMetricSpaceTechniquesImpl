/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.algorithm.impl;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.bounding.nopivot.impl.SecondaryFilteringWithSketches;
import vm.objTransforms.objectToSketchTransformators.AbstractObjectToSketchTransformator;
import vm.search.algorithm.SearchingAlgorithm;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class KNNSearchWithSketchSecondaryFiltering<T> extends SearchingAlgorithm<T> {

    private static final Logger LOG = Logger.getLogger(KNNSearchWithSketchSecondaryFiltering.class.getName());

    private final Dataset fullDataset;
    private final SecondaryFilteringWithSketches filter;
    private final AbstractObjectToSketchTransformator sketchingTechnique;

    public KNNSearchWithSketchSecondaryFiltering(Dataset fullDataset, SecondaryFilteringWithSketches filter, AbstractObjectToSketchTransformator sketchingTechnique) {
        this.filter = filter;
        this.fullDataset = fullDataset;
        this.sketchingTechnique = sketchingTechnique;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> hammingSpace, Object fullQuery, int k, Iterator<Object> objects, Object... params) {
        long t = -System.currentTimeMillis();
        TreeSet<Map.Entry<Object, Float>> currAnswer = null;
        if (params.length > 0 && params[0] instanceof TreeSet) {
            currAnswer = (TreeSet<Map.Entry<Object, Float>>) params[0];
        }
        DistanceFunctionInterface fullDF = fullDataset.getDistanceFunction();
        AbstractMetricSpace fullDatasetMetricSpace = fullDataset.getMetricSpace();
        Object qData = fullDatasetMetricSpace.getDataOfMetricObject(fullQuery);
        Object qSketch = sketchingTechnique.transformMetricObject(fullQuery);
        long[] qSketchData = (long[]) hammingSpace.getDataOfMetricObject(qSketch);
        Object qId = hammingSpace.getIDOfMetricObject(qSketch);
        TreeSet<Map.Entry<Object, Float>> ret = currAnswer == null ? new TreeSet<>(new Tools.MapByValueComparator()) : currAnswer;
        AtomicInteger distComps = new AtomicInteger();
        boolean justIDsProvided = params.length > 0 && params[0] instanceof Map;
        Map fullObjectsStorage = null;
        if (justIDsProvided) {
            fullObjectsStorage = (Map) params[0];
        }
        while (objects.hasNext()) {
            Object fullO = objects.next();
            Object oId;
            if (justIDsProvided) {
                oId = fullO;
            } else {
                oId = hammingSpace.getIDOfMetricObject(fullO);
            }
            float range = adjustAndReturnSearchRadius(ret, k);
            if (range < Float.MAX_VALUE) {
                float lowerBound = filter.lowerBound(qSketchData, oId, range);
                if (lowerBound == Float.MAX_VALUE) {
                    continue;
                }
            }
            distComps.incrementAndGet();
            Object oData;
            if (justIDsProvided) {
                oData = fullObjectsStorage.get(oId);
            } else {
                oData = fullDatasetMetricSpace.getDataOfMetricObject(fullO);
            }
            float distance = fullDF.getDistance(qData, oData);
            ret.add(new AbstractMap.SimpleEntry<>(oId, distance));
        }

        adjustAndReturnSearchRadius(ret, k);
        t += System.currentTimeMillis();
        incTime(qId, t);

        incDistsComps(qId, distComps.get());
        LOG.log(Level.INFO, "Evaluated query {2} using {0} dist comps. Time: {1}", new Object[]{distComps.get(), t, qId.toString()});
        return ret;
    }

    @Override
    public List candSetKnnSearch(AbstractMetricSpace hammingMetricSpace, Object skQ, int k, Iterator objects, Object... additionalParams) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String getResultName() {
        return filter.getTechFullName();
    }

}
