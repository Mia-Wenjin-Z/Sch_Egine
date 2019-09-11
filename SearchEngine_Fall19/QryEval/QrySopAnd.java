import java.io.IOException;

/**
 * todo
 *  The OR operator for all retrieval models.
 */

public class QrySopAnd extends QrySop {

    @Override
    public double getScore(RetrievalModel r) throws IOException {
        return 0;
    }

    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return false;
    }
}
