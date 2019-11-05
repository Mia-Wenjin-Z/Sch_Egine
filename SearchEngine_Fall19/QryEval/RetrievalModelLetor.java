import java.util.Map;

public class RetrievalModelLetor extends RetrievalModel {
    double k1, b, k3;
    double mu, lambda;
    String queryFilePath;
    String trainingQrelsFile;
    String trainingQueryFile;
    String trainingFeatureVectorsFile;
    String featureDisable;
    String svmRankLearnPath;
    String svmRankClassifyPath;
    String svmRankModelFile;
    double svmRankParamC;
    String testingFeatureVectorsFile;
    String testingDocumentScores;
//    String trecEvalOutputPath;
//    String trecEvalOutputLength;


    public RetrievalModelLetor(Map<String, String> parameters) {
        //todo input checking!
        //Read BM25 parameter
        this.k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        this.b = Double.parseDouble(parameters.get("BM25:b"));
        this.k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        //todo to-delete or to change this assert
        assert k1 >= 0.0 && b >= 0.0 && b <= 1.0 && k3 >= 0;
        //Read Indri parameter
        this.mu = Double.parseDouble(parameters.get("Indri:mu"));
        this.lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        assert mu >= 0 && lambda >= 0 && lambda <= 1.0;
        //Read learning to rank parameter
        this.queryFilePath = parameters.get("queryFilePath");
        this.trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        this.trainingQueryFile = parameters.get("letor:trainingQueryFile");
        this.trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
        this.featureDisable = parameters.get("letor:featureDisable");
        this.svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        this.svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        this.svmRankModelFile = parameters.get("letor:svmRankModelFile");
        this.svmRankParamC = Double.parseDouble(parameters.get("letor:svmRankParamC"));
        this.testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        this.testingDocumentScores = parameters.get("letor:testingDocumentScores");
    }


    @Override
    public String defaultQrySopName() {
        return null;
    }
}
