/*
 *  Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.4.
 *
 *  Compatible with Lucene 8.1.1.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.index.*;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));
        RetrievalModel model;
        if (parameters.containsKey("retrievalAlgorithm")) {
            model = initializeRetrievalModel(parameters);
        } else {// for query diversification with existing initial ranking, model is not needed.
            model = new RetrievalModel() {
                @Override
                public String defaultQrySopName() {
                    return "#OR";
                }
            };
        }

        if (model instanceof RetrievalModelLetor) {

            //Get & write feature vectors of training data
            FeatureVector featureVector = new FeatureVector((RetrievalModelLetor) model);
            featureVector.writeTrainingFeatureVector();
            System.out.println("training feature vector written.");

            //Learning: Train SVM using training data
            trainSVM((RetrievalModelLetor) model);
            System.out.println("SVM model trained.");

            //Get & write feature vectors of testing data
            Map<Integer, List<String>> docSequence = featureVector.writeTestingFeatureVector();
            System.out.println("testing feature vector written.");

            //Re-rank: Apply SVM on testing data's feature vectors
            applySVM((RetrievalModelLetor) model);
            System.out.println("SVM model applied");

            //Write re-rank results to TrecEvalOutput -> write a static method for this
            writeLetorScore((RetrievalModelLetor) model, docSequence, parameters);
        } else {

            //  Perform experiments.

            processQueryFile(parameters, model);
        }

        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }


    private static void trainSVM(RetrievalModelLetor model) throws Exception {
        Process cmdProc = Runtime.getRuntime().exec(new String[]{model.svmRankLearnPath, "-c",
                String.valueOf(model.svmRankParamC), model.trainingFeatureVectorsFile, model.svmRankModelFile});
        consume(cmdProc);
    }

    private static void applySVM(RetrievalModelLetor model) throws Exception {
        Process cmdProc = Runtime.getRuntime().exec(new String[]{model.svmRankClassifyPath,
                model.testingFeatureVectorsFile, model.svmRankModelFile, model.testingDocumentScores});
        consume(cmdProc);
    }


    private static void writeLetorScore(RetrievalModelLetor model, Map<Integer, List<String>> docSequence,
                                        Map<String, String> parameters) {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(parameters.get("trecEvalOutputPath")));
            Deque<Double> SVMScoreQueue = readSVMScores(model.testingDocumentScores);

            for (Map.Entry<Integer, List<String>> queryEntry : docSequence.entrySet()) {//for each qid

                int qid = queryEntry.getKey();
                List<String> externalDocIds = queryEntry.getValue();
                ScoreList result = new ScoreList();

                for (int i = 0; i < externalDocIds.size(); i++) {// for each doc

                    if (!SVMScoreQueue.isEmpty()) {

                        result.add(Idx.getInternalDocid(externalDocIds.get(i)), SVMScoreQueue.pollFirst());
                    }
                }

                result.sort();
                if (result != null) {

                    StringBuilder outputStr = formatResults(String.valueOf(qid), result, parameters);
                    //printResults("Learning to rank", outputStr);
                    output.write(outputStr.toString());
                }
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

    static Deque<Double> readSVMScores(String testingDocumentScores) throws IOException {
        BufferedReader input = null;
        Deque<Double> SVMScoreQueue = new LinkedList<>();

        try {
            String docScore = null;
            input = new BufferedReader(new FileReader(testingDocumentScores));

            while ((docScore = input.readLine()) != null) {

                SVMScoreQueue.offer(Double.parseDouble(docScore.trim()));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();

        }
        return SVMScoreQueue;
    }


    private static void consume(Process cmdProc) throws Exception {
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @param parameters All of the parameters contained in the parameter file
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {

            model = new RetrievalModelUnrankedBoolean();

            //  If this retrieval model had parameters, they would be
            //  initialized here.

        }

        //  STUDENTS::  Add new retrieval models here.
        else if (modelString.equals("rankedboolean")) {

            model = new RetrievalModelRankedBoolean();
            //  this retrieval model had parameters, they would be
            //  initialized here.

        } else if (modelString.equals(("bm25"))) {
            String k1Str = parameters.get("BM25:k_1");
            String k3Str = parameters.get("BM25:k_3");
            String bStr = parameters.get("BM25:b");
            double[] BM25Parameters = getBM25Parameters(k1Str, k3Str, bStr);
            model = new RetrievalModelBM25(BM25Parameters[0],
                    BM25Parameters[1], BM25Parameters[2]);
        } else if (modelString.equals("indri")) {
            String muStr = parameters.get("Indri:mu");
            String lambdaStr = parameters.get("Indri:lambda");
            try {
                int mu = Integer.parseInt(muStr);
                double lambda = Double.parseDouble(lambdaStr);
                if (mu >= 0 && lambda >= 0.0 && lambda <= 1.0) {
                    model = new RetrievalModelIndri(mu, lambda);
                } else {
                    throw new IllegalArgumentException("Illegal Indri parameter value!");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Illegal Indri parameters!");
            }
        } else if (modelString.equals("letor")) {
            model = new RetrievalModelLetor(parameters);

        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * BM25:k_1=                Values are real numbers >= 0.0.
     * BM25:b=                    Values are real numbers between 0.0 and 1.0.
     * BM25:k_3=                Values are real numbers >= 0.0.
     * Indri:mu=                  Values are integers >= 0.
     * Indri:lambda=           Values are real numbers between 0.0 and 1.0.
     */

    private static double[] getBM25Parameters(String k1Str, String bStr, String k3Str) {
        try {
            double k1 = Double.parseDouble(k1Str);
            double b = Double.parseDouble(bStr);
            double k3 = Double.parseDouble(k3Str);
            if (k1 >= 0.0 && k3 >= 0.0 && b >= 0.0 && b <= 1.0) {
                return new double[]{k1, b, k3};
            } else {
                throw new IllegalArgumentException("Illegal BM25 parameter value!");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Illegal BM25 parameters!");
        }
    }


    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc, List<Double> total) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");

        total.set(0, total.get(0) + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)));
    }

    /**
     * Process one query.
     *
     * @param qryString A string that contains a query.
     * @param model     The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qryString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qryString = defaultOp + "(" + qryString + ")";
        Qry q = QryParser.getQuery(qryString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList results = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);// get inverted list info

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    results.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            results.sort();
            return results;
        } else
            return null;
    }

    /**
     * Process the query file.
     *
     * @param
     * @param model A retrieval model that will guide matching and scoring
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(Map<String, String> parameters,
                                 RetrievalModel model)
            throws Exception {

        BufferedReader input = null;
        BufferedWriter output = null;
        BufferedWriter queryExpansionOutput = null;

        String queryFilePath = parameters.get("queryFilePath");
        String trecEvalOutputPath = parameters.get("trecEvalOutputPath");

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));
            output = new BufferedWriter(new FileWriter(trecEvalOutputPath));


            //The following for pre-processing for 1. query expansion 2. query diversification


            //If initial doc ranking file (i.e. retrieval results) for query expansion exists
            //Initialize the (scoreList) initialResults using the file
            Map<String, ScoreList> initialResultsMapforExpansion = new HashMap<>();
            if (hasExpansionInitialRankingFile(parameters)) {
                initialResultsMapforExpansion = processInitialRankingFile(parameters.get("fbInitialRankingFile"));
            }
            if (needExpansion(parameters)) {
                queryExpansionOutput = new BufferedWriter(new FileWriter(parameters.get("fbExpansionQueryFile")));
            }

            //If initial doc ranking file (i.e. retrieval results) for diversification exists
            //Initialize the (scoreList) initialResults using the file

            Diversificaton diversificaton = null;
            Map<String, ScoreList> initialResultsMapforDiversification = null;
            Map<String, List<String>> intentMap = null; // <qid, list <qid> >
            Map<String, List<String>> intentQueryMap = null; // <qid, list <qid:query> >

            if (needDiversification(parameters)) {

                if (hasDiversificationInitialRankingFile(parameters)) {

                    initialResultsMapforDiversification = processInitialRankingFile(parameters.get("diversity:initialRankingFile"));
                    intentMap = getIntentsFromInitialRankingFile(parameters.get("diversity:initialRankingFile"));
                } else {

                    List<Map<String, List<String>>> intentList = getIntentQueryFromIntentFile(parameters.get("diversity:intentsFile"));
                    intentMap = intentList.get(0);
                    intentQueryMap = intentList.get(1);
                    initialResultsMapforDiversification = new HashMap<>();

                    int maxInputRankingsLength = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
                    setInitialResultsMapforDiversification(parameters, model, queryFilePath, initialResultsMapforDiversification, intentQueryMap, maxInputRankingsLength);
                }


                diversificaton = new Diversificaton(parameters);


                diversificaton.setIntentMap(intentMap);
                diversificaton.setRankingMap(initialResultsMapforDiversification);
            }


            //  Each pass of the loop processes one query.

            //to-delete: calculate avg memory
            List<Double> totalMem = new ArrayList<>();
            totalMem.add(0, 0.0);
            int loopTime = 0;

            while ((qLine = input.readLine()) != null) {

                printMemoryUsage(false, totalMem);
                loopTime += 1;
                System.out.println("Query " + qLine);
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }
                String qid = pair[0];
                String query = pair[1];
                ScoreList initialResults;


                if (!needExpansion(parameters) && !needDiversification(parameters)) {

                    initialResults = processQuery(query, model);
                    StringBuilder outputStr = formatResults(qid, initialResults, parameters);
                    output.write(outputStr.toString());


                } else if (needExpansion(parameters)) {// Perform query expansion

                    if (hasExpansionInitialRankingFile(parameters)) {
                        //read a document ranking in trec_eval input format from the fbInitialRankingFile;
                        if (!initialResultsMapforExpansion.containsKey(qid)) {
                            throw new Exception(String.format("No document ranking results for query: %s.", qid));
                        }
                        initialResults = initialResultsMapforExpansion.get(qid);

                    } else {
                        initialResults = processQuery(query, model);
                    }


                    String expandedQuery = getExpandedQuery(initialResults, parameters);
                    System.out.printf("%s: %s\n", qid, expandedQuery);
                    queryExpansionOutput.write(String.format("%s: %s\n", qid, expandedQuery));
                    double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));

                    //Wrap query
                    String defaultOp = model.defaultQrySopName();
                    query = defaultOp + "(" + query + ")";
                    String combinedQuery = getCombinedQuery(query, expandedQuery, fbOrigWeight);
                    System.out.println("****Combined Query: " + combinedQuery);

                    //Use the combined query to retrieve documents;
                    ScoreList results = processQuery(combinedQuery, model);
                    StringBuilder outputStr = formatResults(qid, results, parameters);
                    //System.out.println(outputStr);
                    output.write(outputStr.toString());
                } else {

                    String diversityAlgorithm = parameters.get("diversity:algorithm").toLowerCase();
                    ScoreList results = diversificaton.getRetrievalResult(diversityAlgorithm, qid);
                    StringBuilder outputStr = formatResults(qid, results, parameters);
//                    System.out.println(outputStr);
                    output.write(outputStr.toString());
                }
            }
            System.out.println("Average memory: " + totalMem.get(0) / loopTime);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            output.close();
            if (queryExpansionOutput != null) {
                queryExpansionOutput.close();
            }
        }
    }

    /**
     * Check if query diversification is needed
     */
    private static boolean needDiversification(Map<String, String> parameters) {
        return parameters.containsKey("diversity") && parameters.get("diversity").toLowerCase().equals("true");
    }

    private static boolean hasDiversificationInitialRankingFile(Map<String, String> parameters) {
        return parameters.containsKey("diversity:initialRankingFile");
    }

    /**
     * Check if query expansion is needed
     */
    private static boolean needExpansion(Map<String, String> parameters) {
        return parameters.containsKey("fb") && parameters.get("fb").toLowerCase().equals("true");
    }

    private static boolean hasExpansionInitialRankingFile(Map<String, String> parameters) {
        return parameters.containsKey("fbInitialRankingFile");
    }

    /**
     * Print the query results.
     * <p>
     * STUDENTS::
     * This is not the correct output format. You must change this method so
     * that it outputs in the format specified in the homework page, which is:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param outputStr A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, StringBuilder outputStr) throws IOException {

        System.out.println(queryName + ":  ");
        if (outputStr.length() < 1) {
            System.out.println("\tNo results.");
        } else {
            System.out.print(outputStr);
        }
    }

    private static StringBuilder formatResults(String queryName, ScoreList results, Map<String, String> parameters) throws IOException {
        StringBuilder outputStr = new StringBuilder();
        if (results.size() < 1) {
            //System.out.println("\tNo results.");
            Formatter fmt = new Formatter(outputStr);
            fmt.format("%s ", queryName);
            fmt.format("%s ", "Q0");
            fmt.format("%s ", "dummyRecord");
            fmt.format("%d ", 1);
            fmt.format("%d ", 0);
            fmt.format("%s\n", "BeHappy");
        } else {
            Integer outputLength = getOutputLength(results, parameters);
            for (int i = 0; i < outputLength; i++) {
                Formatter fmt = new Formatter(outputStr);
                fmt.format("%s ", queryName);
                fmt.format("%s ", "Q0");
                fmt.format("%s ", Idx.getExternalDocid(results.getDocid(i)));
                fmt.format("%d ", i + 1);
                fmt.format("%.18f ", results.getDocidScore(i));
                fmt.format("%s\n", "BeHappy");
            }
        }
        return outputStr;
    }

    private static Integer getOutputLength(ScoreList results, Map<String, String> parameters) {
        Integer outputLength = results.size();

        if (parameters.containsKey("trecEvalOutputLength")) {
            String trecEvalOutputLength = parameters.get("trecEvalOutputLength");
            outputLength = Math.min(outputLength, Integer.parseInt(trecEvalOutputLength));
        }
        return outputLength;
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @param parameterFileName The name of the parameter file
     * @return The parameters, in &lt;key, value&gt; format.
     * @throws IllegalArgumentException The parameter file can't be read or doesn't contain required parameters
     * @throws IOException              The parameter file can't be read
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();
        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        //  Store (all) key/value parameters in a hashmap.

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            if (pair.length < 2) {
                throw new IllegalArgumentException("Parameter value missing from the parameter file.");
            }
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        /**
         * Check query expansion parameter
         */
        if (parameters.containsKey("fb") && parameters.get("fb").toLowerCase().equals("true")) {
            try {
                checkQueryExpansionParameters(parameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //  Confirm that some of the essential parameters are present.
        //  This list is not complete.  It is just intended to catch silly
        //  errors.

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        if (!parameters.containsKey("diversity:initialRankingFile") && !parameters.containsKey("retrievalAlgorithm")) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        if (parameters.containsKey("trecEvalOutputLength")) {
            String trecOutputPath = parameters.get("trecEvalOutputLength");
            try {
                Integer.parseInt(trecOutputPath);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Illegal trecEvalOutputLength: not integer");
            }
        }

        /**
         * BM25:k_1=                Values are real numbers >= 0.0.
         * BM25:b=                    Values are real numbers between 0.0 and 1.0.
         * BM25:k_3=                Values are real numbers >= 0.0.
         * Indri:mu=                  Values are integers >= 0.
         * Indri:lambda=           Values are real numbers between 0.0 and 1.0.
         */

        //check Indri
        if (parameters.containsKey("retrievalAlgorithm")) {//only when diversification exists, we do not need this
            String model = parameters.get("retrievalAlgorithm");
            switch (model) {
                case "BM25": {
                    if (parameters.containsKey("BM25:k_1") &&
                            parameters.containsKey("BM25:b") &&
                            parameters.containsKey("BM25:k_3")) {
                        return parameters;
                    } else {
                        throw new IllegalArgumentException("Required parameters for BM25" +
                                " were missing from the parameter file.");
                    }
                }

                case "Indri": {
                    if (parameters.containsKey("Indri:mu") &&
                            parameters.containsKey("Indri:lambda")) {
                        return parameters;
                    } else {
                        throw new IllegalArgumentException("Required parameters for Indri" +
                                " were missing from the parameter file.");
                    }
                }
                default:
                    break;
            }
        }

        return parameters;
    }

    private static void checkQueryExpansionParameters(Map<String, String> parameters) throws Exception {

        if (parameters.containsKey("fbDocs") &&
                parameters.containsKey("fbTerms") &&
                parameters.containsKey("fbMu") &&
                parameters.containsKey("fbOrigWeight") &&
                parameters.containsKey("fbExpansionQueryFile")) {
            try {
                int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
                int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
                int mu = Integer.parseInt(parameters.get("fbMu"));
                double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
                if (fbTerms <= 0 || fbDocs <= 0 || mu < 0 || fbOrigWeight < 0.0 || fbOrigWeight > 1.0) {
                    throw new Exception("Illegal input parameter values for Indri query expansion.");
                }
                return;
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Illegal input parameter type for Indri query expansion.");
            }
        } else {
            throw new IllegalArgumentException("Required parameters for Indri query expansion" +
                    " were missing from the parameter file.");
        }
    }

    /**
     * Get Map of <qid, Scorelist> from existing doc ranking provided
     */
    private static Map<String, ScoreList> processInitialRankingFile(String initialRankingFile) throws Exception {
        Map<String, ScoreList> initialResultMap = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(initialRankingFile));
            String input;
            while ((input = in.readLine()) != null) {
                //String[] inputLine = input.split("\t");//For local test
                String[] inputLine = input.split(" ");//For Homework testing
                if (inputLine.length != 6) {
                    throw new Exception("Incomplete initial ranking file!");
                }
                String qid = inputLine[0];
                String externalDocId = inputLine[2];
                double score = Double.parseDouble(inputLine[4]);
                ScoreList scoreList;
                if (!initialResultMap.containsKey(qid)) {//put a score list for new query
                    initialResultMap.put(qid, new ScoreList());
                }
                //Add scoreEntry in the current scorelist
                scoreList = initialResultMap.get(qid);
                int internalDocId = Idx.getInternalDocid(externalDocId);
                scoreList.add(internalDocId, score);
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return initialResultMap;
    }

    /**
     * <p>
     * Map of <qid, list <qid> >
     * eg. <157, <157.1, 157.2> >
     * <p>
     * Map of <qid, list <qid:query> >
     * eg. <157, <157.1: listing beatles songs, 157.2: history beatles rock band2> >
     */
    private static List<Map<String, List<String>>> getIntentQueryFromIntentFile(String intentsFile) {
        List<Map<String, List<String>>> intentList = new ArrayList<>(2);
        Map<String, List<String>> intentMap = new HashMap<>();
        //eg. <157, <157.1, 157.2> >
        Map<String, List<String>> intentQueryMap = new HashMap<>();
        // eg. <157, <157.1: listing beatles songs, 157.2: history beatles rock band2> >

        try {

            BufferedReader in = new BufferedReader(new FileReader(intentsFile));
            String qLine;

            while ((qLine = in.readLine()) != null) {

                String qid = qLine.split(":")[0].split("\\.")[0];
                String qid_intent = qLine.split(":")[0];

                if (!intentMap.containsKey(qid)) {
                    List<String> qidList = new ArrayList<>();
                    if (qid_intent.contains(".")) {
                        qidList.add(qid_intent);
                    }
                    intentMap.put(qid, qidList);
                } else {
                    List<String> qidList = intentMap.get(qid);
                    qidList.add(qid_intent);
                    intentMap.put(qid, qidList);
                }

                if (!intentQueryMap.containsKey(qid)) {
                    List<String> queryList = new ArrayList<>();
                    queryList.add(qLine);
                    intentQueryMap.put(qid, queryList);
                } else {
                    List<String> queryList = intentQueryMap.get(qid);
                    queryList.add(qLine);
                    intentQueryMap.put(qid, queryList);
                }
            }
            intentList.add(intentMap);
            intentList.add(intentQueryMap);
            in.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return intentList;
    }


    /**
     * Map <qid, list<qi for qid>>
     * eg. <157, <157.1, 157.2> >
     * THE FILE MUST BE SORTED BY QID AND SCORE
     *
     * @param initialRankingFile
     * @return
     * @throws Exception
     */
    private static Map<String, List<String>> getIntentsFromInitialRankingFile(String initialRankingFile) throws Exception {
        Map<String, List<String>> intentsMap = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader((initialRankingFile)));
            String input;
            while ((input = in.readLine()) != null) {
                String[] inputLine = input.split(" ");
                if (inputLine.length != 6) {
                    throw new Exception("Incomplete initial ranking file!");
                }
                String qid = inputLine[0];
                if (!qid.contains(".") && !intentsMap.containsKey(qid)) {
                    intentsMap.put(qid, new ArrayList<>());
                } else {
                    String parentQid = qid.split("\\.")[0];
                    List<String> intents = intentsMap.get(parentQid);
                    if (!intents.contains(qid)) {
                        intents.add(qid);
                    }
                    intentsMap.put(parentQid, intents);
                }
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return intentsMap;
    }

    /**
     * Set the initail result into diversification class for  diversified re-ranking
     */
    private static void setInitialResultsMapforDiversification(Map<String, String> parameters, RetrievalModel model, String queryFilePath,
                                                               Map<String, ScoreList> initialResultsMapforDiversification,
                                                               Map<String, List<String>> intentQueryMap,
                                                               int maxInputRankingsLength) {


        BufferedReader input = null;
        String qLine = null;
        try {

            input = new BufferedReader(new FileReader(queryFilePath));

            while ((qLine = input.readLine()) != null) {

                System.out.println("Get initial diversity rank for query " + qLine);
                String[] pair = qLine.split(":");

                if (pair.length != 2) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Each line must contain one ':'.");
                }

                String qid = pair[0];
                String query = pair[1];
                ScoreList initialResults = processQuery(query, model);
                initialResults.truncate(Math.min(maxInputRankingsLength, initialResults.size()));
//                System.out.println(" -> initial results");
//                StringBuilder outputStr = formatResults(qid, initialResults, parameters);
//                System.out.println(outputStr);
//                outputStr.setLength(0);
                initialResultsMapforDiversification.put(qid, initialResults);

                //Retrieve query intent results
                for (String qIntentLine : intentQueryMap.get(qid)) {
                    System.out.println("Get initial diversity rank for query intent: " + qIntentLine);
                    String[] pair_intent = qIntentLine.split(":");
                    if (pair_intent.length != 2) {
                        throw new IllegalArgumentException
                                ("Syntax error:  Each line must contain one ':'.");
                    }
                    String qid_intent = pair_intent[0];
                    String query_intent = pair_intent[1];
                    ScoreList results_intent = processQuery(query_intent, model);
                    results_intent.truncate(Math.min(maxInputRankingsLength, results_intent.size()));
//                    System.out.println(" -> intent results");
//                    StringBuilder outputStr = formatResults(qid_intent, initialResults, parameters);
//                    System.out.println(outputStr);
//                    outputStr.setLength(0);
                    initialResultsMapforDiversification.put(qid_intent, results_intent);
                }
            }
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Indri psudo query expansion
     * e.g. #wand (0.73 obama 0.43 family 0.40 white 0.65 tree 0.33 politics ...)
     *
     * @param initialResult
     * @return
     */
    private static String getExpandedQuery(ScoreList initialResult, Map<String, String> parameters) throws IOException {
        //Read expansion parameters
        int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
        int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
        int mu = Integer.parseInt(parameters.get("fbMu"));

        // Extract potential expansion terms from top n documents
        // Calculate an Indri score for each potential expansion term
        /**
         * NEED to loop over doc (DAAT) because TermVector is per document
         * for each doc
         *      - get term vector;
         *      for each term in doc:
         *      - calculate score**;
         *      - update score (+=) in term score map
         * **However since TermVector ONLY includes terms that appear in the document,
         *   term score with tf = 0 in that doc will not be calculated.
         *
         *
         *Solution -> maintain HashMap of <doc_id, set of {term}>
         *          -> maintain mle for future use
         *          -> maintain full set of terms
         * RUN again:
         * for each doc
         *       - get doc_id
         *      for each term:
         *          if term in HashMap'set of {term}:
         *              - pass (already calculated)
         *          else:
         *              tf = 0;
         *              mle from hashMap
         *              - calculate score
         *              - update score (+=) in term score map
         */
        Map<Integer, Set<String>> docTermsMap = new HashMap<>();//<doc_id, set of {terms in doc}>
        Map<String, Double> termMleMap = new HashMap<>(); //<term, mle score>
        Set<String> termSet = new HashSet<>();

        int docNum = getExpansionDocNum(fbDocs, initialResult.size());
        Map<String, Double> expansionTermScoreMap = new HashMap<>();
        // for each doc
        for (int i = 0; i < docNum; i++) {

            int internalDocId = initialResult.getDocid(i);
            TermVector termVector = new TermVector(internalDocId, "body");
            long docLength = Idx.getFieldLength("body", internalDocId);
            // P (I | d)
            double indriScore = initialResult.getDocidScore(i);
            Set<String> terms = new HashSet<>();// Set of terms under this doc

            //for each term
            for (int termIndex = 1; termIndex < termVector.stemsLength(); termIndex++) {

                String term = termVector.stemString(termIndex);

                if (term.contains(",") || term.contains(".")) {
                    continue;
                }

                terms.add(term);//update set of terms under this doc
                termSet.add(term);//add term to termset

                // P (t | d)
                double ptd = calculateTermProbInDoc(termVector, termIndex, docLength, mu);
                double curScore = ptd * indriScore;

                if (expansionTermScoreMap.containsKey(term)) {

                    expansionTermScoreMap.put(term, expansionTermScoreMap.get(term) + curScore);
                } else {
                    expansionTermScoreMap.put(term, curScore);
                }
                // Pmle (t | C) -> unrelated to doc
                double mle = 1.0 * termVector.totalStemFreq(termIndex) / Idx.getSumOfFieldLengths("body");
                termMleMap.putIfAbsent(term, mle);//update mle
            }
            docTermsMap.put(internalDocId, terms);//update map of doc to terms
        }

        //Calculate term score omitted (absent from TermVector due to tf = 0)
        for (int i = 0; i < docNum; i++) {
            int internalDocId = initialResult.getDocid(i);
            long docLength = Idx.getFieldLength("body", internalDocId);
            // P (I | d)
            double indriScore = initialResult.getDocidScore(i);
            Set<String> terms = docTermsMap.get(internalDocId);

            for (String term : termSet) {
                if (terms.contains(term)) {
                    continue;
                }

                double mle = termMleMap.get(term);
                double ptd = calculateTermProbInDoc(docLength, mu, mle);
                double curScore = ptd * indriScore;

                if (expansionTermScoreMap.containsKey(term)) {

                    expansionTermScoreMap.put(term, expansionTermScoreMap.get(term) + curScore);
                } else {
                    expansionTermScoreMap.put(term, curScore);
                }
            }
        }

        List<Map.Entry<String, Double>> sortedExpensionTermScoreList = sortTermScoreMap(expansionTermScoreMap);

        // Use the top m terms to create an expansion query Qlearned
        String expandedQuery = createExpandedQuery(sortedExpensionTermScoreList, fbTerms);
        return expandedQuery;

    }


    /**
     * Calculate idf adjusted P (t | d)  for Indri query expansion
     *
     * @param termVector
     * @param termIndex
     * @param docLength
     * @param mu
     * @return
     * @throws IOException
     */
    private static double calculateTermProbInDoc(TermVector termVector, int termIndex, long docLength, int mu) throws IOException {
        // P (t | d)
        int tf = termVector.stemFreq(termIndex);
        // Pmle (t | C)
        double mle = 1.0 * termVector.totalStemFreq(termIndex) / Idx.getSumOfFieldLengths("body");
        double idf = Math.log(1 / mle);
        return (tf + mu * mle) / (docLength + mu * 1.0) * idf;
    }

    /**
     * Calculate idf adjusted P (t | d)  for Indri query expansion, tf = 0
     *
     * @param docLength
     * @param mu
     * @param mle       Pmle (t | C)
     * @return
     */
    private static double calculateTermProbInDoc(long docLength, int mu, double mle) {
        int tf = 0;
        double idf = Math.log(1 / mle);
        return (tf + mu * mle) / (docLength + mu * 1.0) * idf;
    }

    private static int getExpansionDocNum(int fbDocs, int initialResultSize) {
        return Math.min(fbDocs, initialResultSize);
    }

    /**
     * Sort into a list , DESC
     *
     * @param expansionTermScoreMap
     * @return
     */
    private static List<Map.Entry<String, Double>> sortTermScoreMap(Map<String, Double> expansionTermScoreMap) {
        List<Map.Entry<String, Double>> list = new LinkedList<>(expansionTermScoreMap.entrySet());

        //Sort by descending order
        Collections.sort(list, (o1, o2) -> (o2.getValue()).compareTo(o1.getValue()));
        return list;
    }

    private static String createExpandedQuery(List<Map.Entry<String, Double>> sortedExpensionTermScoreList, int termNum) {
        StringBuilder expandedQuery = new StringBuilder();
        expandedQuery.append("#WAND (");
        for (int i = 0; i < termNum; i++) {
            Map.Entry<String, Double> entry = sortedExpensionTermScoreList.get(i);
            expandedQuery.append(String.format("%.4f ", entry.getValue()));
            expandedQuery.append(String.format("%s ", entry.getKey()));
        }
        expandedQuery.append(")");
        return expandedQuery.toString();
    }

    /**
     * create a combined query as #wand (w qoriginal + (1-w) qexpandedquery);
     *
     * @param query
     * @param expandedQuery
     * @return
     */
    private static String getCombinedQuery(String query, String expandedQuery, double fbOrigWeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("#WAND (");
        sb.append(String.format("%.4f ", fbOrigWeight));
        sb.append(query + " ");
        sb.append(String.format("%.4f ", 1 - fbOrigWeight));
        sb.append(expandedQuery);
        sb.append(" )");
        String combinedQuery = sb.toString();
        return combinedQuery;
    }


}
