package vm.metricSpace.distance.bounding.nopivot.learning;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.math.Tools;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.metricSpace.distance.DistanceFunctionInterface;
import vm.metricSpace.distance.bounding.nopivot.storeLearned.SecondaryFilteringWithSketchesStoreInterface;

/**
 *
 * @author Vlada
 * @param <T>
 */
public class LearningSecondaryFilteringWithSketches<T> {

    public static final float[] THRESHOLDS_P_CUM = new float[]{0.6f, 0.65f, 0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f};
    public static final Logger LOG = Logger.getLogger(LearningSecondaryFilteringWithSketches.class.getName());

    private final SecondaryFilteringWithSketchesStoreInterface storage;
    private final Dataset fullDataset;
    private final Dataset sketchesDataset;
    //iDim params
    public static final int SKETCHES_SAMPLE_COUNT_FOR_IDIM_PX = 1000000;
    public static final int DISTS_COMPS_FOR_SK_IDIM_AND_PX = 100000000;
    private final File fileOutputForIDim;
    //px params
    private final float distIntervalForPX;
    private final float maxDistForPX;
    private final int sketchLength;
    private final File fileOutputForPX;

    public LearningSecondaryFilteringWithSketches(SecondaryFilteringWithSketchesStoreInterface storage, Dataset fullDataset, Dataset sketchesDataset, File fileOutputForIDim, float distIntervalForPX, float maxDistForPX, int sketchLength, File fileOutputForPX) {
        this.storage = storage;
        this.fullDataset = fullDataset;
        this.sketchesDataset = sketchesDataset;
        this.fileOutputForIDim = fileOutputForIDim;
        this.distIntervalForPX = distIntervalForPX;
        this.maxDistForPX = maxDistForPX;
        this.sketchLength = sketchLength;
        this.fileOutputForPX = fileOutputForPX;
    }

    public void execute() {
        LOG.log(Level.INFO, "Evaluating iDim");
        double iDimOfSketches = evaluateIDimOfSketches();
        LOG.log(Level.INFO, "Evaluating px functions");
        List<Point2D.Float> pxFunctionPoints = learnPXFunctions();

        for (float thresholdPcum : THRESHOLDS_P_CUM) {
            LOG.log(Level.INFO, "Evaluating mapping for thresholdPcum{0}", thresholdPcum);
            Map<Double, Integer> learntMapping = learnMapping(thresholdPcum, iDimOfSketches, pxFunctionPoints);
            if (storage != null) {
                storage.store(learntMapping, thresholdPcum, fullDataset.getDatasetName(), sketchesDataset.getDatasetName(), SKETCHES_SAMPLE_COUNT_FOR_IDIM_PX, DISTS_COMPS_FOR_SK_IDIM_AND_PX, distIntervalForPX);
            }
        }
    }

    private double evaluateIDimOfSketches() {
        if (fileOutputForIDim != null) {
            try {
                System.out.flush();
                System.setOut(new PrintStream(fileOutputForIDim));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(LearningSecondaryFilteringWithSketches.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        List<Object> sketches = sketchesDataset.getSampleOfDataset(SKETCHES_SAMPLE_COUNT_FOR_IDIM_PX);
        int numberOfDists = DISTS_COMPS_FOR_SK_IDIM_AND_PX;
        LOG.log(Level.INFO,"number of dists: " + numberOfDists);
        if (sketches.size() <= 300000) {
            numberOfDists = sketches.size() * 10;
        }
        double[] distances = new double[numberOfDists];
        Random r = new Random();
        int i = 0;
        AbstractMetricSpace<long[]> metricSpaceOfSketches = sketchesDataset.getMetricSpace();
        DistanceFunctionInterface hammingDF = sketchesDataset.getDistanceFunction();
        while (i < numberOfDists) {
            Object sk1 = sketches.get(r.nextInt(sketches.size()));
            Object sk2 = sketches.get(r.nextInt(sketches.size()));
            Object id1 = metricSpaceOfSketches.getIDOfMetricObject(sk1);
            Object id2 = metricSpaceOfSketches.getIDOfMetricObject(sk2);
            if (id1.equals(id2)) {
                continue;
            }
            long[] sk1Data = metricSpaceOfSketches.getDataOfMetricObject(sk1);
            long[] sk2Data = metricSpaceOfSketches.getDataOfMetricObject(sk2);
            float distance = hammingDF.getDistance(sk1Data, sk2Data);
            distances[i] = distance;
            i++;
            if (i % 10 == 0) {
                LOG.log(Level.INFO, "iDim evaluation: {0} distances", i);
            }
        }
        LOG.log(Level.INFO, "Finished iDim evaluation: {0} distances", i);
        double iDim = Tools.getIDim(distances, true);
        return iDim;
    }

    private List<Point2D.Float> learnPXFunctions() {
        if (fileOutputForPX != null) {
            try {
                System.out.flush();
                System.setOut(new PrintStream(fileOutputForPX));
            } catch (FileNotFoundException ex) {
                Logger.getLogger(LearningSecondaryFilteringWithSketches.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        PxEvaluator pxEval = new PxEvaluator(fullDataset, sketchesDataset, SKETCHES_SAMPLE_COUNT_FOR_IDIM_PX, sketchLength, distIntervalForPX);
        
        SortedMap<Float, Float> pxPlot = pxEval.evaluateProbabilities(maxDistForPX, DISTS_COMPS_FOR_SK_IDIM_AND_PX);
        pxPlot = Tools.createNonDecreasingFunction(pxPlot);
        vm.datatools.Tools.printMap(pxPlot);
        List<Point2D.Float> ret = new ArrayList<>();
        for (Map.Entry<Float, Float> point : pxPlot.entrySet()) {
            ret.add(new Point2D.Float(point.getKey(), point.getValue()));
        }
        return ret;
    }

    private Map<Double, Integer> learnMapping(float thresholdPcum, double iDimOfSketches, List<Point2D.Float> pxFunctionPoints) {
        Map<Double, Integer> ret = new TreeMap<>();
        MapperDistDToHammingThresholdT mapperDistDToHammingThresholdT = new MapperDistDToHammingThresholdT(maxDistForPX, iDimOfSketches, sketchLength, pxFunctionPoints, thresholdPcum);
        for (int t = 0; t <= sketchLength; t++) {
            double xMappedToT = mapperDistDToHammingThresholdT.findXMappedToT(t);
            ret.put(xMappedToT, t);
            if (xMappedToT == maxDistForPX) {
                LOG.log(Level.INFO, "Max dist achieved at the Hamming dist {0}", t);
                break;
            }
            if (t % 10 == 0) {
                LOG.log(Level.INFO, "Defining mapping. Done {0} Hamming dists out of {1}", new Object[]{t, sketchLength + 1});
            }
        }
        return ret;
    }

}
