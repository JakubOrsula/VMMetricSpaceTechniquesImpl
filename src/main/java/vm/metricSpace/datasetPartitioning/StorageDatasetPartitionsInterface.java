package vm.metricSpace.datasetPartitioning;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author Vlada
 */
public interface StorageDatasetPartitionsInterface {

    public void store(Map<Object, SortedSet<Object>> mapping, String datasetName, int origPivotCount);

    public Map<Object, TreeSet<Object>> load(String datasetName, int origPivotCount);
}
