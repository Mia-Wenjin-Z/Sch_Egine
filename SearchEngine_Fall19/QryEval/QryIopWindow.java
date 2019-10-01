import java.io.IOException;
import java.util.*;

//todo
public class QryIopWindow extends QryIop {
    private int n; // the parameter parsed from #near/n
    private int maxLoc = -1;// the max location index satisfying window/n

    public QryIopWindow(int n) {
        this.n = n;
        if (n == 0) {
            throw new IllegalArgumentException("Illegal /n in #Window/n operator");
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
            // find common doc
            int maxDocid = findDocid();

            // All docids have been processed.  Done.
            if (maxDocid == Qry.INVALID_DOCID) {
                break;
            }

            //  Create a new posting that records the right-most index positions satisfying near/n
            //  that match the maxDocid.  Save it.
            //  Note:  This implementation only considers a term ONCE to match near/n
            // for instance, a1 x x a2 x b -> #window/4 (a b) will return null since b is considered unmatched with a1

            List<Integer> positions = new ArrayList<Integer>();// store right-most index of matching near #/n term pairs
            PriorityQueue<QryIop> locationHeap = new PriorityQueue<>(this.args.size(), new QryIoPWindowComparator());
            maxLoc = -1;
            //store QryIop in a minHeap in terms of loc index

            if (!setlocationHeap(locationHeap)) {
                //no matching at all, consider next doc
                docIteratorAdvanceAll(maxDocid);
                continue;
            }

            while (true) {
                if (satisfyMatch(locationHeap)) {// max-min <= n
                    positions.add(maxLoc);
                    locationHeap.clear();
                    maxLoc = -1;
                    locIteratorAdvanceAll();

                    if (!setlocationHeap(locationHeap)) {
                        break;// loc iterator exhausted, consider next doc
                    }
                } else {
                    QryIop q_min = locationHeap.poll();
                    q_min.locIteratorAdvance();//advance min loc iterator
                    if (q_min.locIteratorHasMatch()) {
                        //update maxLoc
                        maxLoc = maxLoc > q_min.locIteratorGetMatch() ? maxLoc : q_min.locIteratorGetMatch();
                        //add updated q_min back to min heap
                        locationHeap.offer(q_min);
                        //continue;
                    } else {
                        break;
                        // loc iterator exhausted, consider next doc
                    }
                }
            }

            appendPosting(maxDocid, positions);
            docIteratorAdvanceAll(maxDocid);
        }

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
                        if (maxDocid == Qry.INVALID_DOCID) {
                            return maxDocid;//the current q_i's documents are exhausted. docids have been processed.  Done.
                            // break; INCORRECT!
                            // jump out of for-each and directly goes to top while(true)
                            // lines below for-each will not be executed
                        }
                        commonPosition.add(maxDocid);
                    }

                } else {// the current q_i's documents are exhausted. docids have been processed.  Done.
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
    }//todo duplicate? -> qrylop?

    private int updateMaxDocid(Qry q_i, int maxDocid) {
        if (q_i.docIteratorHasMatch(null)) {
            int q_iDocid = q_i.docIteratorGetMatch();
            if ((maxDocid < q_iDocid) ||
                    (maxDocid == Qry.INVALID_DOCID)) {
                maxDocid = q_iDocid;
            }
        } else {
            maxDocid = Qry.INVALID_DOCID;// the current q_i's documents are exhausted. done
        }
        return maxDocid;
    }//todo duplicate


    private void docIteratorAdvanceAll(int maxDocid) {
        for (Qry q_i : this.args) {
            q_i.docIteratorAdvancePast(maxDocid);
        }
    }//todo duplicate

    // advance all locationIterator
    private void locIteratorAdvanceAll() {
        for (Qry q_i : this.args) {
            ((QryIop) q_i).locIteratorAdvance();
        }
        return;
    }//todo duplicate

    //todo setup of min heap and update maxloc
    private boolean setlocationHeap(PriorityQueue<QryIop> locationHeap) {
        for (Qry q_i : this.args) {
            if (((QryIop) q_i).locIteratorHasMatch()) {
                int loc = ((QryIop) q_i).locIteratorGetMatch();
                maxLoc = maxLoc < loc ? loc : maxLoc;
                locationHeap.offer((QryIop) q_i);
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean satisfyMatch(PriorityQueue<QryIop> locationHeap) {
        return maxLoc - locationHeap.peek().locIteratorGetMatch() < n;
    }

    // append matched window/n doc and loc information to invertedlist
    private void appendPosting(int maxDocid, List<Integer> positions) {
        if (positions.size() > 0) {
            this.invertedList.appendPosting(maxDocid, positions);
        }
    }

    /**
     * Comparator for Location MinHeap -> top element has smallest index
     */
    public class QryIoPWindowComparator implements Comparator<QryIop> {

        @Override
        public int compare(QryIop q1, QryIop q2) {
            int loc1 = q1.locIteratorGetMatch();
            int loc2 = q2.locIteratorGetMatch();
            if (loc1 < loc2)
                return -1;
            else if (loc1 > loc2)
                return 1;
            else
                return 0;
        }
    }

}
