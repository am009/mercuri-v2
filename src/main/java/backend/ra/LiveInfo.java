package backend.ra;

import java.util.HashSet;
import java.util.Set;

import backend.AsmBlock;
import backend.AsmOperand;
import backend.VirtReg;

public class LiveInfo {
    // 本 LiveInfo 所有者
    AsmBlock block;

    Set<VirtReg> liveUse = new HashSet<>(); // 本块使用过的值（注意：如果一个值在本 Block 被重定义，则会出现在 LiveDef, 如果这个值在定义前就在本块被使用，则依然会存计入 liveUse 集合）
    Set<VirtReg> liveDef = new HashSet<>(); // 本块定义过的值
    // LiveIn，入口活跃，即从入口处出发的任意路径上能够出现一个use（在定值之前被使用），那么就是入口活跃的。
    Set<VirtReg> liveIn = new HashSet<>();
    // LiveOut，出口活跃，如果从 BB 出口出发的任意路径上能够出现一个 use（在定值之前被使用），那么就是出口活跃的。
    Set<VirtReg> liveOut = new HashSet<>();

 

}
