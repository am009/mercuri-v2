package ssa.pass;

import java.util.HashSet;
import java.util.Set;

import ds.Global;
import ssa.ds.CallInst;
import ssa.ds.StoreInst;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.TerminatorInst;

public class DCE {
    Module ssaModule;
    Set<Instruction> usefulInstSet = new HashSet<>();

    public DCE(Module ssaModule) {
        this.ssaModule = ssaModule;
    }

    public static void process(Module ssaModule) {
        var dce = new DCE(ssaModule);
        dce.execute();
    }

    private void execute() {
        var funcs = ssaModule.funcs;
        funcs.forEach(func -> this.executeOnFunc(func));
        var it = funcs.iterator();
        while (it.hasNext()) {
            var func = it.next();
            if (func.name.equals("main")) {
                continue;
            }
            if (func.callers.isEmpty()) {
                Global.logger.trace("remove func without caller: " + func.name);
                it.remove();
            }
        }
    }

    private void executeOnFunc(Func func) {
        removeUselessStore(func);
        removeUselessInst(func);
    }

    public boolean isUseful(Instruction inst) {
        if (inst instanceof TerminatorInst || inst instanceof StoreInst) {
            return true;
        }
        if (inst instanceof CallInst) {
            var callInst = (CallInst) inst;
            var target = callInst.target();
            // TODO: 这儿是 BUG，
            if (target.hasSideEffect) {
                return true;
            }
        }
        return false;
    }

    public void findUsefulClosure(Instruction inst) {
        if (usefulInstSet.contains(inst)) {
            return;
        }
        usefulInstSet.add(inst);
        for (var use : inst.oprands) {
            var op = use.value;
            if (op instanceof Instruction) {
                findUsefulClosure((Instruction) op);
            }
        }
    }

    public void removeUselessInst(Func func) {

        usefulInstSet.clear();
        for (var bb : func.bbs) {
            var it = bb.insts.iterator();
            while (it.hasNext()) {
                var inst = it.next();
                if (isUseful(inst)) {
                    findUsefulClosure(inst);
                }
            }
        }

        for (var bb : func.bbs) {
            var it = bb.insts.iterator();
            while (it.hasNext()) {
                var inst = it.next();
                if (!usefulInstSet.contains(inst)) {
                    Global.logger.trace("remove not used inst: " + "'" + inst + "'");
                    bb.removeInstWithIterator(inst, it);
                }
            }
        }
    }

    public void removeUselessStore(Func func) {
        if (true)
            return;
        for (var bb : func.bbs) {
            var it = bb.insts.iterator();
            while (it.hasNext()) {
                var inst = it.next();
                if (inst instanceof StoreInst) {
                    var storeInst = (StoreInst) inst;
                    var pointer = PAA.getArrayValue(storeInst.getPtr());
                    while (it.hasNext()) {
                        var nextInst = it.next();
                        // 如果 store 和下一个 store 重复，就删了当前的
                        if (nextInst instanceof StoreInst) {
                            var nextStoreInst = (StoreInst) nextInst;
                            if (storeInst.getPtr() == nextStoreInst.getPtr()) {
                                bb.removeInstWithIterator(storeInst, it);
                                break;
                            }
                        }
                        // 如果后面有 load，且 load 的指针和 store 的相同，则跳出循环
                        else if (nextInst instanceof LoadInst) {
                            var nextLoadInst = (LoadInst) nextInst;
                            var addr = nextLoadInst.getPtr();
                            var npointer = PAA.getArrayValue(addr);
                            if (PAA.alias(pointer, npointer)) {
                                break;
                            }
                        }
                        // 如果后面有 call，且 arr 和 call 的 GEP 的 arr 相同，则 break
                        else if (nextInst instanceof CallInst) {
                            if (PAA.callAlias(pointer, (CallInst) nextInst)) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
