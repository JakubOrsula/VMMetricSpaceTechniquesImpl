/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search;

import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.queryResults.QueryExecutionStatsStoreInterface;
import vm.queryResults.QueryNearestNeighboursStoreInterface;
import vm.queryResults.errorOnDistEvaluation.ErrorOnDistEvaluator;
import vm.queryResults.errorOnDistEvaluation.ErrorsOnDistStoreInterface;
import vm.queryResults.recallEvaluation.RecallOfCandsSetsEvaluator;
import vm.queryResults.recallEvaluation.RecallOfCandsSetsStoreInterface;
import vm.search.algorithm.SearchingAlgorithm;

/**
 *
 * @author Vlada
 */
public class AlgorithmEvaluator {

    private static final Logger LOG = Logger.getLogger(AlgorithmEvaluator.class.getName());

    private final SearchingAlgorithm alg;
    private final QueryExecutionStatsStoreInterface statsStorage;
    private final QueryNearestNeighboursStoreInterface resultsStorage;
    private final RecallOfCandsSetsStoreInterface recallStorage;
    private final ErrorsOnDistStoreInterface eodStorage;

    public AlgorithmEvaluator(SearchingAlgorithm alg, QueryExecutionStatsStoreInterface statsStorage, QueryNearestNeighboursStoreInterface resultsStorage, RecallOfCandsSetsStoreInterface recallStorage, ErrorsOnDistStoreInterface eodStorage) {
        this.alg = alg;
        this.statsStorage = statsStorage;
        this.resultsStorage = resultsStorage;
        this.recallStorage = recallStorage;
        this.eodStorage = eodStorage;
    }

    public void evaluate(Dataset dataset, List queries, int k, int kCandSetMaxSize, String resultName, Object... additionalParams) {
        long overallTime = -System.currentTimeMillis();
        AbstractMetricSpace metricSpace = dataset.getMetricSpace();
        String datasetName = dataset.getDatasetName();
        String querySetName = dataset.getQuerySetName();
        TreeSet[] results = alg.completeKnnSearchWithPartitioningForQuerySet(metricSpace, queries, k, kCandSetMaxSize, dataset.getKeyValueStorage(), additionalParams);
        overallTime += System.currentTimeMillis();

        LOG.log(Level.INFO, "Storing statistics of queries");
        statsStorage.storeStatsForQueries(alg.getDistCompsPerQueries(), alg.getTimesPerQueries(), alg.getAddditionalStats());
        statsStorage.save();

        LOG.log(Level.INFO, "Storing results of queries");
        resultsStorage.storeQueryResults(metricSpace, queries, results, datasetName, querySetName, resultName);

        LOG.log(Level.INFO, "Evaluating accuracy of queries");
        RecallOfCandsSetsEvaluator evaluator = new RecallOfCandsSetsEvaluator(resultsStorage, recallStorage);
        evaluator.evaluateAndStoreRecallsOfQueries(datasetName, querySetName, k, datasetName, querySetName, resultName, k);
        recallStorage.save();
        LOG.log(Level.INFO, "Evaluating error on distance");
        ErrorOnDistEvaluator eodEvaluator = new ErrorOnDistEvaluator(resultsStorage, eodStorage);
        eodEvaluator.evaluateAndStoreErrorsOnDist(datasetName, querySetName, k, datasetName, querySetName, resultName);
        recallStorage.save();

        LOG.log(Level.INFO, "Overall time: {0}", overallTime);
    }

}
