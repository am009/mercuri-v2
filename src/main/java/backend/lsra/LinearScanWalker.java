package backend.lsra;

import java.util.LinkedList;
import java.util.List;

public class LinearScanWalker {

    List<LiveInterval>[] spillIntervals;
    int[] usePos; // free util
    int[] blockPos; // when reach, prevent freed by spilling
    private MoveResolver moveResolver;

    IntervalRegBindingList unhanndledIntervals;
    IntervalRegBindingList activeIntervals;
    IntervalRegBindingList inactiveIntervals;

    private long currentPosition;
    protected LiveInterval current;

    public LinearScanWalker(List<LiveInterval> fixedIntervals, List<LiveInterval> nonFixedIntervals) {

        int nPhyRegs = LsraConsts.PHY_REG_UPBOUND;
        this.moveResolver = new MoveResolver();
        this.spillIntervals = new List[nPhyRegs];
        for (int i = 0; i < nPhyRegs; i++) {
            spillIntervals[i] = new LinkedList<>();
        }
        this.usePos = new int[nPhyRegs];
        this.blockPos = new int[nPhyRegs];
    }

    public void walk() {
        while (!unhanndledIntervals.isEmpty()) {
            var opIdx = current.first().start;
            currentPosition = opIdx;
            updateState();
            activeIntervals.insertInterval(current);
        }
    }

    private void updateState() {
        int ACTIVE = 0;
        int IN_ACTIVE = 1;
        var handlingType = ACTIVE;
        while(handlingType <= IN_ACTIVE){
            
            handlingType++;
        }
    }

}
