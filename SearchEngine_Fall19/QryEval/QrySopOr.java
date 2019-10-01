/**
 * Copyright (c) 2019, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 * The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
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

        else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    public double getScoreIndri(RetrievalModel r) throws IOException {
        //to-delete: this is not required!
        double score;
        double multi = 1.0;
        if (this.docIteratorHasMatchCache()) {
            int doc_id = this.docIteratorGetMatch();
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache()) {
                    multi *= (1 - ((QrySop) q_i).getScore(r));
                } else {
                    multi *= (1 - ((QrySop) q_i).getDefaultScore(r, doc_id));
                }
            }

        } else {
            return 1 - multi;
        }

        score = 1 - multi;
        return score;
    }

    //todo to-delete this is not required
    @Override
    public double getDefaultScore(RetrievalModel r, int doc_id) throws IOException {
        double score;
        double multi = 1.0;
        for (Qry q_i : this.args) {
            multi *= (1 - ((QrySop) q_i).getDefaultScore(r, doc_id));
        }

        score = 1 - multi;
        return score;
    }


    /**
     * getScore for the UnrankedBoolean retrieval model.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int doc_id = this.docIteratorGetMatch();
            double score = 0.0;

            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == doc_id) {
                    score = Math.max(score, ((QrySop) q_i).getScore(r));
                }
            }
            return score;
        }
    }
}
