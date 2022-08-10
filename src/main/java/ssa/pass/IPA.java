package ssa.pass;

import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.Func;
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
        for(var func: module.funcs) {
            for(var block: func.bbs) {
                for(var inst: block.insts) {
                    this.analysisOnInst(func, block, inst);
                }
            }
        }
    }

    private void analysisOnInst(Func func, BasicBlock block, Instruction inst) {
        if(inst instanceof CallInst) {
            var callInst = (CallInst) inst;
            var target = callInst.target();
            func.callees.add(target);
            target.callers.add(func);
            return;
        }

        if(inst instanceof LoadInst) {
            var loadInst = (LoadInst) inst;
            var target = loadInst.getPtr();
            func.readVars.add(target);
            target.readers.add(func);
            return;
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
