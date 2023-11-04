package vm.metricSpace.distance.storedPrecomputedDistances;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Vlada
 */
public abstract class AbstractPrecomputedDistancesMatrixLoader {

    /*
    Mapping of object IDs to column indexes
     */
    protected Map<String, Integer> columnHeaders;
    /*
    Mapping of object IDs to row indexes
     */
    protected Map<String, Integer> rowHeaders;

    public AbstractPrecomputedDistancesMatrixLoader() {
        this.rowHeaders = new HashMap<>();
        this.columnHeaders = new HashMap<>();
    }

    /**
     *
     * @param datasetName
     * @param pivotSetName
     * @param pivotCount if -1 then all pivots are used (pivots correspond to
     * columns)
     * @return map of distances (for instance, rows correspond to the objects
     * from the dataset, columns correspod to the pivots)
     */
    public abstract float[][] loadPrecomPivotsToObjectsDists(String datasetName, String pivotSetName, int pivotCount);

    public float[][] loadPrecomPivotsToObjectsDists(String datasetName, String pivotSetName) {
        return this.loadPrecomPivotsToObjectsDists(datasetName, pivotSetName, -1);
    }

    public Map<String, Integer> getRowHeaders() {
        return Collections.unmodifiableMap(rowHeaders);
    }

    public Map<String, Integer> getColumnHeaders() {
        return Collections.unmodifiableMap(columnHeaders);
    }

}
