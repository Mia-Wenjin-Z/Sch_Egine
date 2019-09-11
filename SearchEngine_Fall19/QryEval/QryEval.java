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
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);

    //  Perform experiments.
    
    processQueryFile(parameters.get("queryFilePath"), model);

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @param parameters All of the parameters contained in the parameter file
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {

      model = new RetrievalModelUnrankedBoolean();

      //  If this retrieval model had parameters, they would be
      //  initialized here.

    }

    //  STUDENTS::  Add new retrieval models here.
    

    else {

      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc 
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qryString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qryString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qryString = defaultOp + "(" + qryString + ")";
    Qry q = QryParser.getQuery (qryString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList results = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          results.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return results;
    } else
      return null;
  }

  /**
   *  Process the query file.
   *  @param queryFilePath Path to the query file
   *  @param model A retrieval model that will guide matching and scoring
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath,
                               RetrievalModel model)
      throws IOException {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {

        printMemoryUsage(false);
        System.out.println("Query " + qLine);
	String[] pair = qLine.split(":");

	if (pair.length != 2) {
          throw new IllegalArgumentException
            ("Syntax error:  Each line must contain one ':'.");
	}

	String qid = pair[0];
	String query = pair[1];
        ScoreList results = processQuery(query, model);

        if (results != null) {
          printResults(qid, results);
          System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * 
   * STUDENTS:: 
   * This is not the correct output format. You must change this method so
   * that it outputs in the format specified in the homework page, which is:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result) throws IOException {

    System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      System.out.println("\tNo results.");
    } else {
      for (int i = 0; i < result.size(); i++) {
        System.out.println("\t" + i + ":  " + Idx.getExternalDocid(result.getDocid(i)) + ", "
            + result.getDocidScore(i));
      }
    }
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @param parameterFileName The name of the parameter file
   *  @return The parameters, in &lt;key, value&gt; format.
   *  @throws IllegalArgumentException The parameter file can't be read or doesn't contain required parameters
   *  @throws IOException The parameter file can't be read
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();
    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    //  Store (all) key/value parameters in a hashmap.

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    //  Confirm that some of the essential parameters are present.
    //  This list is not complete.  It is just intended to catch silly
    //  errors.

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

}
