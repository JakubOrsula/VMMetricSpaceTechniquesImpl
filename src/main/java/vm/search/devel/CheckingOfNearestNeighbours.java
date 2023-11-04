/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.search.devel;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.queryResults.QueryNearestNeighboursStoreInterface;
import vm.queryResults.recallEvaluation.RecallOfCandsSetsEvaluator;

/**
 *
 * @author Vlada
 */
public class CheckingOfNearestNeighbours {

    private static final Logger LOG = Logger.getLogger(CheckingOfNearestNeighbours.class.getName());
    private final Map<String, TreeSet<Map.Entry<Object, Float>>> groundTruthForDataset;

    public CheckingOfNearestNeighbours(QueryNearestNeighboursStoreInterface resultsStorage, String groundTruthDatasetName, String groundTruthQuerySetName) {
        groundTruthForDataset = resultsStorage.getGroundTruthForDataset(groundTruthDatasetName, groundTruthQuerySetName);
    }

    /**
     * 
     * @param queryID
     * @param groundTruthNNCount
     * @return k true nearest neighbours
     */
    public Set<String> getIDsOfNNForQuery(String queryID, int groundTruthNNCount) {
        if (!groundTruthForDataset.containsKey(queryID)) {
            LOG.log(Level.SEVERE, "Query object {0} not evaluated in the ground truth", queryID);
            return null;
        }
        Set<String> ret = RecallOfCandsSetsEvaluator.getFirstIDs(queryID, groundTruthForDataset.get(queryID), groundTruthNNCount);
        for (String string : ret) {
            System.out.println(string);
        }
        return ret;
    }
}
