package vm.metricSpace.distance.bounding.twopivots.impl;

import vm.metricSpace.distance.bounding.twopivots.TwoPivotsFilter;

/**
 * Filtering that assumes the 4-pointproperty. See: Fig 1 and Equation 1 in
 * https://link.springer.com/chapter/10.1007/978-3-030-89657-7_6 ; Section 3.3.
 * nSimplex projection in
 * https://www.sciencedirect.com/science/article/pii/S030643792030017X and also
 * page 5 in this paper.
 *
 * @author Vlada based on the work of Lucia Vadicamo et al.
 */
public class FourPointBasedFiltering extends TwoPivotsFilter {

    public FourPointBasedFiltering(String resultNamePrefix) {
        super(resultNamePrefix);
    }

    @Override
    public float lowerBound(float distP1P2, float distP2O, float distP1Q, float distP1O, float distP2Q, String p1ID, String p2ID) {
        double xO = evaluateXFunc(distP1O, distP2O, distP1P2);
        double xQ = evaluateXFunc(distP1Q, distP2Q, distP1P2);
        double yO = evaluateYFunc(xO, distP1O, distP1P2);
        double yQ = evaluateYFunc(xQ, distP1Q, distP1P2);
        double deltaX = xO - xQ;
        double deltaY = yO - yQ;
        return (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    @Override
    public float upperBound(float distP1P2, float distP2O, float distP1Q, float distP1O, float distP2Q, String p1ID, String p2ID) {
        double xO = evaluateXFunc(distP1O, distP2O, distP1P2);
        double xQ = evaluateXFunc(distP1Q, distP2Q, distP1P2);
        double yO = evaluateYFunc(xO, distP1O, distP1P2);
        double yQ = evaluateYFunc(xQ, distP1Q, distP1P2);
        double deltaX = xO - xQ;
        double sumY = yO + yQ;
        return (float) Math.sqrt(deltaX * deltaX + sumY * sumY);
    }

    @Override
    protected String getTechName() {
        return "FourPointBasedFiltering";
    }

    /**
     * @return Distance of O from the hyperplane
     */
    private double evaluateXFunc(float distOP1, float distOP2, float distP1P2) {
        return (distOP1 * distOP1 - distOP2 * distOP2) / (2 * distP1P2);
    }

    /**
     *
     * @return distance from the line between p1 and p2
     */
    private double evaluateYFunc(double xO, float distOP1, float distP1P2) {
        double pom = xO + distP1P2 / 2;
        return Math.sqrt(distOP1 * distOP1 - pom * pom);
    }

}
