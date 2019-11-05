/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;
import java.util.List;

/**
 * The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

    /**
     *  Document-independent values that should be determined just once.
     *  Some retrieval models have these, some don't.
     */

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchFirst(r);
    }

    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean(r);
        }

        //  STUDENTS::
        //  Add support for other retrieval models here.

        if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        }
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        }
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SCORE operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, int doc_id) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getDefaultScoreBM25(r);
        }
        if (r instanceof RetrievalModelIndri) {
            return this.getDefaultScoreIndri(r, doc_id);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the Default SCORE operator.");
        }
    }

    /**
     * getScore for the Unranked retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     * getScore for the Ranked retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreRankedBoolean(RetrievalModel r) {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;//to-delete: unreachable?
        } else {
            return this.getArg(0).docIteratorGetMatchPosting().tf;
        }
    }


    private double getScoreBM25(RetrievalModel r) throws IOException {

        QryIop qry = this.getArg(0);
        double score;
        // no need to validate hasMatchCache -> SoP that calls it already validates
//        if(qry.docIteratorHasMatch(r)){
//
//        // calculate score
//           score = calculateBM25Score(qry, r);
//
//        }else{
//            return this.getDefaultScoreBM25(r);
//        }
        score = calculateBM25Score(qry, r);
        return score;
    }

    // calculate BM25 score of one single inverted list e.g. #SCORE(apple)
    private double calculateBM25Score(QryIop qry, RetrievalModel r) throws IOException {
        double k1 = ((RetrievalModelBM25) r).getK1();
        double k3 = ((RetrievalModelBM25) r).getK3();
        double b = ((RetrievalModelBM25) r).getB();

        // RSJ
        int df = qry.getDf();
        long N = Idx.getNumDocs();
        double RSJ = Math.max(Math.log((N - df + 0.5) / (df + 0.5)), 0);

        // tf weight
        int tf = qry.docIteratorGetMatchPosting().tf;
        int doc_id = qry.docIteratorGetMatch();
        String field = qry.getField();
        long docLength = Idx.getFieldLength(field, doc_id);
        double avgDocLen = Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);
        double tf_weight = tf / (tf + k1 * ((1 - b) + b * docLength / avgDocLen));

        //user weight
        //In this system -> Your BM25 queries will always have qtf=1
        int qtf = 1;
        double userWeight = (k3 + 1) * qtf / (k3 + qtf);
        double score = RSJ * tf_weight * userWeight;
        return score;
    }

    /**
     * Calculate BM25 score for Learning to Rank in FeatureVector class
     * using term vector instead of inverted list
     *
     * @param doc_id internal doc id
     * @param field
     * @param terms
     * @return
     * @throws IOException // tokenize the query before calling featureBM25
     *                     queryStems = QryParser.tokenizeQuery (query);
     *                     <p>
     *                     featureBM25 (queryStems, docid, field):
     *                     score = 0
     *                     for each stem in <docid, field>
     *                     if stem is a queryStem
     *                     score += BM25 term score for stem
     *                     end
     *                     end
     *                     <p>
     *                     return score
     */
    public double getScoreBM25(int doc_id, String field, List<String> terms, RetrievalModelLetor r) throws IOException {
        double score = 0;

        TermVector termVector = new TermVector(doc_id, field);
        //Term vector for this field does not exist, set BM25 score to 0.0 (consistent with HW2 implementation)
        if (termVector.positionsLength() == 0 || termVector.stemsLength() == 0) {
            return Double.MIN_VALUE;
        }

        for (String term : terms) {
            int index = termVector.indexOfStem(term);
            if (index == -1) {
                // field BOW does not contain this term, skip
                continue;
            }

            // RSJ
            int df = termVector.stemDf(index);
            long N = Idx.getNumDocs();
            double RSJ = Math.max(Math.log((N - df + 0.5) / (df + 0.5)), 0);


            // tf weight
            double k1 = ((RetrievalModelLetor) r).k1;
            double b = ((RetrievalModelLetor) r).b;
            int tf = termVector.stemFreq(index);
            long docLength = Idx.getFieldLength(field, doc_id);
            double avgDocLen = Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);
            double tf_weight = tf / (tf + k1 * ((1 - b) + b * docLength / avgDocLen));


            //user weight
            double k3 = ((RetrievalModelLetor) r).k3;
            //In this system -> Your BM25 queries will always have qtf=1
            int qtf = 1;
            double userWeight = (k3 + 1) * qtf / (k3 + qtf);

            score += RSJ * tf_weight * userWeight;
        }
        return score;
    }

    private double getDefaultScoreBM25(RetrievalModel r) {
        return 0.0; // tf = 0 -> total BM25 score for this inverted list = 0
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {

        QryIop qry = this.getArg(0);
        double score = calculateIndriScore(qry, r);
        return score;
    }

    /**
     * Calculate Indri score for Learning to Rank in FeatureVector class
     * using term vector instead of inverted list
     *
     * @param doc_id
     * @param field
     * @param terms
     * @param r
     * @return
     */
    public double getScoreIndri(int doc_id, String field, List<String> terms, RetrievalModelLetor r) throws IOException {
        //Default: Indri #AND

        double score = 1;
        long docLength = Idx.getFieldLength(field, doc_id);
        long collectionLength = Idx.getSumOfFieldLengths(field);
        TermVector termVector = new TermVector(doc_id, field);

        //Term vector for this field does not exist, set Indri score to 0.0 (consistent with HW2 implementation)
        if (termVector.positionsLength() == 0 || termVector.stemsLength() == 0) {
            return Double.MIN_VALUE;
        }

        boolean match = false;

        for (String term : terms) {
            int index = termVector.indexOfStem(term);
            double tf = index == -1 ? 0 : termVector.stemFreq(index);
            match = (index != -1);

            double lambda = r.lambda;
            double mu = r.mu;

            double ctf = Idx.getTotalTermFreq(field, term);
            double mle = ctf / collectionLength;
            score *= (1 - lambda) * (tf + mu * mle) / (docLength + mu) + lambda * mle;
        }

        // if a field does not match any term of a query, the score for the field is 0.
        // Documents that have no terms in common with the query were not given a score.
        if (!match) {
            return Double.MIN_VALUE;
        }

        score = Math.pow(score, 1.0 / terms.size());
        return score;
    }


    private double calculateIndriScore(QryIop qry, RetrievalModel r) throws IOException {
        double score;
        double lambda = ((RetrievalModelIndri) r).getLambda();
        double mu = ((RetrievalModelIndri) r).getMu();
        int tf = qry.docIteratorGetMatchPosting().tf;

        String field = qry.getField();
        double mle = 1.0 * qry.getCtf() / Idx.getSumOfFieldLengths(field);
        int doc_id = qry.docIteratorGetMatch();
        long docLength = Idx.getFieldLength(field, doc_id);

        score = (1 - lambda) * (tf + mu * mle) / (docLength + mu) + lambda * mle;
        return score;
    }


    private double getDefaultScoreIndri(RetrievalModel r, int doc_id) throws IOException {
        QryIop qry = this.getArg(0);
        double score = calculateDefaultIndriScore(qry, r, doc_id);
        return score;
    }

    private double calculateDefaultIndriScore(QryIop qry, RetrievalModel r, int doc_id) throws IOException {
        double lambda = ((RetrievalModelIndri) r).getLambda();
        double mu = ((RetrievalModelIndri) r).getMu();

        String field = qry.getField();
        double mle = 1.0 * qry.getCtf() / Idx.getSumOfFieldLengths(field);
        long docLength = Idx.getFieldLength(field, doc_id);
        double score = (1 - lambda) * (mu * mle) / (docLength + mu) + lambda * mle;
        return score;
    }

    /**
     * Initialize the query operator (and its arguments), including any
     * internal iterators.  If the query operator is of type QryIop, it
     * is fully evaluated, and the results are stored in an internal
     * inverted list that may be accessed via the internal iterator.
     *
     * @param r A retrieval model that guides initialization
     * @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException {

        Qry q = this.args.get(0);
        q.initialize(r);
    }

    /**
     * Calculate the percentage of query terms that match the document field.
     * For learning to rank
     *
     * @param doc_id
     * @param field
     * @param terms
     * @return
     */
    public double getTermOverlapPct(int doc_id, String field, List<String> terms) throws IOException {
        if (terms.size() == 0) {
            return Double.MIN_VALUE;
        }
        TermVector termVector = new TermVector(doc_id, field);
        //Term vector for this field does not exist, set Indri score to 0.0 (consistent with HW2 implementation)
        if (termVector.positionsLength() == 0 || termVector.stemsLength() == 0) {
            return Double.MIN_VALUE;
        }
        int commonTermCount = 0;
        for (String term : terms) {
            int index = termVector.indexOfStem(term);
            if (index == -1) {
                continue;
            }
            commonTermCount++;
        }
        return commonTermCount / (1.0 * terms.size());
    }
}
