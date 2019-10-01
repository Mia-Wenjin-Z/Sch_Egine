import java.io.IOException;
import java.util.ArrayList;


public class QrySopWand extends QrySopW {
//    public QrySopWand(ArrayList<Double> weightVector) {
//        super(weightVector);
//    }//to-delete

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if(this.getSizeofWeight() != this.args.size()){
            throw new IllegalArgumentException("Weights not matching arguments!");
        }
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException("#WAND cannot be applied to" + r.getClass().getName());
        }
    }

    private double getScoreIndri(RetrievalModel r) throws IOException {

        double score = 1.0;
        double sumWeight = this.getSumofWeight();
        if (this.docIteratorHasMatchCache()) {
            int doc_id = this.docIteratorGetMatch();
            for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                double weight = this.getWeight(i);
                if (q_i.docIteratorHasMatchCache() && q_i.docIteratorGetMatch() == doc_id) {
                    score *= Math.pow(((QrySop) q_i).getScore(r), weight / sumWeight);
                } else {
                    score *= Math.pow(((QrySop) q_i).getDefaultScore(r, doc_id), weight / sumWeight);
                }
            }
        }
        return score;
    }

    @Override
    public double getDefaultScore(RetrievalModel r, int doc_id) throws IOException{
        if(this.getSizeofWeight() != this.args.size()){
            throw new IllegalArgumentException("Weights not matching arguments!");
        }
        double score = 1.0;
        double sumWeight = this.getSumofWeight();
        for (int i = 0; i < this.args.size(); i++) {
            Qry q_i = this.args.get(i);
            double weight = this.getWeight(i);
            score *= Math.pow(((QrySop) q_i).getDefaultScore(r, doc_id), weight / sumWeight);
        }
        return score;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }
}
