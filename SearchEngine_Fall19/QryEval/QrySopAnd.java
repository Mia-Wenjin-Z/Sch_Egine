import java.io.IOException;

/**
 * The AND operator for all retrieval models.
 */

public class QrySopAnd extends QrySop {

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);//appears at least once
        }
        return this.docIteratorHasMatchAll(r);
    }


    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    @Override
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
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    @Override
    public double getDefaultScore(RetrievalModel r, int doc_id) throws IOException {
        double score = 1.0;
        for (Qry qry : this.args) {
            score *= ((QrySop) qry).getDefaultScore(r, doc_id);
        }
        return Math.pow(score, 1.0 / this.args.size());
    }

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
            double score = Integer.MAX_VALUE;
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == doc_id) {
                    score = Math.min(score, ((QrySop) q_i).getScore(r));
                } else {
                    return 0.0;//to-delete: bug fixed
                }
            }
            return score;
        }
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 1.0;
        if (this.docIteratorHasMatchCache()) {
            //call get score
            int doc_id = this.docIteratorGetMatch();
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == doc_id) {
                    score *= ((QrySop) q_i).getScore(r);
                    //to-delete: only considers how scores combine in the current layer
                } else {
                    score *= ((QrySop) q_i).getDefaultScore(r, doc_id);
                }
            }
        } else {
            //if there is no match at all
            return 1.0;// or 0.0?
        }
        return Math.pow(score, 1.0 / this.args.size());
    }
}




