package ssa.pass;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import common.Pair;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CallInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.GetElementPtr;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.StoreInst;
import ssa.ds.Value;

public class GVN {
    Module ssaModule;

    public GVN(Module ssaModule) {
        this.ssaModule = ssaModule;
    }

    public static void process(Module ssaModule) {
        var gvn = new GVN(ssaModule);
        ssaModule.funcs.forEach(func -> gvn.execute(func));
    }

    private void execute(Func func) {
        var rpo = Simplifier.computeReversePostOrderBlockList(func);
        for (var bb : rpo) {
            this.excuteOnBB(bb);
            assert bb.hasValidTerm();
        }
    }

    private void excuteOnBB(BasicBlock bb) {
        var npred = bb.pred().size();
        for (var i = 0; i < bb.insts.size(); i++) {
            var inst = bb.insts.get(i);
            this.executeOnInst(bb, inst);
        }
    }

    private void executeOnInst(BasicBlock parent, Instruction inst) {
        assert parent == inst.parent;
        if (inst.getUses().size() == 0 && !(inst instanceof StoreInst) && !(inst instanceof CallInst)) {
            return;
        }
        var simplifiedValue = Simplifier.simplify(inst, true);
        // 如果已经化简为非指令，则原来的指令可以直接删掉
        if (!(simplifiedValue instanceof Instruction)) {
            this.replaceIfDiffrent(inst, simplifiedValue);
            return;
        }

        Instruction simplifiedInst = (Instruction) simplifiedValue;
        if(simplifiedInst == inst) {
            return;
        }
        if (simplifiedInst instanceof BinopInst) {
            var binopInst = (BinopInst) simplifiedInst;
            if (binopInst.op.isBoolean()) {
                // TODO: really need this?
                // return;
            }

            if (this.replaceIfDiffrent(inst, simplifiedInst)) {
                return;
            }

            // 替换依赖
            var val = encache(simplifiedInst);
            this.replaceIfDiffrent(inst, val);
        } // BinopInst
        if (simplifiedInst instanceof GetElementPtr) {
            var val = encache(simplifiedInst);
            replaceIfDiffrent(inst, val);
        } // GetElementPtr
        if (simplifiedInst instanceof LoadInst) {
            // TODO
            return;
        } // LoadInst 
        if (simplifiedInst instanceof StoreInst) {
            // TODO
            return;
        } // StoreInst
        if (simplifiedInst instanceof CallInst) {
            // TODO
            return;
        } // CallInst
    }

    private boolean replaceIfDiffrent(Instruction inst, Value v) {
        if (inst == v) {
            return false;
        }
        var size = valueTable.size();
        for (var i = 0; i < size; i++) {
            var kv = valueTable.get(i);
            if (kv.first == inst) {
                // remove
                valueTable.remove(i);
                i--;
            }
        }
        // 不对，这里不能单纯根据 v 是否是指令就进行替换。
        // 有时候，我们需要替换整个指令
        // 有时候，却是要替换指令的某个操作数，但这个操作数本身是一个指令？比如 lhs, rhs
        // 为了统一这两种情况，干脆把自己替换成 v
        inst.replaceAllUseWith(v);
        inst.parent.removeInst(inst);
        // if (v instanceof Instruction) {
        //     var newInst = (Instruction) v;
        //     inst.parent.replaceInst2(inst, newInst);
        //     ;
        // }else{
        //     inst.parent.removeInst(inst);
        // }

        return true;
    }

    private List<Pair<Value, Value>> valueTable = new LinkedList<>();

    private Value encache(Value val) {
        var size = valueTable.size();
        for (var i = 0; i < size; i++) {
            var kv = valueTable.get(i);
            if (kv.first == val) {
                return kv.second;
            }
            if (val instanceof ConstantValue && kv.first instanceof ConstantValue) {
                var cval = (ConstantValue) val;
                var ckey = (ConstantValue) kv.first;
                if (cval.equals(ckey)) {
                    return kv.second;
                }
            }
        } // end for
        valueTable.add(new Pair<>(val, val));
        var insAt = valueTable.size() - 1;
        if (val instanceof Instruction) {
            var inst = (Instruction) val;
            if (inst instanceof BinopInst || inst instanceof GetElementPtr || inst instanceof CallInst
                    || inst instanceof LoadInst) {
                valueTable.get(insAt).second = this.encacheInst(inst);
            }
        }
        return valueTable.get(insAt).second;
    }

    private Value encacheInst(Instruction inst) {
        if (inst instanceof LoadInst) {
            var loadInst = (LoadInst) inst;
            return encacheInst(loadInst);
        } else if (inst instanceof StoreInst) {
            var storeInst = (StoreInst) inst;
            return encacheInst(storeInst);
        } else if (inst instanceof GetElementPtr) {
            var getElementPtr = (GetElementPtr) inst;
            return encacheInst(getElementPtr);
        } else if (inst instanceof BinopInst) {
            var binopInst = (BinopInst) inst;
            return encacheInst(binopInst);
        } else if (inst instanceof CallInst) {
            var callInst = (CallInst) inst;
            return encacheInst(callInst);
        } else {
            assert false;
            return null;
        }
    }

    private Value encacheInst(LoadInst inst) {
        // for (var kv : valueTable) {
        //     var key = kv.first;
        //     var valNum = kv.second;
        //     // if key as a binary op, NOT equals to inst
        //     if (key instanceof LoadInst) {
        //         var kInst = (LoadInst) key;
        //         // 忽略重复的指令
        //         if (kInst.equals(inst)) {
        //             break;
        //         }
        //         // // TODO: 这里需要确保使用的地址没有被定值点重定义
        //         // var allsame = encache(inst.getPtr() )== encache(kInst.getPtr());

        //         // for (var i = 0; i < inst.oprands.size(); i++) {
        //         //     if (!encache(inst.oprands.get(i).value).equals(encache(kInst.oprands.get(i).value))) {
        //         //         return inst;
        //         //     }
        //         // }
        //     }
        //     if (key instanceof StoreInst) {
        //         var kInst = (StoreInst) key;
        //         if (kInst.equals(inst)) {
        //             break;
        //         }
        //     }
        // } // end for
        return inst;
    }

    private Value encacheInst(GetElementPtr inst) {

        var size = valueTable.size();
        for (var tableEntryIdx = 0; tableEntryIdx < size; tableEntryIdx++) {
            var kv = valueTable.get(tableEntryIdx);
            var key = kv.first;
            var valNum = kv.second;
            // if key as a binary op, NOT equals to inst
            if (!(key instanceof GetElementPtr)) {
                continue;
            }
            var kInst = (GetElementPtr) key;
            if (inst.equals(kInst)) {
                break;
            }
            if (inst.oprands.size() != kInst.oprands.size()) {
                continue;
            }
            for (var i = 0; i < inst.oprands.size(); i++) {
                if (!encache(inst.oprands.get(i).value).equals(encache(kInst.oprands.get(i).value))) {
                    return inst;
                }
            }
            return valNum;
        } // end for
        return inst;
    }

    private Value encacheInst(BinopInst inst) {
        var lhs = inst.getOperand0();
        var rhs = inst.getOperand1();
        var size = valueTable.size();
        for (var i = 0; i < size; i++) {
            var kv = valueTable.get(i);
            var key = kv.first;
            var valNum = kv.second;
            // if key as a binary op, NOT equals to inst
            if (key instanceof BinopInst) {
                var kInst = (BinopInst) key;
                var klhs = encache(kInst.getOperand0());
                var krhs = encache(kInst.getOperand1());

                boolean sameOp = kInst.op.equals(inst.op);
                boolean sameOpr = klhs.equals(lhs) && krhs.equals(rhs)
                        || (inst.op.isCommutative() && klhs.equals(rhs) && krhs.equals(lhs));
                boolean sameReverse = inst.op.isReverse(kInst.op) && klhs.equals(rhs) && krhs.equals(lhs);
                if (sameOp && (sameOpr || sameReverse)) {
                    return valNum;
                }
            } // end if
        } // end for
        return inst;
    } // end encacheInst(BinopInst)

    private Value encacheInst(CallInst inst) {
        return inst;
        // TODO
    }

}
