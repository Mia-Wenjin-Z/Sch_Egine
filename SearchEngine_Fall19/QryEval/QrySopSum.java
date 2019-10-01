import java.io.IOException;

public class QrySopSum extends QrySop {
    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException("#SUM cannot be applied to " + r.getClass().getName());
        }
    }

    private double getScoreBM25(RetrievalModel r) throws IOException {
        double score = 0.0;
        if (this.docIteratorHasMatchCache()) {
            int doc_id = this.docIteratorGetMatch();
            for (Qry q_i : this.args) {
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == doc_id) {
                    score += ((QrySop) q_i).getScore(r);
                } else {
                    score += ((QrySop) q_i).getDefaultScore(r, doc_id);
                }
            }
        }
        return score;
    }

    @Override
    public double getDefaultScore(RetrievalModel r, int doc_id) throws IOException {
        double score = 0.0;
        for (Qry q_i : this.args) {
            score += ((QrySop) q_i).getDefaultScore(r, doc_id);
        }
        return score;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }
}
