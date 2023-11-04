package vm.metricSpace.distance.bounding.twopivots.impl;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.metricSpace.distance.bounding.twopivots.TwoPivotsFilter;
import static vm.search.algorithm.impl.KNNSearchWithTwoPivotFiltering.PRINT_DETAILS;

/**
 *
 * @author xmic
 */
public class PtolemaiosFilteringWithLimitedAnglesSimpleCoef extends TwoPivotsFilter {

    private final Map<String, float[]> coefs;
    private static final Logger LOGGER = Logger.getLogger(PtolemaiosFilteringWithLimitedAnglesSimpleCoef.class.getName());
    public static final Integer CONSTANT_FOR_PRECISION = 10000;
    public static final Float RATIO_OF_IGNORED_SMALLEST = 0.0f / 100f; // percentile defining the minimum and the maximum. I.e., 2 times this is ignored.
    public static final Integer NUMBER_OF_TETRAHEDRONS_FOR_LEARNING = 40000000;

    public PtolemaiosFilteringWithLimitedAnglesSimpleCoef(String namePrefix, Map<String, float[]> coefs) {
        super(namePrefix);
        this.coefs = coefs;
    }

    @Override
    public float lowerBound(float distP1P2, float distP2O, float distQP1, float distP1O, float distP2Q, String p1ID, String p2ID) {
        float lb1 = returnBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID, 0) / CONSTANT_FOR_PRECISION;
        float lb2 = returnBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID, 2);
        if (PRINT_DETAILS) {
            System.out.print("lb1;" + lb1 + ";lb2;" + lb2 + ";");
            if (lb1 > lb2) {
                System.out.println(1);
            } else {
                System.out.println(2);
            }
        }
        return Math.max(lb1, lb2);
    }

    @Override
    public float upperBound(float distP1P2, float distP2O, float distQP1, float distP1O, float distP2Q, String p1ID, String p2ID) {
//        return Float.MAX_VALUE;
        float ub1 = returnBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID, 1) / CONSTANT_FOR_PRECISION;
        float ub2 = returnBound(distP1P2, distP2O, distQP1, distP1O, distP2Q, p1ID, p2ID, 3);
        if (PRINT_DETAILS) {
            System.out.print("ub1;" + ub1 + ";ub2;" + ub2 + ";");
            if (ub1 < ub2) {
                System.out.println(1);
            } else {
                System.out.println(2);
            }
        }
        return Math.min(ub1, ub2);
    }

    private float returnBound(float distP1P2, float distP2O, float distQP1, float distP1O, float distP2Q, String p1ID, String p2ID, int index) {
        int signum = index > 1 ? -1 : 1;
        float coef = getCoef(p1ID, p2ID, index);
        double a = distP1P2;
        double ef = distP1O * distP2Q;
        double bd = distQP1 * distP2O;
        double fraction = Math.abs(bd + signum * ef) / a;
        if (PRINT_DETAILS && signum < 0) {
            System.out.println(a + ";" + distP1O + ";" + distP2O + ";" + distQP1 + ";" + distP2Q + ";;;" + bd + ";" + ef + ";;;" + fraction + ";" + coef);
        }
        return (float) (coef * fraction);
    }

    public float getCoef(String p1ID, String p2ID, int index) {
        if (coefs.containsKey(p1ID + "-" + p2ID)) {
            return coefs.get(p1ID + "-" + p2ID)[index];
        }
        if (coefs.containsKey(p2ID + "-" + p1ID)) {
            return coefs.get(p2ID + "-" + p1ID)[index];
        }
        LOGGER.log(Level.SEVERE, "No coef for pivots ({0}-{1}). Coefs for pivot pairs provided: {2}", new Object[]{p1ID, p2ID, coefs.size()});
        throw new Error("No coef provided");
    }

    @Override
    protected String getTechName() {
        return "ptolemaios_limited_angles_simpleCoefs_" + NUMBER_OF_TETRAHEDRONS_FOR_LEARNING;
    }
}
