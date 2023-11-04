/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package vm.metricSpace.distance.impl;

import vm.metricSpace.distance.DistanceFunctionInterface;

/**
 *
 * @author Vlada
 */
public class CosineDistance extends DistanceFunctionInterface<float[]> {

    @Override
    public float getDistance(float[] o1, float[] o2) {
        if (o1 == o2) {
            return 0;
        }
        if (o2.length != o1.length) {
            throw new IllegalArgumentException("Cannot compute distance on different vector dimensions (" + o1.length + ", " + o2.length + ")");
        }
        float numerator = 0;
        float o1Norm = 0;
        float o2Norm = 0;
        for (int i = 0; i < o1.length; i++) {
            numerator += o1[i] * o2[i];
            o1Norm += o1[i] * o1[i];
            o2Norm += o2[i] * o2[i];
        }
        float denominator = o1Norm * o2Norm;
        return Math.max(0, 1 - numerator / denominator);
    }

    @Override
    public float getDistance(float[] o1, float[] o2, Object... additionalParams) {
        if (o1 == o2) {
            return 0;
        }
        if (o2.length != o1.length) {
            throw new IllegalArgumentException("Cannot compute distance on different vector dimensions (" + o1.length + ", " + o2.length + ")");
        }
        float numerator = 0;
        float o1Norm = (float) additionalParams[0];
        float o2Norm = (float) additionalParams[1];
        for (int i = 0; i < o1.length; i++) {
            numerator += o1[i] * o2[i];
        }
        float denominator = o1Norm * o2Norm;
        return 1 - numerator / denominator;
    }

}
