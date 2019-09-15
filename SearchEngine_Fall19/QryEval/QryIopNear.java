import java.io.IOException;
import java.util.*;

/**
 *
 * The NEAR operator for all retrieval models.
 */

public class QryIopNear extends QryIop{

    private int n; // the parameter parsed from #near/n

    public QryIopNear(int n) {
        this.n = n;
        if(n == 0){
            throw new IllegalArgumentException("Illegal /n in #near/n operator");
        }
    }

    @Override
    protected void evaluate() throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  this is the final result.

        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        //  Each pass of the loop adds 1 document to result inverted list
        //  until all of the argument inverted lists are depleted.

        while (true) {

            int maxDocid = findDocid();

            // All docids have been processed.  Done.
            if (maxDocid == Qry.INVALID_DOCID) {
                break;
            }

            //  Create a new posting that records the right-most index positions satisfying near/n
            //  that match the maxDocid.  Save it.
            //  Note:  This implementation only considers a term ONCE to match near/n
            // for instance, a1 x x a2 x b -> #near/4 (a b) will return null since b is considered unmatched with a1

            List<Integer> positions = new ArrayList<Integer>();// store right-most index of matching near #/n term pairs
            List<Integer> locationVector = new ArrayList<>(this.args.size()); // store locations of each term in a vector


            // Set the location vector
            if (!setLocationVector(locationVector, maxDocid)) {
                // no matching at all, consider next doc
                docIteratorAdvanceAll(maxDocid);
                continue;
            }
            // loop within a doc to find all matching term loc index
            while (true) {

                if (satisfyMatch(locationVector, n)) {

                    positions.add(locationVector.get(locationVector.size() - 1));
                    locationVector.clear();
                    locIteratorAdvanceAll();

                    if (!setLocationVector(locationVector, maxDocid)) {
                        break;// loc iterator exhausted, consider next doc
                    }
                } else {
                    advanceLeftMostLocIterator();
                    Qry q_left = this.args.get(0);
                    if (((QryIop) q_left).locIteratorHasMatch()) {
                        // recursively update location vector
                        int leftLocation = ((QryIop) q_left).locIteratorGetMatch();

                        if (!updateLocationVector(locationVector, leftLocation)) {
                            break;// loc iterator exhausted, consider next doc
                        }
                    } else {
                        break;// loc iterator exhausted, consider next doc
                    }
                }
            }

            appendPosting(maxDocid, positions);
            docIteratorAdvanceAll(maxDocid);
        }
    }

    // append matched near/n doc and loc information to invertedlist
    private void appendPosting(int maxDocid, List<Integer> positions){
        if(positions.size()>0){
            this.invertedList.appendPosting(maxDocid, positions);}
    }

    //  Find the next document id that contains all query terms. If there is none, we're done.
    // todo this method is very lengthy and needs future refinement

    private int findDocid() {

        Set<Integer> commonPosition = new HashSet<>(args.size());
        int maxDocid = Qry.INVALID_DOCID;

        while (true) {
            for (Qry q_i : this.args) {

                if (q_i.docIteratorHasMatch(null)) {
                    int q_iDocid = q_i.docIteratorGetMatch();

                    if ((maxDocid < q_iDocid) ||
                            (maxDocid == Qry.INVALID_DOCID)) {
                        maxDocid = q_iDocid;
                        commonPosition.add(maxDocid);
                    } else {
                        q_i.docIteratorAdvanceTo(maxDocid);
                        maxDocid = updateMaxDocid(q_i, maxDocid);
                        if(maxDocid == Qry.INVALID_DOCID){
                            return maxDocid;//the current q_i's documents are exhausted. docids have been processed.  Done.
                            // break; INCORRECT!
                            // jump out of for-each and directly goes to top while(true)
                            // lines below for-each will not be executed
                        }
                        commonPosition.add(maxDocid);
                    }

                }
                else{// the current q_i's documents are exhausted. docids have been processed.  Done.
                    maxDocid = Qry.INVALID_DOCID;
                    return maxDocid;
                    //break;
                }
            }
            if (commonPosition.size() == 1) {// reach to the common doc id
                break;
            } else if (commonPosition.size() == 0) {// All docids have been processed.  Done.
                maxDocid = Qry.INVALID_DOCID;
                return maxDocid;
            } else {
                commonPosition.clear();// does not reach to the common doc id
                commonPosition.add(maxDocid);
            }
        }
        return maxDocid;
    }

    private int updateMaxDocid(Qry q_i,int maxDocid ){
        if (q_i.docIteratorHasMatch(null)) {
            int q_iDocid = q_i.docIteratorGetMatch();
            if ((maxDocid < q_iDocid) ||
                    (maxDocid == Qry.INVALID_DOCID)) {
                maxDocid = q_iDocid;
            }
        }else{
            maxDocid = Qry.INVALID_DOCID;// the current q_i's documents are exhausted. done
        }
        return maxDocid;
    }



    // get the legal (i.e. index of term i < index of term i + 1) location vector
    // returns true if loc iterator has not been exhausted

    private boolean setLocationVector(List<Integer> locationVector, int maxDocid) {
        for (int i = 0; i < this.args.size(); i++) {
            Qry q_i = this.args.get(i);

            if (((QryIop) q_i).locIteratorHasMatch()) {
                int location = ((QryIop) q_i).locIteratorGetMatch();

                if (locationVector.size() >  Math.max(0, i - 1)) {
                    int prevLocation = locationVector.get(locationVector.size() - 1);

                    //ensure current location index > prev location index
                    if (prevLocation > location) {
                        ((QryIop) q_i).locIteratorAdvancePast(prevLocation);
                      if(((QryIop) q_i).locIteratorHasMatch()){
                          location = ((QryIop) q_i).locIteratorGetMatch();
                      }
                      else{
                          locationVector.clear();
                          return false;
                      }
                    }
                }
                locationVector.add(location);// initial set of locationVector, directly add
            } else {
                locationVector.clear();
                return false;
            }
        }
        return true;
    }

    // advance all locationIterator
    private void locIteratorAdvanceAll() {
        for (Qry q_i : this.args) {
            ((QryIop) q_i).locIteratorAdvance();
        }
        return;
    }

    // check if location index satisfy near/n
    private boolean satisfyMatch(List<Integer> locationVector, int n) {
        int prevLocation = locationVector.get(0);
        for (int i = 0; i < locationVector.size() - 1; i++) {
            int currLocation = locationVector.get(i + 1);
            if (currLocation - prevLocation > n) {
                return false;
            }
            prevLocation = currLocation;
        }
        return true;
    }

    private void advanceLeftMostLocIterator() {
        Qry q_left = this.args.get(0);
        ((QryIop) q_left).locIteratorAdvance();
    }

    // update location vector after left most loc iterator advanced
    private boolean updateLocationVector(List<Integer> locationVector, int leftLocation) {
        locationVector.set(0, leftLocation);
        if (leftLocation > locationVector.get(1)) {
            return updateHelper(locationVector, 0);
        }
        return true;
    }

    // recursively update(if needed) loc iterator and location vector after left-most iterator advances
    private boolean updateHelper(List<Integer> locationVector, int left) {
        if (left == locationVector.size() - 1) {
            return true;
        }
        int prevLocation = locationVector.get(left);
        int currLocation = locationVector.get(left + 1);

        if (prevLocation > currLocation) {
            Qry q = this.args.get(left + 1);
            ((QryIop) q).locIteratorAdvancePast(prevLocation);

            if (((QryIop) q).locIteratorHasMatch()) {
                currLocation = ((QryIop) q).locIteratorGetMatch();
                locationVector.set(left + 1, currLocation);
                return updateHelper(locationVector, left + 1);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private void docIteratorAdvanceAll(int maxDocid){
        for(Qry q_i: this.args) {
            q_i.docIteratorAdvancePast(maxDocid);
        }
    }
}
