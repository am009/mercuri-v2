package ssa.pass;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import common.Pair;
import ds.Global;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CallInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.PhiInst;
import ssa.ds.StoreInst;
import ssa.ds.Value;

// 《Global Code Motion Global Value Numbering》
public class GVN {
    Module ssaModule;

    public GVN(Module ssaModule) {
        this.ssaModule = ssaModule;
    }

    public static void process(Module ssaModule) {
        var gvn = new GVN(ssaModule);
        // 不要用 ssaModule.funcs.forEach
        var curSize = ssaModule.funcs.size();
        for (int i = 0; 0 <= i && i < curSize; i++) {
            var func = ssaModule.funcs.get(i);
            Global.logger.trace("GVN on " + func.name);
            // PAA.run(func);
            gvn.executeGVN(func);
            // 评测只管运行时间，不管exe大小，所以留不可达函数也没什么问题
            // PAA.clear(func);
            // var prevSize = curSize;
            // DCE.process(ssaModule);
            // curSize = ssaModule.funcs.size();
            // var deltaSize = curSize - prevSize;
            // assert (deltaSize <= 0);
            // i += deltaSize;
            // assert (i >= 0) : "Correctness of func emit ought to be reviewed";
        }
    }

    private void executeGVN(Func func) {
        var rpo = Simplifier.computeReversePostOrderBlockList(func);
        for (var bb : rpo) {
            this.excuteOnBB(bb);
            assert bb.hasValidTerm();
        }
    }

    private void excuteOnBB(BasicBlock bb) {
        var npred = bb.pred().size();
        // 这里要小心，如果我们在 i 的位置插入了新的指令（可能还不止一条），那么实际上要往后多走几步，不然会死循环
        var curSize = bb.insts.size();
        for (var i = 0; i >= 0 && i < curSize; i++) {
            var inst = bb.insts.get(i);
            this.executeOnInst(bb, inst);
            var prevSize = curSize;
            curSize = bb.insts.size();
            var deltaSize = curSize - prevSize;
            i += deltaSize;
            // deltaSize 很可能小于 0, 从而 i 可能反而要回退。因此每次循环要确保它 i >= 0
        }
    }

    private void executeOnInst(BasicBlock parent, Instruction inst) {
        if (inst.getUses().size() == 0 && !(inst instanceof StoreInst) && !(inst instanceof CallInst)) {
            return;
        }
        // step 1 化简constant result
        // 这里可能会返回一个值，或者一个优化后的指令。如果是指令，则可以认为在 simplify 内部完成了插入
        // 因此在这里不要对其进行重复插入，只是将依赖 inst 的指令的对 inst 的依赖，替换为对 simplifiedValue 的依赖
        // 这个替换通过 replaceIfDiffrent 完成
        var simplifiedValue = Simplifier.simplify(inst, true);
        // 如果已经化简为非指令，则原来的指令可以直接删掉
        if (!(simplifiedValue instanceof Instruction)) {
            this.replaceIfDiffrent(inst, simplifiedValue);
            return;
        }
        // 如果优化后仍然是指令
        Instruction simplifiedInst = (Instruction) simplifiedValue;
        // 优化后的指令和原来相同，不做处理
        if (simplifiedInst == inst) {
            return;
        }
        // step 2 化简指令本身
        // step 3 从map里取
        if (simplifiedInst instanceof BinopInst) {
            var binopInst = (BinopInst) simplifiedInst;

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
            var loadInst = (LoadInst) simplifiedInst;
            var pointer = loadInst.getPtr();
            Value array = PAA.getArrayValue(pointer);

            boolean getConst = false;
            if (pointer instanceof GetElementPtr && PAA.isGlobal(array)) {
                GlobalVariable globalArray = (GlobalVariable) array;
                if (globalArray.isConst) {
                    boolean constIndex = true;

                    // if (globalArray.fixedInit == null) {
                    //   // mark global const 产生的常量数组
                    //   ConstantInt c = ConstantInt.newOne(factory.getI32Ty(), 0);
                    //   replace(inst, c);
                    //   return;
                    // } else if (globalArray.fixedInit instanceof ConstantArray
                    //     && ((GetElementPtr) pointer).getNumOP() > 2) {
                    //   ConstantArray constantArray = (ConstantArray) globalArray.fixedInit;
                    //   Stack<Integer> indexList = new Stack<>();
                    //   Value tmpPtr = pointer;
                    //   while (tmpPtr instanceof GetElementPtr) {
                    //     // 不考虑基址+偏移的GEP
                    //     if (((GetElementPtr) tmpPtr).getNumOP() <= 2) {
                    //       constIndex = false;
                    //       break;
                    //     }
                    //     Value index = ((Instruction) tmpPtr).getOperands().get(2);
                    //     if (!(index instanceof ConstantInt)) {
                    //       constIndex = false;
                    //       break;
                    //     }
                    //     indexList.push(((ConstantInt) index).getVal());
                    //     tmpPtr = ((Instruction) tmpPtr).getOperands().get(0);
                    //   }
                    //   if (constIndex) {
                    //     Constant c = constantArray;
                    //     while (!indexList.isEmpty()) {
                    //       int index = indexList.pop();
                    //       c = ((ConstantArray) c).getConst_arr_().get(index);
                    //     }
                    //     assert c instanceof ConstantInt;
                    //     replace(inst, c);
                    //     getConst = true;
                    //   }
                    // }
                }
            }

            if (!getConst) {
                Value val = encache(simplifiedInst);
                this.replaceIfDiffrent(inst, val);
            }
            return;
        } // LoadInst 
        if (simplifiedInst instanceof PhiInst) {
            var phiInst = (PhiInst) simplifiedInst;
            boolean sameIncoming = true;
            Value val = encache(phiInst.preds.get(0).value);
            for (int i = 1; i < phiInst.preds.size() && sameIncoming; i++) {
                if (!val.equals(encache(phiInst.preds.get(i).value))) {
                    sameIncoming = false;
                }
            }
            if (sameIncoming) {
                // FIXME: 为啥是 phiInst，而不是 inst
                replaceIfDiffrent(phiInst, val);
            }
        } // PhiINst
        if (simplifiedInst instanceof StoreInst) {
            var val = inst.getOperand0();
            if (!val.type.isPointer) {
                valueTable.add(new Pair<>(inst, inst));
            }
            return;
        } // StoreInst
        if (simplifiedInst instanceof CallInst) {
            Value val = encache(simplifiedInst);
            replaceIfDiffrent(inst, val);
            return;
        } // CallInst
    }

    // 此处 replace 只负责将对 inst 的使用换成对 v 的使用，然后废弃 inst.
    // inst 的插入不由此处完成
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
        Global.logger.trace("replacing. nolonger use '" + inst + "', use '" + v + "' instead");
        inst.replaceAllUseWith(v);
        inst.removeAllOpr();
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
