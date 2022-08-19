package ssa.pass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ds.Global;
import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.Value;

public class FuncInline {
    Module m;
    int maxInlineDepth = -1;
    int maxInlineSize = -1;
    List<Func> inlinableFuncs = new ArrayList<>(); // funcs to be inlined
    Set<Func> recurFuncs = new HashSet<>(); // funcs that are recursive

    public class InlineContext {
        CallInst callInst;
        Func caller;
        Func callee;

        public InlineContext(CallInst callInst, Func caller, Func callee) {
            this.callInst = callInst;
            this.caller = caller;
            this.callee = callee;
        }

    }

    public FuncInline(Module m) {
        this.m = m;
    }

    public static void process(Module m) {
        var optimizer = new FuncInline(m);
        optimizer.init();
        optimizer.execute();
    }

    private void execute() {
        this.executeOnFunc(m.getMain());
    }

    private void executeOnFunc(Func main) {
        // var bbit = main.bbs.listIterator();
        // while (bbit.hasNext()) {
        //     var curBB = bbit.next();
        //     var it = curBB.insts.iterator();
        //     while (it.hasNext()) {
        //         var curInst = it.next();
        //         if (!isInlinableInst(curInst)) {
        //             Global.logger.trace("ignore non-inlinable inst: " + curInst);
        //         }
        //         // remove call
        //         it.remove();
        //         // split
        //         var callInst = (CallInst) curInst;
        //         var splitBB = curBB.splitAt(callInst, it);
        //         bbit.add(splitBB);
        //         splitBB.owner = main;
        //         // 处理 preds, succs
        //         for (var succ : curBB.succ()) {
        //             succ.pred().remove(curBB);
        //             succ.pred().add(splitBB);
        //             splitBB.succ().add(succ);
        //         }
        //         curBB.succ().clear();
        //         // 将函数的 bbs 复制进来
        //         var inlinedBBs = copyBBs(callInst);
        //     }
        // }
    }
    // private static int nextCloneId = 0;
    // // 进行了形参和实参的替换，新bb内的前驱和后继也有维护，bb没有设置parent
    // private List<BasicBlock> copyBBs(CallInst callInst) {
    //     var callee = callInst.target();
    //     var inlinedBBs = new ArrayList<>();
    //     var bbit = callee.bbs.listIterator();
    //     Map<Value,Value> mapOldNew = new HashMap<>();
    //     while (bbit.hasNext()) {
    //         var curBB = bbit.next();
    //         var newBB = new BasicBlock(curBB.label+"_clone_"+(nextCloneId++), null);
    //         mapOldNew.put(curBB.val, newBB.val);
    //         inlinedBBs.add(newBB);
    //         // 复制insts
    //         var it = curBB.insts.iterator();
    //         while (it.hasNext()) {
    //             var curInst = it.next();
    //             var newInst = curInst.clone();
    //             newBB.insts.add(newInst);
    //         }
    //         // 复制preds和succs
    //         for (var pred : curBB.pred()) {
    //             newBB.pred().add(pred);
    //         }
    //         for (var succ : curBB.succ()) {
    //             newBB.succ().add(succ);
    //         }
    //         // 复制phi
    //         var phiIt = curBB.phis.iterator();
    //         while (phiIt.hasNext()) {
    //             var curPhi = phiIt.next();
    //             var newPhi = new PhiInst(newBB);
    //             newBB.phis.add(newPhi);
    //             for (var opr : curPhi.oprands) {
    //                 newPhi.addOperand(opr.value, opr.parent);
    //             }
    //         }
    //         // 复制call
    //         if (curBB.call != null) {
    //             newBB.call = curBB.call.copy();
    //             newBB.call.parent = newBB;
    //         }
    //     }
    //     return inlinedBBs;
    // }

    private boolean isInlinableInst(Instruction curInst) {
        if (!(curInst instanceof CallInst)) {
            return false;
        }
        var callInst = (CallInst) curInst;
        var target = callInst.target();
        if (!inlinableFuncs.contains(target)) {
            return false;
        }
        if (recurFuncs.contains(target)) {
            assert false : "should be excluded from inlinableFuncs";
            return false;
        }
        if (target.preventInline) {
            assert false : "should be excluded from inlinableFuncs";
            return false;
        }
        return true;
    }

    private void init() {
        for (var func : m.funcs) {
            assert !func.bbs.isEmpty() : "func " + func.name + " has no bbs";
            if (func.name == "main") {
                func.preventInline = true;
            }
            if (func.callers.isEmpty()) {
                func.preventInline = true;
            }
            if (func.preventInline) {
                continue;
            }

            var path = new HashSet<Func>();
            var rec = dfsRecursiveFuncs(func, path);
            if (rec) {
                func.preventInline = true;
            }
            if (func.preventInline) {
                continue;
            }
            if (func.bbs.size() > 1) {
                inlinableFuncs.add(func);
            }
        }
    }

    // 模拟所有调用路径, 如果新调用的函数已经出现在路径上, 说明是递归调用
    private boolean dfsRecursiveFuncs(Func func, Set<Func> dfsPath) {
        dfsPath.add(func);
        for (var callee : func.callees) {
            if (dfsPath.contains(callee)) {
                recurFuncs.add(callee);
                return true;
            } else {
                return dfsRecursiveFuncs(callee, dfsPath);
            }
        }
        dfsPath.remove(func);
        return false;
    }

    private boolean inline(InlineContext ctx) {
        // 确保没有相互调用
        // if(callee.callers.contains(callee)){
        // return;
        // }
        assert (ctx.caller.bbs.size() > 0);
        var callerBB = ctx.callInst.parent;

        return true;
    }
}
