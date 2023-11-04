/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.metricSpace.distance.storedPrecomputedDistances;

import java.util.Map;

/**
 *
 * @author xmic
 */
public class MainMemoryStoredPrecomputedDistances extends AbstractPrecomputedDistancesMatrixLoader {

    private final float[][] dists;

    public MainMemoryStoredPrecomputedDistances(float[][] dists, Map<String, Integer> columnHeaders, Map<String, Integer> rowHeaders) {
        super.columnHeaders = columnHeaders;
        super.rowHeaders = rowHeaders;
        this.dists = dists;
    }

    @Override
    public float[][] loadPrecomPivotsToObjectsDists(String datasetName, String pivotSetName, int pivotCount) {
        return dists;
    }

}
