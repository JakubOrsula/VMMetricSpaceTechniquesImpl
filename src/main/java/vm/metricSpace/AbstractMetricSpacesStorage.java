package vm.metricSpace;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.datatools.Tools;

/**
 *
 * @author Vlada
 */
public abstract class AbstractMetricSpacesStorage {

    private static final Logger LOG = Logger.getLogger(AbstractMetricSpacesStorage.class.getName());
    
    public static enum OBJECT_TYPE {
        DATASET_OBJECT,
        PIVOT_OBJECT,
        QUERY_OBJECT;
    }

    /**
     * Get iterator over metric objects in a given dataset
     *
     * @param datasetName volutary, if it helps you to distinguish your datasets
     * @param params volutary, again, if you want to use them to get your metric
     * objects
     * @return iterator over metric objects in a specific dataset
     */
    public abstract Iterator<Object> getObjectsFromDataset(String datasetName, Object... params);

    /**
     * Get pivots for the specified datasets. My codes usually assume a set of
     * 2560 pivots and just some of them are actually used. In general, 256 -
     * 2560 pivots are fine for this library.
     *
     * @param pivotSetName volutary, if it helps you to distinguish your
     * datasets
     * @param params volutary, again, if you want to use them
     * @return list of pivoting objects for a specific dataset
     */
    public abstract List<Object> getPivots(String pivotSetName, Object... params);

    /**
     * Get query objects to test developed algorithms (usually searching). I
     * usually test 1000 randomly selected query objects, but feel free to test
     * an arbitrary nzumber of them.
     *
     * @param querySetName volutary, if it helps you to distinguish your
     * datasets
     * @param params volutary, again, if you want to use them
     * @return list of query objects used for testing
     */
    public abstract List<Object> getQueryObjects(String querySetName, Object... params);

    public abstract void storeObjectToDataset(Object metricObject, String datasetName, Object... additionalParamsToStoreWithNewDataset);

    public abstract void storePivots(List<Object> pivots, String pivotSetNane, Object... additionalParamsToStoreWithNewPivotSet);

    public abstract void storeQueryObjects(List<Object> queryObjs, String querySetName, Object... additionalParamsToStoreWithNewQuerySet);

    public abstract int getPrecomputedDatasetSize(String datasetName);

    public int getNumberOfPivots(String pivotSetName, Object... params) {
        List<Object> pivots = getPivots(pivotSetName, params);
        return pivots.size();
    }

    public int getNumberOfQueries(String querySetName, Object... params) {
        List<Object> queries = getQueryObjects(querySetName, params);
        return queries.size();
    }

    public int reevaluatetNumberOfObjectsInDataset(String datasetName, Object... params) {
        Iterator<Object> metricObjects = getObjectsFromDataset(datasetName);
        int ret = 0;
        for (ret = 0; metricObjects.hasNext(); ret++) {
            metricObjects.next();
        }
        System.out.println("Number of objects in a dataset: " + datasetName + " is: " + ret);
        return ret;
    }

    protected abstract void updateDatasetSize(String datasetName, int count);

    public void updateDatasetSize(String datasetName) {
        int count = reevaluatetNumberOfObjectsInDataset(datasetName);
        LOG.log(Level.INFO, "Found {0} objects in the dataset {1}", new Object[]{count, datasetName});
        updateDatasetSize(datasetName, count);
    }

    /**
     *
     * @param datasetName volutary, if it helps you to distinguish your datasets
     * @param objectCount
     * @param params volutary, again, if you want to use them. First value is
     * the number of objects to return.
     * @return list of sample objects from a specific dataset
     */
    public List<Object> getSampleOfDataset(String datasetName, int objectCount, Object... params) {
        params = Tools.concatArrays(params, new Object[]{objectCount});
        Iterator<Object> it = getObjectsFromDataset(datasetName, params);
        List<Object> ret = Tools.getObjectsFromIterator(it, objectCount);
        if (ret.size() != objectCount && objectCount > 0) {
            LOG.log(Level.SEVERE, "I was not able to find {0} objects in the dataset. Found just {1} objects", new Object[]{objectCount, ret.size()});
        }
        return ret;
    }

    /**
     *
     * @param it iterator over metric objects to storeObjectToDataset in a DB
     * @param count maximum number of metric objects to storeObjectToDataset. 0
     * or -1 for all.
     * @param datasetName id of the group of objects (e.g. dataset id, or query
     * set id, or pivot set id) - this info is stored with each object
     * @param additionalParamsToStoreWithNewDataset see the same param in the
     * method storeObjectToDataset
     * @return 
     */
    public synchronized int storeObjectsToDataset(Iterator<Object> it, int count, String datasetName, Object... additionalParamsToStoreWithNewDataset) {
        int maxCount = count > 0 ? count : Integer.MAX_VALUE;
        int counter;
        for (counter = 0; it.hasNext() && counter <= maxCount; counter++) {
            Object o = it.next();
            if (o == null) {
                continue;
            }
            storeObjectToDataset(o, datasetName, additionalParamsToStoreWithNewDataset);
            if (counter % 10000 == 0) {
                LOG.log(Level.INFO, "Inserted {0} metric objects", new Object[]{counter});
            }

        }
        LOG.log(Level.INFO, "Insert finished with {0} metric objects inserted", new Object[]{counter});
        return counter;
    }
}
