/**
 * This class is used to generate feature vector for <query, doc> pairs
 * And write result to corresponding files. i.e. trainingFeatureVectorsFile or testingFeatureVectorsFile
 */

import java.io.*;
import java.util.*;

public class FeatureVector {

    private RetrievalModelLetor model;
    private Set<String> disabledFeatures;
    private static final int FEATURE_NUM = 19;
    private static final int DOC_NUM = 100;
    private static double[] maxScoreOfFeatures = new double[FEATURE_NUM];
    private static double[] minScoreOfFeatures = new double[FEATURE_NUM];


    //Map (external_doc_id, feature vector) given the query
    //Map (query id, external_doc_id, rev_score)

    public FeatureVector(RetrievalModelLetor model) {
        this.model = model;
        if (model.featureDisable != null) {
            setDisabledFeature();
            initializeScoreOfFeatures();
        }
    }

    private void setDisabledFeature() {
        for (String disabledFeature : model.featureDisable.split(",")) {
            disabledFeatures = new HashSet<>();
            disabledFeatures.add(disabledFeature);
        }
    }

    private void initializeScoreOfFeatures() {
        for (int i = 1; i < FEATURE_NUM; i++) {
            // only use 1 - 18, position 0 is not used
            this.maxScoreOfFeatures[i] = -Double.MAX_VALUE;
            this.minScoreOfFeatures[i] = Double.MAX_VALUE;
        }
    }

    private void updateMaxandMin(int index, double score) {
        this.maxScoreOfFeatures[index] = this.maxScoreOfFeatures[index] < score ? score : this.maxScoreOfFeatures[index];
        this.minScoreOfFeatures[index] = this.minScoreOfFeatures[index] < score ? this.minScoreOfFeatures[index] : score;
    }


    /**
     * calculate/write feature vectors for the training dataset
     */
    public void writeTrainingFeatureVector() {
        List<Map.Entry<Integer, String>> queryList = getQueryList(model.trainingQueryFile);
        Map<Integer, Map<String, Integer>> relevanceMap = getRelevanceMap(model.trainingQrelsFile);
        Map<String, Map<Integer, Double>> featureVectorMap = new HashMap<>();
        //run a while loop for each q here
        BufferedWriter output = null;

        try {
            output = new BufferedWriter(new FileWriter(model.trainingFeatureVectorsFile));

            for (Map.Entry<Integer, String> queryEntry : queryList) {//for each qid
                Integer qid = queryEntry.getKey();
                Map<String, Integer> docRelevanceMap = relevanceMap.get(qid);

                for (Map.Entry<String, Integer> docEntry : docRelevanceMap.entrySet()) {//for each doc
                    String externalDocId = docEntry.getKey();
                    int docid = Idx.getInternalDocid(externalDocId);
                    if (docid == -1) {
                        continue;
                    }
                    //Feature vector for the current doc
                    Map<Integer, Double> featureVector = new HashMap<>();
                    //Begin calculating features f1-f18
                    String query = queryEntry.getValue();
                    List<String> terms = tokenize(query);
                    setFeatureVectorScore(docid, terms, featureVector);
                    //Update to feature vector Map
                    featureVectorMap.putIfAbsent(externalDocId, featureVector);
                }
                //normalization
                normalizeVector(featureVectorMap);
                //write result to file - write on a per query basis
                writeTrainingFeatureVectorToFiles(qid, relevanceMap, featureVectorMap, output);
                initializeScoreOfFeatures();//todo to-check reset norm score
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void writeTestingFeatureVector() {
        List<Map.Entry<Integer, String>> queryList = getQueryList(model.queryFilePath);
        //No relevance map. all relevance score set to zero for testing data
        Map<String, Map<Integer, Double>> featureVectorMap = new HashMap<>();
        //run a while loop for each q here
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(model.testingFeatureVectorsFile));

            RetrievalModel BM25model = new RetrievalModelBM25(model.k1,
                    model.b, model.k3);

            for (Map.Entry<Integer, String> queryEntry : queryList) {//for each qid
                Integer qid = queryEntry.getKey();
                //User BM25 TO GET relevant top 100 doc
                String query = queryEntry.getValue();
                ScoreList result = QryEval.processQuery(query, BM25model);
                int size = Math.min(result.size(), this.DOC_NUM);

                for (int i = 0; i < size; i++) {//for each doc
                    int docid = result.getDocid(i);
                    String externalDocId = Idx.getExternalDocid(docid);
                    if (docid == -1) {
                        continue;
                    }
                    //Feature vector for the current doc
                    Map<Integer, Double> featureVector = new HashMap<>();
                    //Begin calculating features f1-f18
                    List<String> terms = tokenize(query);
                    setFeatureVectorScore(docid, terms, featureVector);
                    //Update to feature vector Map
                    featureVectorMap.putIfAbsent(externalDocId, featureVector);
                }
                //normalization
                normalizeVector(featureVectorMap);
                //write result to file - write on a per query basis
                writeTrainingFeatureVectorToFiles(qid, featureVectorMap, output);
                initializeScoreOfFeatures();//todo to-check reset norm score
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Calculating features f1-f18
     *
     * @param docid
     * @param terms
     * @param featureVector
     * @throws IOException
     */
    private void setFeatureVectorScore(int docid, List<String> terms, Map<Integer, Double> featureVector) throws IOException {
        //f1 spam
        if (!this.disabledFeatures.contains("1")) {
            int spamScore = Integer.parseInt(Idx.getAttribute("spamScore", docid));
            featureVector.put(1, spamScore * 1.0);
            updateMaxandMin(1, spamScore * 1.0);
        }


        String rawUrl = Idx.getAttribute("rawUrl", docid);
        //f2: Url depth for d(number of '/' in the rawUrl field).
        if (!this.disabledFeatures.contains("2")) {
            int depth = 0;
            for (int i = 0; i < rawUrl.length(); i++) {
                if (rawUrl.charAt(i) == '/') {
                    depth++;
                }
            }
            featureVector.put(2, depth * 1.0);
            updateMaxandMin(2, depth * 1.0);
        }
        //f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
        if (!this.disabledFeatures.contains("3")) {
            double containsWiki = rawUrl.contains("wikipedia.org") ? 1 : 0;
            featureVector.put(3, containsWiki);
            updateMaxandMin(3, containsWiki);
        }

        // f4: PageRank score for d (read from index).
        if (!this.disabledFeatures.contains("4")) {
            double prScore = Double.parseDouble(Idx.getAttribute("PageRank", docid));
            featureVector.put(4, prScore);
            updateMaxandMin(4, prScore);
        }

        String[] fields = {"body", "title", "url", "inlink"};
        for (int i = 0; i < fields.length; i++) {
            QrySopScore sop = new QrySopScore();
            String field = fields[i];
            // f5,8,11,14: BM25 score using fields
            if (!this.disabledFeatures.contains(String.valueOf(5 + 3 * i))) {
                double BM25Score = sop.getScoreBM25(docid, field, terms, model);
                featureVector.put(5 + 3 * i, BM25Score);
                updateMaxandMin(5 + 3 * i, BM25Score);
            }
            // f6,9,12,15: Indri score using fields
            if (!this.disabledFeatures.contains(String.valueOf(6 + 3 * i))) {
                double IndriScore = sop.getScoreIndri(docid, field, terms, model);
                featureVector.put(6 + 3 * i, IndriScore);
                updateMaxandMin(6 + 3 * i, IndriScore);
            }

            // f7,10,13,16: term overlap
            //Hint: Term overlap is defined as the percentage of query terms that match the document field.
            if (!this.disabledFeatures.contains(String.valueOf(7 + 3 * i))) {
                double overlap = sop.getTermOverlapPct(docid, field, terms);
                featureVector.put(7 + 3 * i, overlap);
                updateMaxandMin(7 + 3 * i, overlap);
            }
        }

        // f17: A custom feature - use your imagination.
        // Combined overlap: 1 - multiplication (1 - overlap[field])
        if (!this.disabledFeatures.contains("17")) {
            QrySopScore sop = new QrySopScore();
            double overlapBody = 1 - sop.getTermOverlapPct(docid, "body", terms);
            double overlapTitle = 1 - sop.getTermOverlapPct(docid, "title", terms);
            double overlapUrl = 1 - sop.getTermOverlapPct(docid, "url", terms);
            double overlapInlink = 1 - sop.getTermOverlapPct(docid, "inlink", terms);
            double overlap = 1 - overlapBody * overlapTitle * overlapUrl * overlapInlink;
            featureVector.put(17, overlap);
            updateMaxandMin(17, overlap);
        }

        // f18: A custom feature - use your imagination.
        // values more centalized Indri Score across different fields
        if (!this.disabledFeatures.contains("18")) {
            double[] IndriScore = new double[]{featureVector.get(7), featureVector.get(10),
                    featureVector.get(13), featureVector.get(16)};
            double centerity = 1 - calculateSD(IndriScore);
            featureVector.put(18, centerity);
            updateMaxandMin(18, centerity);
        }

    }

    private static double calculateSD(double numArray[]) {
        double sum = 0.0, standardDeviation = 0.0;
        int length = numArray.length;
        for (double num : numArray) {
            sum += num;
        }
        double mean = sum / length;
        for (double num : numArray) {
            standardDeviation += Math.pow(num - mean, 2);
        }
        return Math.sqrt(standardDeviation / length);
    }

    /**
     * Get (qid, query) entry list sorted by qid for feature vector generation
     *
     * @param queryFilePath
     * @return
     */
    private List<Map.Entry<Integer, String>> getQueryList(String queryFilePath) {
        Map<Integer, String> queryMap = getQueryMap(queryFilePath);
        List<Map.Entry<Integer, String>> list = sortQueryMap(queryMap);
        return list;
    }

    /**
     * Get (qid, query) map
     *
     * @param queryFilePath
     * @return
     */
    private Map<Integer, String> getQueryMap(String queryFilePath) {
        Map<Integer, String> queryMap = new HashMap<>();// (qid, query)
        BufferedReader input = null;
        try {
            input = new BufferedReader(new FileReader(queryFilePath));
            String line;

            while ((line = input.readLine()) != null) {
                String[] lineArray = line.split(":", 2);

                if (lineArray.length < 2) {
                    throw new Exception("query incomplete!");
                }
                Integer qid = Integer.parseInt(lineArray[0]);
                String query = lineArray[1];
                queryMap.put(qid, query);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return queryMap;
    }

    //Sort query map into a list , ASCE by qid
    private static List<Map.Entry<Integer, String>> sortQueryMap(Map<Integer, String> queryMap) {
        List<Map.Entry<Integer, String>> list = new LinkedList<>(queryMap.entrySet());
        //Sort by ascending order
        Collections.sort(list, Comparator.comparing(Map.Entry::getKey));
        return list;
    }

    /**
     * tokenize raw query term - todo to check in debug
     *
     * @param query
     * @return
     * @throws IOException
     */
    private List<String> tokenize(String query) throws IOException {
        List<String> terms = new ArrayList<String>();
        String[] rawQueryTerms = query.split("\\s+");
        for (String rawQueryTerm : rawQueryTerms) {
            String[] tokens = QryParser.tokenizeString(rawQueryTerm);
            for (String token : tokens) {
                terms.add(token);
            }
        }
        return terms;
    }

    /**
     * Get <qid, <ex_doc_id, rel_score> > nested map
     *
     * @param queryFilePath
     * @return
     */
    private Map<Integer, Map<String, Integer>> getRelevanceMap(String queryFilePath) {
        Map<Integer, Map<String, Integer>> relevanceMap = new HashMap<>();
        BufferedReader input = null;
        try {
            input = new BufferedReader(new FileReader(queryFilePath));
            String line;

            while ((line = input.readLine()) != null) {
                // example line: 12 0 clueweb09-en0002-76-33427 0
                String[] lineArray = line.split("\\s+");

                if (lineArray.length < 4) {
                    throw new Exception("relevance label line incomplete!");
                }
                Integer qid = Integer.parseInt(lineArray[0]);
                String externalDocId = lineArray[2];
                Integer relevanceScore = Integer.parseInt(lineArray[3]);
                relevanceMap.putIfAbsent(qid, new HashMap<>());
                relevanceMap.get(qid).put(externalDocId, relevanceScore);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return relevanceMap;

    }


    /**
     * Write feature vectors to file for training data
     * // example:
     * // 2 qid:1 1:1 2:1 3:0 4:0.2 5:0 # clueweb09-en0000-48-24794
     * // 2 qid:1 1:0 2:0 3:1 4:0.1 5:1 # clueweb09-en0011-93-16495
     *
     * @param qid
     * @param relevanceMap
     * @param featureVectorMap
     * @param output
     * @throws IOException
     */
    private void writeTrainingFeatureVectorToFiles(int qid, Map<Integer, Map<String, Integer>> relevanceMap,
                                                   Map<String, Map<Integer, Double>> featureVectorMap,
                                                   BufferedWriter output) throws IOException {

        StringBuilder outputLine = new StringBuilder();
        Map<String, Integer> docIdToRelScoreMap = relevanceMap.get(qid);

        for (Map.Entry<String, Map<Integer, Double>> entry : featureVectorMap.entrySet()) {

            String externalDocId = entry.getKey();
            int relevanceScore = docIdToRelScoreMap.containsKey(externalDocId) ?
                    docIdToRelScoreMap.get(externalDocId) : 0;
            outputLine.append(String.format("%d\tqid:%d", relevanceScore, qid));//2 qid:1

            Map<Integer, Double> featureVector = featureVectorMap.get(externalDocId);
            for (int i = 1; i < FEATURE_NUM; i++) {//1:1 2:1 3:0 4:0.2 5:0

                double score = featureVector.get(i);
                outputLine.append(String.format("\t%d:%.14f", i, score));
            }
            outputLine.append(String.format("\t#\t%s\n", externalDocId));//# clueweb09-en0000-48-24794
            output.write(outputLine.toString());
        }
    }

    /**
     * Write feature vectors to file for testing data
     * // example:
     * // 0 qid:1 1:1 2:1 3:0 4:0.2 5:0 # clueweb09-en0000-48-24794
     * // 0 qid:1 1:0 2:0 3:1 4:0.1 5:1 # clueweb09-en0011-93-16495
     *
     * @param qid
     * @param
     * @param featureVectorMap
     * @param output
     * @throws IOException
     */
    private void writeTrainingFeatureVectorToFiles(int qid,
                                                   Map<String, Map<Integer, Double>> featureVectorMap,
                                                   BufferedWriter output) throws IOException {

        StringBuilder outputLine = new StringBuilder();

        for (Map.Entry<String, Map<Integer, Double>> entry : featureVectorMap.entrySet()) {

            String externalDocId = entry.getKey();
            int relevanceScore = 0;
            outputLine.append(String.format("%d\tqid:%d", relevanceScore, qid));//0 qid:1

            Map<Integer, Double> featureVector = featureVectorMap.get(externalDocId);
            for (int i = 1; i < FEATURE_NUM; i++) {//1:1 2:1 3:0 4:0.2 5:0

                double score = featureVector.get(i);
                outputLine.append(String.format("\t%d:%.14f", i, score));
            }
            outputLine.append(String.format("\t#\t%s\n", externalDocId));//# clueweb09-en0000-48-24794
            output.write(outputLine.toString());
        }
    }


    /**
     * normalize feature vectors for a given query q
     * For example, the normalized value for feature f5 (q3, d21) is:
     * (featureValue_f5 (q3, d21) - minValue_f5 (q3)) / (maxValue_f5 (q3) - minValue_f5 (q3)).
     *
     * @param featureVectorMap
     */
    private void normalizeVector(Map<String, Map<Integer, Double>> featureVectorMap) {

        for (Map<Integer, Double> featureVector : featureVectorMap.values()) {
            for (int i = 1; i < FEATURE_NUM; i++) {
                double max = this.maxScoreOfFeatures[i];
                double min = this.minScoreOfFeatures[i];
                double score = featureVector.containsKey(i) ? featureVector.get(i) : Double.MIN_VALUE;
                if (score != Double.MIN_VALUE) {
                    score = (max == min) ? 0 : (score - min) / (max - min);
                }
                featureVector.put(i, score);
                //todo to check if valid: changing while iterating
            }
        }
    }
}
