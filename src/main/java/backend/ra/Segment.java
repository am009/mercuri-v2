package backend.ra;

// 表示 vreg 活跃区间（可能不连续）的一个连续小段
public class Segment {
    // start slot
    long start;
    // end slot
    long end;

    public Segment(long start, long end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }
}
