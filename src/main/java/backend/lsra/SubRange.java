package backend.lsra;


// 表示 vreg 活跃区间（可能不连续）的一个连续小段
public class SubRange {
    // start slot
    long start;
    // end slot
    long end;

	/**
	 * A link to allow the range to be put into a singly linked list.
	 */
	public SubRange next;

    public SubRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }

    public boolean covers(long position) {
        return start <= position && position <= end;
    }
}
