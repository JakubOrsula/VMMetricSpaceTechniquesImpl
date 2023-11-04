/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.algorithm.impl;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.metricSpace.ToolsMetricDomain;
import vm.metricSpace.datasetPartitioning.StorageDatasetPartitionsInterface;
import vm.metricSpace.datasetPartitioning.impl.GRAPPLEPartitioning;
import vm.metricSpace.distance.DistanceFunctionInterface;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class GRAPPLEPartitionsCandSetIdentifier<T> extends VoronoiPartitionsCandSetIdentifier<T> {

    private final Map<String, Float> distsP1P2 = new HashMap<>();

    public GRAPPLEPartitionsCandSetIdentifier(Dataset dataset, StorageDatasetPartitionsInterface GRAPPLEPartitioningStorage, int pivotCountUsedForPartitioningLearning) {
        super(dataset, GRAPPLEPartitioningStorage, pivotCountUsedForPartitioningLearning);
        Object[] pivotsArray = pivotsMap.entrySet().toArray();
        for (int idx1 = 0; idx1 < pivotsArray.length - 1; idx1++) {
            Map.Entry<Object, T> p1 = (Map.Entry<Object, T>) pivotsArray[idx1];
            Object p1ID = p1.getKey();
            T p1Data = p1.getValue();
            for (int idx2 = idx1 + 1; idx2 < pivotsArray.length; idx2++) {
                Map.Entry<Object, T> p2 = (Map.Entry<Object, T>) pivotsArray[idx2];
                Object p2ID = p2.getKey();
                T p2Data = p2.getValue();
                String key1 = p1ID + "-" + p2ID;
                float distP1P2 = df.getDistance(p1Data, p2Data);
                distsP1P2.put(key1, distP1P2);
            }
        }
    }

    @Override
    public Object[] evaluateKeyOrdering(DistanceFunctionInterface<T> df, Map<Object, T> pivotsMap, T qData, Object... params) {
        Object[] pivotsArray = pivotsMap.entrySet().toArray();
        SortedSet<AbstractMap.SimpleEntry<Object, Float>> pivotPairs = new TreeSet(new Tools.MapByValueComparator<>());
        for (int idx1 = 0; idx1 < pivotsArray.length - 1; idx1++) {
            Map.Entry<Object, T> p1 = (Map.Entry<Object, T>) pivotsArray[idx1];
            Object p1ID = p1.getKey();
            T p1Data = p1.getValue();
            float distQP1 = df.getDistance(qData, p1Data);
            for (int idx2 = idx1 + 1; idx2 < pivotsArray.length; idx2++) {
                Map.Entry<Object, T> p2 = (Map.Entry<Object, T>) pivotsArray[idx2];
                Object p2ID = p2.getKey();
                T p2Data = p2.getValue();
                float distQP2 = df.getDistance(qData, p2Data);
                String key1 = p1ID + "-" + p2ID;
                String key2 = p2ID + "-" + p1ID;
                float distP1P2 = distsP1P2.get(key1);

                float alphaCosine = (distQP2 * distQP2 + distQP1 * distQP1 - distP1P2 * distP1P2);
                float diff = distQP1 > distQP2 ? 0.01f : -0.01f;
                pivotPairs.add(new AbstractMap.SimpleEntry<>(key1, alphaCosine + diff));
                pivotPairs.add(new AbstractMap.SimpleEntry<>(key2, alphaCosine));
            }
        }
        Iterator<AbstractMap.SimpleEntry<Object, Float>> it = pivotPairs.iterator();
        Object[] ret = new Object[pivotPairs.size()];
        for (int i = 0; it.hasNext(); i++) {
            AbstractMap.SimpleEntry<Object, Float> entry = it.next();
            ret[i] = entry.getKey();
        }
        return ret;
    }

    @Override
    public TreeSet<Map.Entry<Object, Float>> completeKnnSearch(AbstractMetricSpace<T> metricSpace, Object queryObject, int k, Iterator<Object> objects, Object... additionalParams) {
        AtomicLong t = new AtomicLong(-System.currentTimeMillis());
        AtomicInteger distComps = new AtomicInteger();
        Object qID = metricSpace.getIDOfMetricObject(queryObject);
        T qData = metricSpace.getDataOfMetricObject(queryObject);
        Map keyValueStorage = (Map) additionalParams[0];
        int kCandSetMaxSize = (int) additionalParams[1];
        Iterator<Object> candSet = candSetKnnSearch(metricSpace, queryObject, kCandSetMaxSize, objects, additionalParams).iterator();
        TreeSet<Map.Entry<Object, Float>> ret = new TreeSet<>(new Tools.MapByValueComparator());
        Map<Object, Float> queryToPivotsDists = ToolsMetricDomain.evaluateDistsToPivots(qData, pivotsMap, df);
        float range = Float.MAX_VALUE;
        int total = 0;
        int ok = 0;
        while (candSet.hasNext()) {
            GRAPPLEPartitioning.ObjectMetadata oMetadata = (GRAPPLEPartitioning.ObjectMetadata) candSet.next();
            boolean add;
            if (ret.size() < k) {
                add = true;
            } else {
                add = oMetadata.getLBdOQ(queryToPivotsDists, range);
            }
            if (add) {
                Object oID = oMetadata.getoID();
                T metricObjectData;
                metricObjectData = (T) keyValueStorage.get(oID);
                float distance = df.getDistance(qData, metricObjectData);
                distComps.incrementAndGet();
                ret.add(new AbstractMap.SimpleEntry<>(oID, distance));
                if (ret.size() > k) {
                    ret.remove(ret.last());
                }
                range = ret.last().getValue();
            } else {
                ok++;
            }
            total++;
        }
        t.addAndGet(System.currentTimeMillis());
        timesPerQueries.put(qID, t);
        distCompsPerQueries.put(qID, distComps);
        System.out.println(ok + ";" + total + ";" + ((float) ok * 100) / total + "; %");
        return ret;

    }
}
