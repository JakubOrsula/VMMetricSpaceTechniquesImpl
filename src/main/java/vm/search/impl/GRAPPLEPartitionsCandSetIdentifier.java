/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.impl;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import vm.datatools.Tools;
import vm.datatools.Tools.MapByValueComparatorWithOwnKeyComparator;
import vm.metricSpace.Dataset;
import vm.metricSpace.datasetPartitioning.StorageDatasetPartitionsInterface;
import vm.metricSpace.distance.DistanceFunctionInterface;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class GRAPPLEPartitionsCandSetIdentifier<T> extends VoronoiPartitionsCandSetIdentifier<T> {

    public GRAPPLEPartitionsCandSetIdentifier(Dataset dataset, StorageDatasetPartitionsInterface voronoiPartitioningStorage, int pivotCountUsedForPartitioningLearning) {
        super(dataset, voronoiPartitioningStorage, pivotCountUsedForPartitioningLearning);
    }

    @Override
    public Object[] evaluateKeyOrdering(DistanceFunctionInterface<T> df, Map<Object, T> pivotsMap, T qData, Object... params) {
        Iterator<Map.Entry<Object, T>> it1 = pivotsMap.entrySet().iterator();
        Iterator<Map.Entry<Object, T>> it2 = pivotsMap.entrySet().iterator();
        SortedSet<AbstractMap.SimpleEntry<Object, Float>> pivotPairs = new TreeSet(new Tools.MapByValueComparator<>());
        Map<String, Float> distsP1P2 = new HashMap<>();
        while (it1.hasNext()) {
            Map.Entry<Object, T> p1 = it1.next();
            Object p1ID = p1.getKey();
            T p1Data = p1.getValue();
            while (it2.hasNext()) {
                Map.Entry<Object, T> p2 = it2.next();
                Object p2ID = p2.getKey();
                if (p1ID == p2ID) {
                    continue;
                }
                T p2Data = p2.getValue();
                String key1 = p1ID + "-" + p2ID;
                float distQP1 = df.getDistance(qData, p1Data);
                float distQP2 = df.getDistance(qData, p2Data);
                Float distP1P2 = distsP1P2.get(key1);
                if (distP1P2 == null) {
                    distP1P2 = df.getDistance(p1Data, p2Data);
                    distsP1P2.put(key1, distP1P2);
                }
                float priority = (distQP2 * distQP2 + distQP1 * distQP1 - distP1P2 * distP1P2) / (2 * distQP1 * distQP2);
                String key2 = p2ID + "-" + p1ID;
                if (distQP1 < distQP2) {
                    pivotPairs.add(new AbstractMap.SimpleEntry<>(key1, priority - Float.MIN_NORMAL));
                    pivotPairs.add(new AbstractMap.SimpleEntry<>(key2, priority + Float.MIN_NORMAL));
                } else {
                    pivotPairs.add(new AbstractMap.SimpleEntry<>(key2, priority - Float.MIN_NORMAL));
                    pivotPairs.add(new AbstractMap.SimpleEntry<>(key1, priority + Float.MIN_NORMAL));
                }
            }

        }
    }

    private class ComparatorForPriorityQueue extends MapByValueComparatorWithOwnKeyComparator<Float> {

        public ComparatorForPriorityQueue(Comparator<Float> comp) {
            super(comp);
        }

    }

}
