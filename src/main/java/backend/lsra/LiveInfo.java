package backend.lsra;

import java.util.HashSet;
import java.util.Set;

import backend.AsmBlock;
import backend.AsmOperand;
import backend.VirtReg;

public class LiveInfo {
    // 本 LiveInfo 所有者
    // 暂未使用，为 null
    public AsmBlock block;

    public Set<VirtReg> liveUse = new HashSet<>(); // 本块使用过的值（注意：如果一个值在本 Block 被重定义，则会出现在 LiveDef, 如果这个值在定义前就在本块被使用，则依然会存计入 liveUse 集合）
    public Set<VirtReg> liveDef = new HashSet<>(); // 本块定义过的值
    // LiveIn，入口活跃，即从入口处出发的任意路径上能够出现一个use（在定值之前被使用），那么就是入口活跃的。
    public Set<VirtReg> liveIn = new HashSet<>();
    // LiveOut，出口活跃，如果从 BB 出口出发的任意路径上能够出现一个 use（在定值之前被使用），那么就是出口活跃的。
    public Set<VirtReg> liveOut = new HashSet<>();

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("liveUse: ");
        for (var reg : liveUse) {
            sb.append(reg.toString() + " ");
        }
        sb.append("\n");
        sb.append("liveDef: ");
        for (var reg : liveDef) {
            sb.append(reg.toString() + " ");
        }
        sb.append("\n");
        sb.append("liveIn: ");
        for (var reg : liveIn) {
            sb.append(reg.toString() + " ");
        }
        sb.append("\n");
        sb.append("liveOut: ");
        for (var reg : liveOut) {
            sb.append(reg.toString() + " ");
        }
        sb.append("\n");
        return sb.toString();
    }
}
