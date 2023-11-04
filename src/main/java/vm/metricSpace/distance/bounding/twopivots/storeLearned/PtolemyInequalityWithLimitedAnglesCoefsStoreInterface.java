package vm.metricSpace.distance.bounding.twopivots.storeLearned;

import java.util.Map;

/**
 *
 * @author xmic
 */
public interface PtolemyInequalityWithLimitedAnglesCoefsStoreInterface {
    
    public void storeCoefficients(Map<Object, float[]> results, String resultName);

    public String getResultDescription(String datasetName, int numberOfSmallestDists, int sampleOCount, int sampleQcount, int pivots, boolean allPivotPairs);

}
