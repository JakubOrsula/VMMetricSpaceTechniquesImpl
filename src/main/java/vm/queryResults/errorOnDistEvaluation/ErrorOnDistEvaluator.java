package vm.queryResults.errorOnDistEvaluation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import vm.queryResults.QueryNearestNeighboursStoreInterface;

/**
 *
 * @author Vlada
 */
public class ErrorOnDistEvaluator {

    private final QueryNearestNeighboursStoreInterface resultsStorage;
    private final ErrorsOnDistStoreInterface errorsOnDistStorage;

    public ErrorOnDistEvaluator(QueryNearestNeighboursStoreInterface resultsStorage, ErrorsOnDistStoreInterface errorsOnDistStorage) {
        this.resultsStorage = resultsStorage;
        this.errorsOnDistStorage = errorsOnDistStorage;
    }

    public Map<String, Float> evaluateAndStoreErrorsOnDist(String groundTruthDatasetName, String groundTruthQuerySetName, int objCount,
            String candSetName, String candSetQuerySetName, String resultSetName) {

        Map<String, TreeSet<Map.Entry<Object, Float>>> groundTruthForDataset = resultsStorage.getGroundTruthForDataset(groundTruthDatasetName, groundTruthQuerySetName);
        Map<String, TreeSet<Map.Entry<Object, Float>>> candidateSets = resultsStorage.getQueryResultsForDataset(resultSetName, candSetName, candSetQuerySetName);

        Map<String, Float> ret = new HashMap<>();
        Set<String> queryIDs = groundTruthForDataset.keySet();
        for (String queryID : queryIDs) {
            float d1 = getKThDist(groundTruthForDataset.get(queryID), objCount);
            float d2 = getKThDist(candidateSets.get(queryID), objCount);
            float errorOnDist = (d2 - d1) / d1;
            errorsOnDistStorage.storeErrorOnDistForQuery(queryID, errorOnDist);
            System.out.println("Query ID: " + queryID + ", error on distance: " + errorOnDist);
            ret.put(queryID, errorOnDist);
        }
        return ret;
    }

    private Float getKThDist(TreeSet<Map.Entry<Object, Float>> set, int k) {
        if (set == null) {
            return Float.MAX_VALUE;
        }
        Iterator<Map.Entry<Object, Float>> it = set.iterator();
        for (int i = 0; i < k - 1 && it.hasNext(); i++) {
            it.next();
        }
        if (it.hasNext()) {
            return it.next().getValue();
        }
        return null;
    }

}
