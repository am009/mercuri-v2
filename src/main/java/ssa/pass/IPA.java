package ssa.pass;

import ssa.ds.StoreInst;
import ssa.ds.Value;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.Func;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.Module;

public class IPA {
    Module module;

    public IPA(Module module) {
        this.module = module;
    }

    public static void process(Module module) {
        var ipa = new IPA(module);
        ipa.executeInit();
        ipa.executeCallingAnalysis();
    }

    private void executeCallingAnalysis() {
        for (var func : module.funcs) {
            for (var block : func.bbs) {
                for (var inst : block.insts) {
                    this.analysisOnInst(func, block, inst);
                }
            }
        }

        for (var func : module.funcs) {
            if (func.hasSideEffect) {
                dfsSideEffect(func);
            }
            if (func.usingGlobs) {
                dfsUsedGlobalVariable(func);
            }
        }
    }

    private void dfsSideEffect(Func func) {
        for (var callerFunc : func.callers) {
            callerFunc.defGlobs.addAll(func.defGlobs);
            if (!callerFunc.hasSideEffect) {
                callerFunc.hasSideEffect = true;
                dfsSideEffect(callerFunc);
            }
        }
    }

    private void dfsUsedGlobalVariable(Func func) {
        for (var callerFunc : func.callers) {
            callerFunc.loadGlobs.addAll(func.loadGlobs);
            if (!callerFunc.usingGlobs) {
                callerFunc.usingGlobs = true;
                dfsUsedGlobalVariable(callerFunc);
            }
        }
    }

    private void analysisOnInst(Func func, BasicBlock block, Instruction inst) {
        if (inst instanceof CallInst) {
            var callInst = (CallInst) inst;
            var target = callInst.target();
            func.callees.add(target);
            target.callers.add(func);
            return;
        }

        if (inst instanceof LoadInst) {
            var loadInst = (LoadInst) inst;
            var addr = loadInst.getPtr();
            if (addr instanceof AllocaInst) {
                var allocaInst = (AllocaInst) addr;
                if (!allocaInst.type.isArray()) {
                    return;
                }
            }
            var ptr = PAA.getArrayValue(addr);
            if (ptr instanceof GlobalVariable) {
                func.usingGlobs = true;
                var glob = (GlobalVariable) ptr;
                if (!glob.isConst) {
                    func.loadGlobs.add(glob);
                }
            }
            return;
        }
        if (inst instanceof StoreInst) {
            var storeInst = (StoreInst) inst;
            var addr = storeInst.getPtr();
            if (addr instanceof AllocaInst) {
                var allocaInst = (AllocaInst) addr;
                if (!allocaInst.type.isArray()) {
                    return;
                }
            }
            var ptr = PAA.getArrayValue(addr);
            if (!(ptr instanceof GlobalVariable || PAA.isParam(ptr))) {
                return;
            }
            func.hasSideEffect = true;
            if (ptr instanceof GlobalVariable) {
                var glob = (GlobalVariable) ptr;
                if (!glob.isConst) {
                    func.defGlobs.add(glob);
                }
            }
        }

    }

    private void executeInit() {
        module.funcs.forEach(func -> {
            func.callers.clear();
            func.callees.clear();
            func.hasSideEffect = false;
            func.usingGlobs = false;
        });
        module.builtins.forEach(func -> {
            func.callers.clear();
            func.callees.clear();
            func.hasSideEffect = true;
            func.usingGlobs = true;
        });
    }

}
