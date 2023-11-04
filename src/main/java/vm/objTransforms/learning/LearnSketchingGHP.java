package vm.objTransforms.learning;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.objTransforms.objectToSketchTransformators.AbstractObjectToSketchTransformator;
import vm.objTransforms.objectToSketchTransformators.SketchingGHP;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.objTransforms.storeLearned.GHPSketchingPivotPairsStoreInterface;

/**
 *
 * @author xmic
 */
public class LearnSketchingGHP {

    private static final Logger LOG = Logger.getLogger(LearnSketchingGHP.class.getName());
    public static final Float BALANCE_TOLERATION = 0.05f;

    private final int numberOfPivotsForMakingAllPairs;
    private final int maxNumberOfBalancedForGeneticHeuristic;

    private final GHPSketchingPivotPairsStoreInterface storage;

    public LearnSketchingGHP(Dataset dataset, GHPSketchingPivotPairsStoreInterface sketchingStorage) {
        this(dataset, sketchingStorage, 512, 15000);
    }

    public LearnSketchingGHP(Dataset dataset, GHPSketchingPivotPairsStoreInterface sketchingStorage, int numberOfPivotsForMakingAllPairs, int maxNumberOfBalancedForGeneticHeuristic) {
        this.storage = sketchingStorage;
        this.numberOfPivotsForMakingAllPairs = numberOfPivotsForMakingAllPairs;
        this.maxNumberOfBalancedForGeneticHeuristic = maxNumberOfBalancedForGeneticHeuristic;
    }

    public void evaluate(Dataset dataset, int sampleSetSize, int[] sketchLengths, float balance, Object... additionalInfoForDistF) {
        if (balance < 0 || balance > 1) {
            throw new IllegalArgumentException("Set balanced from range (0, 1).");
        }
        AbstractMetricSpace metricSpace = dataset.getMetricSpace();
        DistanceFunctionInterface<Object> df = dataset.getDistanceFunction();
        List<Object> sampleOfDataset = dataset.getSampleOfDataset(sampleSetSize);
        List<Object> pivots = dataset.getPivots(numberOfPivotsForMakingAllPairs);

        AbstractObjectToSketchTransformator sketchingTechnique = new SketchingGHP(df, metricSpace, pivots, true, true, additionalInfoForDistF);

        List<BitSet> columnWiseSketches = sketchingTechnique.createColumnwiseSketches(metricSpace, sampleOfDataset, df);
        int[] balancedIndexes = getIndexesOfProperlyBalanced(columnWiseSketches, balance, sampleOfDataset.size(), sketchingTechnique);
        columnWiseSketches = Tools.filterList(columnWiseSketches, balancedIndexes);
        sketchingTechnique.preserveJustGivenBits(balancedIndexes);
        Object[] pivotsBalancedBackup = Tools.copyArray(sketchingTechnique.getPivots());

        AuxiliaryLearnSketching aux = new AuxiliaryLearnSketching();
        float[][] sketchesCorrelations = aux.getSketchesCorrelations(columnWiseSketches, sampleSetSize, balance == 0.5f);

        if (columnWiseSketches.size() < maxNumberOfBalancedForGeneticHeuristic) {
            LOG.log(Level.WARNING, "Only {0} bits balanced up to {1} were found.", new Object[]{columnWiseSketches.size(), balance});
        } else {
            LOG.log(Level.INFO, "{0} bits balanced up to {1} were found. {2} will be selected by greedy heuristic", new Object[]{columnWiseSketches.size(), balance, maxNumberOfBalancedForGeneticHeuristic});
            int[] idxs = filterOutMostCorrelatedByGreedyHeuristic(sketchesCorrelations, maxNumberOfBalancedForGeneticHeuristic);
            columnWiseSketches = Tools.filterList(columnWiseSketches, idxs);
            sketchingTechnique.preserveJustGivenBits(idxs);
            sketchesCorrelations = aux.getSketchesCorrelations(columnWiseSketches, sampleSetSize, balance == 0.5f);
        }
        for (int sketchLength : sketchLengths) {
            LOG.log(Level.INFO, "\n\nStarting learning of sketches of length {0} bits.", new Object[]{sketchLength});
            String resultName = sketchingTechnique.getNameOfTransformedSetOfObjects(dataset.getDatasetName(), sketchLength, balance);
            int[] lowCorrelatedBits = selectLowCorrelatedBits(sketchLength, columnWiseSketches, sketchesCorrelations);
            sketchingTechnique.preserveJustGivenBits(lowCorrelatedBits);
            storage.storeSketching(resultName, metricSpace, Tools.arrayToList(sketchingTechnique.getPivots()), numberOfPivotsForMakingAllPairs, maxNumberOfBalancedForGeneticHeuristic);
            sketchingTechnique = new SketchingGHP(df, metricSpace, pivotsBalancedBackup, false, true);
        }
    }

    private int[] getIndexesOfProperlyBalanced(List<BitSet> columnWiseSketches, float balance, int sampleObjectCount, AbstractObjectToSketchTransformator sketchingTechnique) {
        List<Integer> balanced = new ArrayList<>();
        int min = (int) ((balance - BALANCE_TOLERATION) * sampleObjectCount);
        int max = (int) ((balance + BALANCE_TOLERATION) * sampleObjectCount);
        for (int i = 0; i < columnWiseSketches.size(); i++) {
            BitSet invertedSketch = columnWiseSketches.get(i);
            int card = invertedSketch.cardinality();
            if (card <= max && card >= min) {
                balanced.add(i);
            } else {
                int reversedCard = sampleObjectCount - card;
                if (reversedCard <= max && reversedCard >= min) {
                    sketchingTechnique.redefineSketchingToSwitchBit(i);
                    balanced.add(i);
                }
            }
        }
        int[] array = new int[balanced.size()];
        for (int i = 0; i < balanced.size(); i++) {
            int value = balanced.get(i);
            array[i] = value;
        }
        return array;
    }

    private int[] selectLowCorrelatedBits(int sketchLength, List<BitSet> columnWiseSketches, float[][] sketchesCorrelations) {
        if (sketchLength > columnWiseSketches.size()) {
            throw new RuntimeException("Length of long sketches is " + columnWiseSketches.size() + ". Cannot create sketches of length " + sketchLength);
        }
        if (sketchLength <= 1 || sketchLength >= columnWiseSketches.size()) {
            throw new IllegalArgumentException("That is nonsense - create longer sketches");
        }
        AuxiliaryLearnSketching aux = new AuxiliaryLearnSketching();
        return aux.selectLowCorrelatedBits(sketchesCorrelations, sketchLength);
    }

    private int[] filterOutMostCorrelatedByGreedyHeuristic(float[][] correlationMatrix, int retSize) {
        SortedSet<Map.Entry<Integer, Float>> sumOfCorrelations = Tools.evaluateSumsPerRow(correlationMatrix);
        Set<Integer> indexesToRemove = new HashSet<>();
        Tools.MapByValueComparator comparator = new Tools.MapByValueComparator();
        while (sumOfCorrelations.size() > retSize) {
            Map.Entry<Integer, Float> lastEntry = sumOfCorrelations.last();
            int removeIndex = lastEntry.getKey();
            indexesToRemove.add(removeIndex);
            SortedSet<Map.Entry<Integer, Float>> sumOfCorrelations2 = new TreeSet<>(comparator);
            Iterator<Map.Entry<Integer, Float>> it = sumOfCorrelations.iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Float> next = it.next();
                int idx = next.getKey();
                if (idx != removeIndex) {
                    float newSum = next.getValue() - correlationMatrix[removeIndex][idx];
                    sumOfCorrelations2.add(new AbstractMap.SimpleEntry<>(idx, newSum));
                }
            }
            sumOfCorrelations = sumOfCorrelations2;
        }
//        ArrayList<Map.Entry<Integer, Float>> arrayList = new ArrayList<>(sumOfCorrelations);
//        for (int i = 1; i < arrayList.size(); i++) {
//            Map.Entry<Integer, Float> prev = arrayList.get(i - 1);
//            Map.Entry<Integer, Float> cur = arrayList.get(i);
//            if (prev.getValue().equals(cur.getValue())) {
//                System.err.println("Indexes with the same sum: " + prev.getKey() + ", " + cur.getKey() + ", i: " + i + ", " + prev.getValue() + ", " + cur.getValue());
//            }
//        }
        int[] ret = new int[retSize];
        int idx = 0;
        for (Integer i = 0; i < correlationMatrix.length; i++) {
            if (!indexesToRemove.contains(i)) {
                ret[idx] = i;
                idx++;
            }
        }
        return ret;
    }

}
