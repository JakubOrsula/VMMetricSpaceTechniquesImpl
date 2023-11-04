package vm.queryResults.recallEvaluation;

/**
 *
 * @author Vlada
 */
public interface RecallOfCandsSetsStoreInterface {

    /**
     *
     * @param queryObjId id of the query object
     * @param recall its recall
     * @param additionalParametersToStore store also the names of the query set,
     * dataset, info about the ground truth and its parameters like the number
     * of nearest neighbours etc.
     */
    public void storeRecallForQuery(Object queryObjId, float recall, Object... additionalParametersToStore);

    public void save();
}
