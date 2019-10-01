import java.io.IOException;
import java.util.ArrayList;

/**
 * class of weighted score operator
 */
public abstract class QrySopW extends QrySop {
    private ArrayList<Double> weightVector = new ArrayList<>();

    //todo setweight

    public void appendWeight(double weight) {
        this.weightVector.add(weight);
    }

    public double getSumofWeight() {
        double sum = 0;
        for (Double weight : weightVector) {
            sum += weight;
        }
        return sum;
    }

    public double getSizeofWeight(){
        return this.weightVector.size();
    }

    public double getWeight(int index) {
        if (index > this.weightVector.size() - 1){
            throw new IllegalArgumentException("weight for index " + index + "does not exist!");
        }
        return this.weightVector.get(index);
    }
    public abstract double getScore(RetrievalModel r) throws IOException;

    public abstract double getDefaultScore(RetrievalModel r, int doc_id) throws IOException;

}
