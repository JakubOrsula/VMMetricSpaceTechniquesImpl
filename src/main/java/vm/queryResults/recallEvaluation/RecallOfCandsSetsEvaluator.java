package vm.queryResults.recallEvaluation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.queryResults.QueryNearestNeighboursStoreInterface;

/**
 *
 * @author Vlada
 */
public class RecallOfCandsSetsEvaluator {

    private final QueryNearestNeighboursStoreInterface resultsStorage;
    private final RecallOfCandsSetsStoreInterface recallStorage;

    public RecallOfCandsSetsEvaluator(QueryNearestNeighboursStoreInterface resultsStorage, RecallOfCandsSetsStoreInterface recallStorage) {
        this.resultsStorage = resultsStorage;
        this.recallStorage = recallStorage;
    }

    public Map<String, Float> evaluateAndStoreRecallsOfQueries(String groundTruthDatasetName, String groundTruthQuerySetName, int groundTruthNNCount,
            String candSetName, String candSetQuerySetName, String resultSetName, Integer candidateNNCount) {

        Map<String, TreeSet<Map.Entry<Object, Float>>> groundTruthForDataset = resultsStorage.getGroundTruthForDataset(groundTruthDatasetName, groundTruthQuerySetName);
        Map<String, TreeSet<Map.Entry<Object, Float>>> candidateSets = resultsStorage.getQueryResultsForDataset(resultSetName, candSetName, candSetQuerySetName);

        Map<String, Float> ret = new HashMap<>();
        Set<String> queryIDs = groundTruthForDataset.keySet();
        for (String queryID : queryIDs) {
            if (!candidateSets.containsKey(queryID)) {
                Logger.getLogger(RecallOfCandsSetsEvaluator.class.getName()).log(Level.WARNING, "Query object {0} not evaluated in the candidates", queryID);
            }
            Set<String> groundTruthForQuery = getFirstIDs(queryID, groundTruthForDataset.get(queryID), groundTruthNNCount);
            Set<String> candidatesForQuery = getFirstIDs(queryID, candidateSets.get(queryID), candidateNNCount);
            int hits = 0;
            for (String id : groundTruthForQuery) {
                if (candidatesForQuery.contains(id)) {
                    hits++;
                }
            }
            float recall = ((float) hits) / groundTruthForQuery.size();
            recallStorage.storeRecallForQuery(queryID, recall, groundTruthDatasetName, groundTruthQuerySetName, groundTruthNNCount, candSetName, candSetQuerySetName, candidateNNCount, resultSetName);
            System.out.println("Query ID: " + queryID + ", recall: " + recall);
            ret.put(queryID, recall);
        }
        recallStorage.save();
        return ret;
    }

    public static final Set<String> getFirstIDs(String queryID, TreeSet<Map.Entry<Object, Float>> evaluatedQuery, Integer count) {
        Set<String> ret = new HashSet<>();
        if (evaluatedQuery == null) {
            return ret;
        }
        int limit = count == null ? Integer.MAX_VALUE : count;
        Iterator<Map.Entry<Object, Float>> it = evaluatedQuery.iterator();
        while (it.hasNext() && ret.size() < limit) {
            Map.Entry<Object, Float> nn = it.next();
            ret.add(nn.getKey().toString());
        }
        if (count != null && count > ret.size()) {
            throw new IllegalArgumentException("The candidate set evaluated the query " + queryID + " does not contain so many objects. Required: " + count + ", found: " + ret.size());
        }
        return ret;
    }
}
