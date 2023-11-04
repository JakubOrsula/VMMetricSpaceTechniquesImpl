package vm.simRel.impl.learn;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import vm.metricSpace.AbstractMetricSpace;
import vm.metricSpace.Dataset;
import vm.search.algorithm.SearchingAlgorithm;
import vm.search.algorithm.impl.SimRelSeqScanKNNCandSet;
import vm.simRel.impl.learn.storeLearnt.SimRelEuclidThresholdsTOmegaStorage;

/**
 *
 * @author Vlada
 */
public class ThresholdsTOmegaEvaluator {

    private static final Logger LOG = Logger.getLogger(ThresholdsTOmegaEvaluator.class.getName());

    private final int querySampleCount;
    private final int kPCA;

    public ThresholdsTOmegaEvaluator(int querySampleCount, int kPCA) {
        this.querySampleCount = querySampleCount;
        this.kPCA = kPCA;
    }

    public float[][] learnTOmegaThresholds(Dataset<float[]> pcaDataset, SimRelEuclidThresholdsTOmegaStorage simRelStorage, int dataSampleCount, int pcaLength, float... percentiles) {
        List<Object> pcaOfCandidates = pcaDataset.getSampleOfDataset(dataSampleCount);
        return learnTOmegaThresholds(pcaDataset, pcaOfCandidates, simRelStorage, dataSampleCount, pcaLength, percentiles);
    }

    public float[][] learnTOmegaThresholds(Dataset<float[]> pcaDataset, List<Object> pcaOfCandidates, SimRelEuclidThresholdsTOmegaStorage simRelStorage, int dataSampleCount, int pcaLength, float... percentiles) {
        List<Object> pcaQuerySamples = pcaDataset.getPivots(querySampleCount);
        AbstractMetricSpace<float[]> pcaDatasetMetricSpace = pcaDataset.getMetricSpace();

        SimRelEuclideanPCAForLearning simRelLearn = new SimRelEuclideanPCAForLearning(pcaLength);

        SearchingAlgorithm simRelAlg = new SimRelSeqScanKNNCandSet(simRelLearn, kPCA);

        for (int i = 0; i < pcaQuerySamples.size(); i++) {
            Object pcaQueryObj = pcaQuerySamples.get(i);
            simRelLearn.resetCounters(pcaLength);
            Object queryObjId = pcaDatasetMetricSpace.getIDOfMetricObject(pcaQueryObj);

            simRelAlg.candSetKnnSearch(pcaDataset.getMetricSpace(), pcaQueryObj, kPCA, pcaOfCandidates.iterator());
            LOG.log(Level.INFO, "Learning tresholds with the query obj {0}, i.e., qID {0}", new Object[]{i + 1, queryObjId});
        }

        float[][] ret = simRelLearn.getDiffWhenWrong(percentiles);
        simRelStorage.store(ret, pcaDataset.getDatasetName());
        return ret;
    }

//    public float[] learnTOmegaThresholds(Dataset<float[]> pcaDataset, SimRelEuclidThresholdsTOmegaStorage storage) {
//        List<Object> querySamples = pcaDataset.getPivots(querySampleCount);
//        AbstractMetricSpace<float[]> metricSpace = pcaDataset.getMetricSpace();
//        List<Object> sampleOfDataset = pcaDataset.getSampleOfDataset(dataSampleCount);
//        float[] vector = metricSpace.getDataOfMetricObject(sampleOfDataset.get(0));
//        int pcaLength = vector.length;
//        SimRelEuclideanPCAForLearning simRelLearn = new SimRelEuclideanPCAForLearning(pcaLength);
//        SearchingAlgorithm alg = new SimRelSeqScanKNNCandSet(simRelLearn, kPCA);
//
//        simRelLearn.resetLearning(pcaLength);
//        for (int i = 0; i < querySamples.size(); i++) {
//            Object queryObj = querySamples.get(i);
//            String qID = metricSpace.getIDOfMetricObject(queryObj).toString();
//            simRelLearn.resetCounters(pcaLength);
//            alg.candSetKnnSearch(pcaDataset.getMetricSpace(), queryObj, kPCA, sampleOfDataset.iterator());
//            LOG.log(Level.INFO, "Learning tresholds with the query obj {0}, i.e., qID {0}", new Object[]{i + 1, qID});
//        }
//        float[][] ret = simRelLearn.getDiffWhenWrong(PERCENTILES);
//        storage.store(ret, pcaDataset.getDatasetName());
//        return ret[0];
//    }
}
