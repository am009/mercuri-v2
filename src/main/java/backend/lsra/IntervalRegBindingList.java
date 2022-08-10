package backend.lsra;

import java.util.List;

public class IntervalRegBindingList {
    public List<LiveInterval> fixedIntervals;
    public List<LiveInterval> nonFixedIntervals;

    public IntervalRegBindingList(List<LiveInterval> fixedIntervals, List<LiveInterval> nonFixedIntervals) {
        this.fixedIntervals = fixedIntervals;
        this.nonFixedIntervals = nonFixedIntervals;
    }

    public List<LiveInterval> getFixedIntervals() {
        return fixedIntervals;
    }

    public List<LiveInterval> getNonFixedIntervals() {
        return nonFixedIntervals;
    }

    public void insertInterval(LiveInterval interval) {
        List<LiveInterval> target;
        if (interval.isFixed()) {
            target = fixedIntervals;
        } else {
            target = nonFixedIntervals;
        }
        // insert in order
        for (int i = 0; i < target.size(); i++) {
            if (target.get(i).current().start > interval.current().start) {
                target.add(i, interval);
                return;
            }
        }
        target.add(interval);
        return;
    }

    public void removeInterval(LiveInterval interval) {
        if (interval.isFixed()) {
            fixedIntervals.remove(interval);
        } else {
            nonFixedIntervals.remove(interval);
        }
    }

    public boolean isEmpty() {
        return fixedIntervals.isEmpty() && nonFixedIntervals.isEmpty();
    }


    
}
