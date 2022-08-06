package backend.ra;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.print.attribute.SetOfIntegerSyntax;

import backend.VirtReg;
import ds.Global;

public class LiveRange {

    VirtReg owner;

    // intervals 是 Segment 按开始端点排序的有序列表
    List<Segment> segments = new ArrayList<>();

    public LiveRange(VirtReg owner) {
        this.owner = owner;
        Global.logger.trace("create LiveRange for " + owner.toString());
    }

    @Override
    public String toString() {
        var sbuf = new StringBuffer();
        for (Segment segment : segments) {
            sbuf.append("[");
            sbuf.append(segment.start);
            sbuf.append(", ");
            sbuf.append(segment.end);
            sbuf.append("] ");
        }
        return sbuf.toString();
    }

    public void extend(long start, long end) {
        Global.logger.trace("extend LiveRange for " + owner.toString() + " with args: [" + start + "," + end + "]");
        Global.logger.trace("before " + toString());

        assert (start <= end);
        Segment newSeg = new Segment(start, end);
        int i = findInsertPos(start);
        segments.add(i, newSeg);
        merge();

        Global.logger.trace("after " + toString());
    }

    // 由于有时候发现定值点，需要截断，但区间尚未建立起来，因此就先放到 todo 列表，最后再处理。
    Set<Long> todo = new HashSet<>();

    public void handleTodoBreaks() {
        Global.logger.trace("handle todo breaks for " + owner.toString());
        Global.logger.trace("before " + toString());
        while (!todo.isEmpty()) {
            var breakPoint = todo.iterator().next();
            var done = this.disconnect(breakPoint);
            if (done) {
            } else {
                Global.logger.warning("breakpoint " + breakPoint + " is not done");
            }

            // remove 
            todo.remove(breakPoint);
        }
        Global.logger.trace("after " + toString());
    }

    // 将被定值的变量的range在此处截断;
    public boolean disconnect(long breakIndex) {
        Global.logger.trace("break LiveRange for " + owner.toString() + " with args: " + breakIndex);
        Global.logger.trace("before " + toString());
        // try 只是为了在函数结束时打日志
        try {
            if (segments.size() == 0) {
                todo.add(breakIndex);
                Global.logger.trace("not break because no segs");
                return false;
            }
            
            var existing = getLiveSegmentAt(breakIndex);
            if (existing == null) {
                todo.add(breakIndex);
                Global.logger.trace("not break because no existing");
                return false;
            }
            var existingIdx = segments.indexOf(existing);            
            Global.logger.trace("found existing: " + existing + " at index: " + existingIdx);
            if (existing.start == breakIndex) {
                // <------existing-------->
                // ^ cut, define here
                todo.add(breakIndex);
                return false;

            } else if (existing.end == breakIndex) {
                // <------existing-------->
                //                        ^ def here
                existing.end = breakIndex - 1;
            } else if (existing.start > breakIndex) {
                //      <------existing-------->
                //  ^ def here
                var newSeg = new Segment(breakIndex, breakIndex);
                var insPos = findInsertPos(breakIndex);
                segments.add(insPos, newSeg);
                Global.logger.trace("add new seg: " + newSeg + " at index: " + insPos);
                return true;
            } else {
                // <------existing-------->
                //                 ^ def here (break)
                // becomes
                // <---existing---><def--->
                if (!(existing.start < breakIndex && breakIndex < existing.end)) {
                    assert (false);
                }
                var oldEnd = existing.end;
                existing.end = breakIndex - 1;
                var newSeg = new Segment(breakIndex, oldEnd);
                var insPos = findInsertPos(breakIndex);
                segments.add(insPos, newSeg);
                Global.logger.trace("add new seg: " + newSeg + " at index: " + insPos);
                assert (newSeg.start <= newSeg.end);
            }
            // 最后检查一下 existing 是否因为截断而被删除了
            if (existing.start > existing.end) {
                Global.logger.trace("remove existing seg because nolonger valid: " + existing);
                segments.remove(existingIdx);

            }
        } catch (Exception e) {
            assert (false);
        } finally {
            Global.logger.trace("after " + toString());
        }
        return true;
    }

    public int findInsertPos(long slot) {
        int l = 0, r = segments.size();
        while (l < r) {
            int mid = (r - l) / 2 + l;
            var val = segments.get(mid).start;
            if (val == slot) {
                return mid;
            } else if (val < slot) {
                l = mid + 1;
            } else if (val > slot) {
                r = mid;
            }
        }
        return r;
    }

    public int findSegAt(long slot) {
        int l = 0, r = segments.size();
        while (l < r) {
            int mid = (r - l) / 2 + l;
            var start = segments.get(mid).start;
            var end = segments.get(mid).end;
            if (start <= slot && slot <= end) {
                return mid;
            } else if (start > slot) {
                r = mid;
            } else if (end < slot) {
                l = mid + 1;
            }
        }
        return l;
    }

    public void merge() {
        for (int i = 0; i < segments.size() - 1; i++) {
            // assert sorted 
            if (!(segments.get(i).start <= segments.get(i + 1).start)) {
                Global.logger.error("LiveRange.merge: unsorted segments");
                assert (false);
            }
            if (!(segments.get(i).start <= segments.get(i).end)) {
                Global.logger.error("LiveRange.merge: invalid segment");
                assert (false);
            }
            if (segments.get(i).end >= segments.get(i + 1).start) {
                segments.get(i).end = Math.max(segments.get(i).end, segments.get(i + 1).end);
                segments.remove(i + 1);
                i--;
            }
        }
    }

    public Boolean isLiveAt(long index) {
        assert (index >= 0);
        int i = findSegAt(index);
        if (i < segments.size() && segments.get(i).start <= index && segments.get(i).end >= index) {
            return true;
        }
        return false;
    }

    public Segment getLiveSegmentAt(long index) {
        assert (index >= 0);
        int i = findSegAt(index);
        if (i < segments.size()) {
            if (segments.get(i).start <= index && index <= segments.get(i).end) {
                return segments.get(i);
            } else {
                ;
            }
        }
        return null;
    }

    public void extend(long slot) {
        extend(slot, slot);
    }

}
