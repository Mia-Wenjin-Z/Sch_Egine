public class RetrievalModelBM25 extends RetrievalModel{

    private double k1;
    private double k3;
    private double b;

    public RetrievalModelBM25(double k1, double k3, double b) {
        this.k1 = k1;
        this.k3 = k3;
        this.b = b;
    }

    public double getK1() {
        return k1;
    }

    public double getK3() {
        return k3;
    }

    public double getB() {
        return b;
    }

    @Override
    public String defaultQrySopName() {
        return new String ("#SUM");
    }
}
