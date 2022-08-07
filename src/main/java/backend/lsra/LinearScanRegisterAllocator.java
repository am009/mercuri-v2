package backend.lsra;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import backend.AsmModule;

public class LinearScanRegisterAllocator {
    DagLinearFlow flow;
    AsmModule m;

    public LinearScanRegisterAllocator(AsmModule m, DagLinearFlow flow) {
        this.m = m;
        this.flow = flow;
    }

    public static AsmModule process(AsmModule m) {

        var flow = LiveIntervalAnalyzer.process(m);
        var g = new LinearScanRegisterAllocator(m, flow);
        g.doAnalysis();
        return m;
    }

    public class RegSet {
        int totalCount;
        int nextFree;
        Boolean[] useMask;

    }

    private void doAnalysis() {
        unhandled = flow.liveIntervals.entrySet().stream().map(x->x.getValue()).collect(Collectors.toSet());
        for (var kv : flow.liveIntervals.entrySet()) {

        }

        // for(var func : m.funcs) {
        //     for(var block: func.bbs){
        //         for(var inst : block.insts) {
                   
        //         }
        //     }
        // }
    }
    Set<LiveRange> unhandled;
    Set<LiveRange> active = new HashSet<LiveRange>();
    Set<LiveRange> inactive = new HashSet<LiveRange>();
    Set<LiveRange> handled = new HashSet<LiveRange>();
    private void walkIntervals(){
       

        while(!unhandled.isEmpty()) {
            var current = unhandled.iterator().next();
            unhandled.remove(current);

            var position = current.first().start;

            // check for intervals in active that are expired or inactive
            for(var it : active) {
                if(it.last().end < position){
                    active.remove(it);
                    handled.add(it);
                }else if (!it.covers(position)){
                    active.remove(it);
                    inactive.add(it);
                }
            }
            // check for intervals in inactive that are expired or active
            for(var it : inactive) {
                if(it.last().end < position){
                    inactive.remove(it);
                    handled.add(it);
                }else if (it.covers(position)){
                    inactive.remove(it);
                    active.add(it);
                }
            }

            // find a free register for current
            var result = tryAllocFreeReg(current);
            // if(null == result) {
            //     allocBlockedReg(current);
            // }
            // if(isAssigned(current)){
            //     active.add(current);
            // }
        }
    }

    Map <LiveRange, Integer> It2freePos = new HashMap<LiveRange, Integer>();
    // 尝试寻找一个空闲的寄存器，这样不用 spill 操作
    Object tryAllocFreeReg(LiveRange current){
        var freePos = -1;
        for(var it : active) {
            // It2freePos.put(It2freePos, 0);
        }
        return null;
    }

    // 通过 spill 方式抢占一个寄存器
    void allocBlockedReg(){

    }
    
    private void resolveDataFlow() {
        
    //   for(var func : m.funcs) {
        //   for(var block: func.bbs){
            //   for(var inst : block.insts) {
            //   
            //   }
        //   }
    //   }
    }
}
