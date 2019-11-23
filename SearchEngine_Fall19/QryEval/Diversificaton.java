import java.io.IOException;
import java.util.*;

public class Diversificaton {

    // <qid, <docid, {score, score1, score2, ...} >>
    private Map<String, Map<Integer, List<Double>>> rankingMap = new HashMap<>();
    // <qid, {qid1, qid2,...} >
    private Map<String, List<String>> intentMap = new HashMap<>();
    // <qid, {sum_score, sum_score1, sum_score2} >
    private Map<String, List<Double>> sumMap = new HashMap<>();


    private double lambda;
    private int maxInputRankingsLength;
    private int maxResultRankingLength;
    boolean needNormalization = false;
    String algorithm;

    public Diversificaton(Map<String, String> parameters) {

        this.lambda = Double.parseDouble(parameters.get("diversity:lambda"));
        this.maxInputRankingsLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        this.maxResultRankingLength = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
        this.algorithm = parameters.get("diversity:algorithm");
    }


    public void setIntentMap(Map<String, List<String>> intentMap) {
        this.intentMap = intentMap;
    }


    /**
     * <qid, <docid, {score, score1, score2, ...} > >
     *
     * @param initialResultsMapforDiversification
     */
    public void setRankingMap(Map<String, ScoreList> initialResultsMapforDiversification) throws IOException {


        // set qid
        for (Map.Entry<String, ScoreList> entry : initialResultsMapforDiversification.entrySet()) {//for each qid

            String qid = entry.getKey();

            if (!qid.contains(".")) {//query

                ScoreList result = entry.getValue();
                int resultSize = Math.min(maxInputRankingsLength, result.size());
                result.sort();
                result.truncate(resultSize);

                int queryIntentSize = intentMap.get(qid).size();

                // Sum of scores of query(intent) across docs, per query basis
                List<Double> sumScores = new ArrayList<>(queryIntentSize);//per query
                //initialize
                for (int i = 0; i < queryIntentSize + 1; i++) {
                    sumScores.add(i, 0.0);
                }

                Map<Integer, List<Double>> docScoreMapForQuery = getDocScoreMapForQuery(result, sumScores);

                rankingMap.put(qid, docScoreMapForQuery);
                sumMap.put(qid, sumScores);

            } else {
                //query intent,  dealt with later
            }
        }


        //set query intent
        for (Map.Entry<String, ScoreList> entry : initialResultsMapforDiversification.entrySet()) {//for each qid_intent

            String qid = entry.getKey();


            if (qid.contains(".")) {//query intent


                String parentQid = qid.split("\\.")[0];
                ScoreList result = entry.getValue();
                int resultSize = Math.min(maxInputRankingsLength, result.size());
                result.sort();
                result.truncate(resultSize);


                // Sum of scores of query(intent) across docs, per query basis
                List<Double> sumScores = sumMap.get(parentQid);//per query
                setDocScoreMapForQueryIntent(qid, result, sumScores);

            } else {
                //query, omitted since already dealt with
            }
        }
        if (needNormalization) {
            normalizeScores();
        }
    }

    private void normalizeScores() throws IOException {
        for (Map.Entry<String, Map<Integer, List<Double>>> entry : rankingMap.entrySet()) {//for each qid
            String qid = entry.getKey();
            List<Double> sumScoreList = sumMap.get(qid);
            Map<Integer, List<Double>> docScoreMap = rankingMap.get(qid);

//            System.out.println("qid: " + qid + " sumOfScores: " + Arrays.asList(sumScoreList));

            double maxSum = Collections.max(sumScoreList);
//            System.out.println("dominator: " + maxSum);
//
//            System.out.println("doc#" + docScoreMap.size());

            for (Map.Entry<Integer, List<Double>> doc : docScoreMap.entrySet()) {//for each doc

                int docId = doc.getKey();

                List<Double> scoreList = doc.getValue();

                System.out.print(Idx.getExternalDocid(docId));
//                System.out.println(Arrays.asList(scoreList));
                for (int i = 0; i < scoreList.size(); i++) {
                    scoreList.set(i, scoreList.get(i) / maxSum);
                }
//                System.out.println(Arrays.asList(scoreList));

                docScoreMap.put(docId, scoreList);
            }
            rankingMap.put(qid, docScoreMap);
        }
    }


    /**
     * <docid, {score0, score1, score2}> for qid
     *
     * @param result    scorelist for qid
     * @param sumScores sumofScore list to be updated
     * @return
     */
    private Map<Integer, List<Double>> getDocScoreMapForQuery(ScoreList result, List<Double> sumScores) {

        //per doc:< internalDocid, {score0, score1, ...} >
        Map<Integer, List<Double>> docScores = new HashMap<>();

        //Loop results for this query
        for (int i = 0; i < result.size(); i++) {// for each doc of qid

            int docId = result.getDocid(i);//default as internal doc id
            double score = result.getDocidScore(i);

            if (score > 1.0) {
                needNormalization = true;
            }

            //scorelist of each query() for the current doc
            int queryIntentSize = sumScores.size() - 1;
            List<Double> scoreList = new ArrayList<>(queryIntentSize + 1);
            for (int j = 0; j < queryIntentSize + 1; j++) {
                scoreList.add(0.0);
            }

            scoreList.set(0, score);//add query score to scoreList for current doc
            sumScores.set(0, sumScores.get(0) + score);// update sum of score for this qid
            docScores.put(docId, scoreList);
        }
        return docScores;
    }

    private void setDocScoreMapForQueryIntent(String qid, ScoreList result,
                                              List<Double> sumScores) {

        //per doc:< internalDocid, {score0, score1, ...} >
        String parentQid = qid.split("\\.")[0];
        int intent_index = Integer.parseInt(qid.split("\\.")[1]);

        //Loop results for this query
        for (int i = 0; i < result.size(); i++) {// for each doc of qid_intent

            int docId = result.getDocid(i);//default as internal doc id
            double score = result.getDocidScore(i);

            //scorelist of the query intent for the current doc
            if (!rankingMap.get(parentQid).containsKey(docId)) {
                continue;
            }
            List<Double> scoreList = rankingMap.get(parentQid).get(docId);

            //add query intent scores to scoreList for current doc
            scoreList.set(intent_index, score);
            sumScores.set(intent_index, sumScores.get(intent_index) + score);//update sum of score for this intent
        }
    }


    public ScoreList getRetrievalResult(String diversityAlgorithm, String qid) throws Exception {

        ScoreList result;
        if (diversityAlgorithm.equals("xquad")) {

            result = this.xQuAD(qid);
        } else if (diversityAlgorithm.equals("pm2")) {

            result = this.PM2(qid);
        } else {
            throw new Exception("Unsupported diversity algorithm!");
        }

        result.sort();
        int resultLength = Math.min(result.size(), maxResultRankingLength);
        result.truncate(resultLength);

        return result;
    }

    private ScoreList xQuAD(String qid) {

        ScoreList result = new ScoreList();
        int docNum = rankingMap.get(qid).size();
        int intentSize = intentMap.get(qid).size();

        // p(qi/q)
        double intentWeight = 1.0 / intentSize;


        Map<Integer, List<Double>> docToQueryScoreMap = rankingMap.get(qid);

        // S : map of doc and its score list that has been picked
        Map<Integer, List<Double>> xQuADScoreMap = new HashMap<>();

        while (result.size() < docNum) {//loop until all docs are exhausted

            double maxScore = -1;
            int maxScoreDocId = -1;
            List<Double> maxScoreList = null;

            for (Map.Entry<Integer, List<Double>> entry : docToQueryScoreMap.entrySet()) {//for each doc not re-ranked
                int docId = entry.getKey();
                List<Double> scoreList = entry.getValue();


                double totalIntentScore = 0;
                // Sum( p (qi | q) * p (d | qi ) )* Coverage
                for (int i = 1; i < scoreList.size(); i++) {  // for each intent

                    //Score of d for intent qi
                    double intentScore = scoreList.get(i);

                    //Coverage: How well S already covers intent qi
                    double coverage = 1;

                    // loop S: xQuADScoreMap
                    for (Integer rankedDocid : xQuADScoreMap.keySet()) {
                        coverage *= (1 - xQuADScoreMap.get(rankedDocid).get(i));
                    }

                    totalIntentScore += intentWeight * intentScore * coverage;
                }

                double queryScore = scoreList.get(0);
                double xQuADscore = (1 - lambda) * queryScore + lambda * totalIntentScore;

                //update next doc to be picked
                if (maxScore < xQuADscore) {
                    maxScoreDocId = docId;
                    maxScore = xQuADscore;
                    maxScoreList = scoreList;
                }

            }
            xQuADScoreMap.put(maxScoreDocId, maxScoreList);// pick doc with max xQuADscore into S
            docToQueryScoreMap.remove(maxScoreDocId);// remove the doc from candidate map
            result.add(maxScoreDocId, maxScore);// add result to scorelist
        }
        return result;
    }

    private ScoreList PM2(String qid) throws IOException {

        ScoreList result = new ScoreList();
        Map<Integer, List<Double>> docToQueryScoreMap = rankingMap.get(qid);
        int docNum = rankingMap.get(qid).size();
        int intentSize = intentMap.get(qid).size();


        // p(qi/q)
        double intentWeight = 1.0 / intentSize;

        // votes
        double vi = intentWeight * this.maxResultRankingLength;

        // quotient scores qi i.e. priority
        double[] quotient = new double[intentSize];

        // slots, i.e. coverage
        double[] slots = new double[intentSize];

        // score list that has been picked last time, index at 0 is query score, omitted
        List<Double> maxScoreList = null;

        while (result.size() < docNum) {//loop until all docs are exhausted
            double maxScore = -1;
            int maxScoreDocId = -1;
            int maxQuotientIndex = -1;
            double maxQuotient = -Double.MAX_VALUE;


            //Update coverage's denominator
            double sumScore = 0;
            if (result.size() > 0) {
                for (int i = 1; i < maxScoreList.size(); i++) {
                    sumScore += maxScoreList.get(i);
                }
            }

            //Update coverage, quotient and quotient index
            for (int i = 0; i < intentSize; i++) {

                // update s i.e. coverage
                if (result.size() > 0 && sumScore != 0) {
                    slots[i] += maxScoreList.get(i + 1) / sumScore;
                } else {
                    slots[i] = 0;
                }
//                System.out.print("Slots: ");
//                System.out.println(Arrays.toString(slots));

                //update quotient
                quotient[i] = vi / (2 * slots[i] + 1);

                // select intent
                if (quotient[i] > maxQuotient) {
                    maxQuotient = quotient[i];
                    maxQuotientIndex = i;
                }
            }

//            System.out.println("hosen" + maxQuotientIndex);

            for (Map.Entry<Integer, List<Double>> entry : docToQueryScoreMap.entrySet()) {//for each doc not re-ranked
                int docId = entry.getKey();
//                System.out.println("current doc" + Idx.getExternalDocid(docId));
                List<Double> scoreList = entry.getValue();
                // Covers qi
                double curIntentScore = 0;

                double queryScore_curIntent = scoreList.get(maxQuotientIndex + 1);
                curIntentScore = lambda * quotient[maxQuotientIndex] * queryScore_curIntent;

                // Covers other intents
                double otherIntentScore = 0;

                for (int i = 0; i < quotient.length; i++) {
                    if (i == maxQuotientIndex) {
                        continue;
                    }
                    double queryScore_otherIntent = scoreList.get(i + 1);
                    otherIntentScore += quotient[i] * queryScore_otherIntent;
                }

                otherIntentScore *= (1 - lambda);
                double score = curIntentScore + otherIntentScore;

                if (score > maxScore) {
                    maxScore = score;
//                    System.out.println("maxScore updated: " + maxScore);
                    maxScoreDocId = docId;
                    maxScoreList = scoreList;
                }
            }

//            System.out.print("Chosen Score list: ");
//            System.out.println(Arrays.asList(maxScoreList));
            docToQueryScoreMap.remove(maxScoreDocId);
            result.add(maxScoreDocId, maxScore);
//            System.out.println("Added score of PM2: " + Idx.getExternalDocid(maxScoreDocId) + " "  + maxScore);

//            if(maxScore == 0){
//                break;
//            }
        }
        return result;
    }
}
