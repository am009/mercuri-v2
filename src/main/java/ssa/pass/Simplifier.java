package ssa.pass;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import backend.arm.inst.BinOpInst;
import common.ListUtil;
import ds.Global;
import dst.ds.BinaryOp;
import dst.ds.EvaluatedValue;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CallInst;
import ssa.ds.CastInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.StoreInst;
import ssa.ds.TerminatorInst;
import ssa.ds.Value;

public class Simplifier {
    public static LinkedList<BasicBlock> computeReversePostOrderBlockList(Func func) {

        var visited = new HashMap<BasicBlock, Boolean>(func.bbs.size());

        var ret = new LinkedList<BasicBlock>();
        var stack = new Stack<BasicBlock>();
        var entry = func.bbs.get(0);
        for (var block : func.bbs) {
            visited.put(block, false);
        }
        stack.push(entry);
        var curr = entry;

        while (!stack.empty()) {
            curr = stack.pop();
            ret.add(curr);
            for (var child : curr.succ()) {
                if (!visited.get(child)) {
                    visited.put(child, true);
                    stack.push(child);
                }
            }
        }

        return ret;
    }

    /**
     * inst 是被简化的指令
     * 如果生成了新的优化指令，此函数会负责插入到 inst 的后边，最后外面调用此函数方，应该删除 inst
     * 
     * 疑问：ayame 全部插入到块尾部，然后依赖它的指令也被追加到末尾，原来的旧指令被删除（DCE）
     * 看起来依赖它的优化指令追加到末尾了，但是依赖这个优化指令的其它指令还在前面，导致其它指令依赖了末尾的指令，也即使用了未定义的值
     * 不知道为啥 ayame 能避开这个问题
     */
    public static Value simplify(Instruction inst, Boolean rec) {
        Global.logger.trace("simplify: " + inst);
        if (inst instanceof BinopInst) {
            var binop = (BinopInst) inst;
            var simplified = switch (binop.op) {
                case ADD -> simplifyAdd(binop, binop, rec);
                case SUB -> simplifySub(binop, binop, rec);
                case MUL -> simplifyMul(binop, binop, rec);
                case DIV -> simplifyDiv(binop, binop, rec);
                case MOD -> simplifyMod(binop, binop, rec);
                case LOG_AND -> simplifyLogAnd(binop, binop, rec);
                case LOG_OR -> simplifyLogOr(binop, binop, rec);
                case LOG_EQ -> simplifyLogEq(binop, binop, rec);
                case LOG_NEQ -> simplifyLogNeq(binop, binop, rec);
                case LOG_LT -> simplifyLogLt(binop, binop, rec);
                case LOG_GT -> simplifyLogGt(binop, binop, rec);
                case LOG_LE -> simplifyLogLe(binop, binop, rec);
                case LOG_GE -> simplifyLogGe(binop, binop, rec);
                default -> {
                    throw new RuntimeException("unsupported binop: " + binop.op);
                }
            };
            if (simplified instanceof Instruction && simplified != inst) {
                Global.logger.trace("insert simplified instruction: from '" + inst + "' to '" + simplified + "'");
                //  注意，这里可能简化后变成一条已经存在的指令
                // 比如说 %? = add i32 %0, 0
                // 简化后直接变成 lhs 的 load 指令
                insertInstIfNeeded(inst, (Instruction) simplified);
            }
            return simplified;
        } else {
            return inst;
        }
    }

    public static Value simplifyAdd(Instruction old, BinopInst inst, Boolean rec) {
        assert (inst.op == BinaryOp.ADD);
        insertInstIfNeeded(old, inst);

        var lhs = inst.getOperand0();
        var rhs = inst.getOperand1();
        if (lhs instanceof GlobalVariable) {
            lhs = ((GlobalVariable) lhs).init;
        }
        if (rhs instanceof GlobalVariable) {
            rhs = ((GlobalVariable) rhs).init;
        }
        Value c = foldConstant(lhs, rhs, inst.op);
        if (c != null) {
            return c;
        }

        // Const + value -> value + Const
        if (lhs instanceof ConstantValue) {
            inst.removeAllOpr();
            inst.addOprand(rhs);
            inst.addOprand(lhs);

            lhs = inst.getOperand0();
            rhs = inst.getOperand1();
        }

        // lhs + 0 -> lhs
        if (rhs instanceof ConstantValue) {
            var cv = (ConstantValue) rhs;
            if (cv.val.equals(0)) {
                return lhs;
            }
        }

        // lhs + rhs == 0
        // 1. lhs = sub(0, rhs) or rhs = sub(0, lhs)
        // 2. lhs = sub(a, b) and rhs = sub(b, a)
        if (lhs instanceof BinopInst && rhs instanceof BinopInst) {
            var lhsInst = (BinopInst) lhs;
            var rhsInst = (BinopInst) rhs;

            if (lhsInst.op == BinaryOp.SUB && rhsInst.op == BinaryOp.SUB) {
                var ll = lhsInst.getOperand0();
                var lr = lhsInst.getOperand1();
                var rl = rhsInst.getOperand0();
                var rr = rhsInst.getOperand1();
                // ll == 0
                if (ll instanceof ConstantValue && ((ConstantValue) ll).val.equals(0)) {
                    if (lr.equals(rhs)) {
                        return ConstantValue.ofInt(0);
                    }
                } else if (rl instanceof ConstantValue && ((ConstantValue) rl).val.equals(0)) {
                    if (rr.equals(lhs)) {
                        return ConstantValue.ofInt(0);
                    }
                }
            }
        }

        if (!rec)
            return inst;

        // sub on the right
        if (rhs instanceof BinopInst) {
            var rhsInst = (BinopInst) rhs;
            // lhs + (Y - lhs)
            if (rhsInst.op == BinaryOp.SUB) {
                if (lhs == rhsInst.getOperand1()) {
                    return rhsInst.getOperand0();
                }

                // X + (Y - Z) -> (X + Y) - Z or (X - Z) + y

                // try -- X + (Y - Z) -> (X + Y) - Z
                var subLhs = rhsInst.getOperand0();
                var subRhs = rhsInst.getOperand1();
                var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, lhs, subLhs);
                var simplifiledAddInst = simplifyAdd(old, tmpAddInst, false);
                if (simplifiledAddInst != tmpAddInst) {
                    return simplify(new BinopInst(inst.parent, BinaryOp.SUB, simplifiledAddInst, subRhs), false);
                }

                // try --  X + (Y - Z) ->  (X - Z) + y
                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhs, subRhs);
                var simplifiledSubInst = simplifySub(old, tmpSubInst, false);
                if (simplifiledSubInst != tmpSubInst) {
                    return simplify(new BinopInst(inst.parent, BinaryOp.ADD, simplifiledSubInst, subLhs), false);
                }
            }
        }

        // sub on the left

        if (lhs instanceof BinopInst) {
            var lhsInst = (BinopInst) lhs;
            if (lhsInst.op == BinaryOp.SUB) {
                // (Y - X )+ X -> Y
                if (lhsInst.getOperand1() == rhs) {
                    return lhsInst.getOperand0();
                }

                // (X - Y) + Z -> (X + Z) - Y
                var subLhs = lhsInst.getOperand0();
                var subRhs = lhsInst.getOperand1();

                var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, subLhs, rhs);
                var simplifiledAddInst = simplifyAdd(old, tmpAddInst, false);
                if (simplifiledAddInst != tmpAddInst) {
                    return simplify(new BinopInst(inst.parent, BinaryOp.SUB, simplifiledAddInst, subRhs), false);
                }
                // (X - Y) + Z -> (Z - Y ) + X

                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, subRhs, rhs);
                var simplifiledSubInst = simplifySub(old, tmpSubInst, false);
                if (simplifiledSubInst != tmpSubInst) {
                    return simplify(new BinopInst(inst.parent, BinaryOp.ADD, simplifiledSubInst, subLhs), false);
                }
            }
        }

        // (X + Y) + Z -> X + (Y + Z)
        if (lhs instanceof BinopInst) {
            var lhsInst = (BinopInst) lhs;
            if (lhsInst.op == BinaryOp.ADD) {
                // ll and r can be simplified
                var lhsLhs = lhsInst.getOperand0();
                var lhsRhs = lhsInst.getOperand1();
                var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, lhsLhs, rhs);
                var simplifiledAddInst = simplify(tmpAddInst, false);
                if (simplifiledAddInst != tmpAddInst) {
                    // (ll + r ) + lr
                    return simplify(new BinopInst(inst.parent, BinaryOp.ADD, simplifiledAddInst, lhsRhs), false);
                }

                // lr and r can be simplified
                var tmpAddInst2 = new BinopInst(inst.parent, BinaryOp.ADD, lhsRhs, rhs);
                var simplifiledAddInst2 = simplify(tmpAddInst2, false);
                if (simplifiledAddInst2 != tmpAddInst2) {
                    // ll + (lr + r)
                    return simplify(new BinopInst(inst.parent, BinaryOp.ADD, lhsLhs, simplifiledAddInst2), false);
                }
            }
        }

        return inst;
    }

    // inst 有可能是生成的优化指令，还未来得及插入到BasicBlock中，因此在此检查是否需要插入
    // old 用于指出应该插入到哪个位置之前
    private static void insertInstIfNeeded(Instruction old, Instruction inst) {
        if(old == inst) {
            return;
        }
        assert (inst.parent != null);
        var parent = inst.parent;
        if (parent.insts.contains(inst)) {
            return;
        }
        Global.logger.trace("insert new inst " + inst);
        var index = parent.insts.indexOf(old);
        parent.insts.add(index, inst);
        // parent.addBeforeTerminator(inst);

    }

    private static Value foldConstant(Value lhs, Value rhs, BinaryOp op) {
        if (lhs instanceof ConstantValue) {
            var clhs = (ConstantValue) lhs;
            if (rhs instanceof ConstantValue) {
                var crhs = (ConstantValue) rhs;
                var evald = EvaluatedValue.fromOperation(clhs.toEvaluatedValue(), crhs.toEvaluatedValue(), op);
                return ConstantValue.of(evald);
            }
        }
        return null;

    }

    public static Value simplifySub(Instruction old, BinopInst inst, Boolean rec) {

        assert (inst.op == BinaryOp.SUB);
        insertInstIfNeeded(old, inst);

        var lhs = inst.getOperand0();
        var rhs = inst.getOperand1();

        if (lhs instanceof GlobalVariable) {
            lhs = ((GlobalVariable) lhs).init;
        }
        if (rhs instanceof GlobalVariable) {
            rhs = ((GlobalVariable) rhs).init;
        }

        Value c = foldConstant(lhs, rhs, inst.op);
        if (c != null) {
            return c;
        }

        // lhs - 0 -> lhs
        if (rhs instanceof ConstantValue) {
            var crhs = (ConstantValue) rhs;
            if (crhs.val.equals(0)) {
                return lhs;
            }
        }

        // lhs - rhs == 0
        // FIXME: 这里如果两个都是 load 指令，且 load 相同的数，实际上仍然不能被优化，因为两个指令的编号不同
        // 二者虽然都是 load，可能其中一个中途被 redef
        if (lhs.equals(rhs)) {
            return ConstantValue.ofInt(0);
        }

        // lhs - Const -> lhs + (-Const)
        // 真的有必要？
        // if (rhs instanceof ConstantValue) {
        //     var crhs = (ConstantValue) rhs;
        //     var cval = crhs.val;
        //     if (cval instanceof Integer) {
        //         var cint = (Integer) cval;
        //         return new BinopInst(inst.parent, BinaryOp.ADD, lhs, ConstantValue.ofInt(-cint));
        //     }
        //     if (cval instanceof Float) {
        //         var cFloat = (Float) cval;
        //         return new BinopInst(inst.parent, BinaryOp.ADD, lhs, ConstantValue.ofFloat(-cFloat));
        //     }

        // }

        if (!rec)
            return inst;

        if (lhs instanceof BinopInst) {
            var lhsBinop = (BinopInst) lhs;
            if (lhsBinop.op == BinaryOp.ADD) {
                // (X + Y) - Z -> X + (Y - Z) or Y + (X - Z)
                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhsBinop.getOperand0(), rhs); // X - Z
                var tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    return simplifyAdd(old,
                            new BinopInst(inst.parent, BinaryOp.ADD, lhsBinop.getOperand1(), tmpSimple), false);
                }

                tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhsBinop.getOperand1(), rhs); // Y - Z
                tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    return simplifyAdd(old,
                            new BinopInst(inst.parent, BinaryOp.ADD, lhsBinop.getOperand0(), tmpSimple), false);
                }

            } else if (lhsBinop.op == BinaryOp.SUB) {
                // (X - Y) - Z -> (X - Z) - Y or X - (Y + Z)
                var ll = lhsBinop.getOperand0(); // X
                var lr = lhsBinop.getOperand1(); // Y
                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, ll, rhs); // X-Z
                var tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    // X - Z - Y
                    return simplifySub(old, new BinopInst(inst.parent, BinaryOp.SUB, tmpSimple, lr), false);
                }

                var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, lr, rhs); // Y + Z
                tmpSimple = simplifyAdd(old, tmpAddInst, false);
                if (tmpSimple != tmpAddInst) {
                    // X - (Y + Z)
                    return simplifySub(old, new BinopInst(inst.parent, BinaryOp.SUB, ll, tmpAddInst), false);
                }
            }
        }

        if (rhs instanceof BinopInst) {
            var rhsBinop = (BinopInst) rhs;
            if (rhsBinop.op == BinaryOp.ADD) {
                //  X - (Y + Z) -> (X - Y) - Z or (X - Z) - Y 
                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhs, rhsBinop.getOperand0());
                var tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    return simplifySub(old,
                            new BinopInst(inst.parent, BinaryOp.SUB, tmpSimple, rhsBinop.getOperand1()), false);
                }

                tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhs, rhsBinop.getOperand1());
                tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    return simplifySub(old,
                            new BinopInst(inst.parent, BinaryOp.SUB, tmpSimple, rhsBinop.getOperand0()), false);
                }

            } else if (rhsBinop.op == BinaryOp.SUB) {
                //  lhs  rl  rr    lhs  rl  rr    lhs  rr   rl
                //  Z - (X - Y) -> (Z - X) + Y or (Z + Y) - X
                var rl = rhsBinop.getOperand0(); // X
                var rr = rhsBinop.getOperand1(); // Y
                var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhs, rl); // Z - X
                var tmpSimple = simplifySub(old, tmpSubInst, false);
                if (tmpSimple != tmpSubInst) {
                    // X - Z - Y
                    return simplifyAdd(old, new BinopInst(inst.parent, BinaryOp.ADD, tmpSimple, rr), false);
                }

                var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, lhs, rr); // Z + Y
                tmpSimple = simplifyAdd(old, tmpAddInst, false);
                if (tmpSimple != tmpAddInst) {
                    // X - (Y + Z)
                    return simplifySub(old, new BinopInst(inst.parent, BinaryOp.SUB, tmpAddInst, rl), false);
                }
            }
        }

        return inst;
    }

    public static Value simplifyMul(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.MUL;
        insertInstIfNeeded(old, inst);

        return inst;
    }

    public static Value simplifyDiv(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.DIV;
        insertInstIfNeeded(old, inst);

        return inst;
    }

    public static Value simplifyMod(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.MOD;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogAnd(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_AND;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogOr(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_OR;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogEq(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_EQ;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogNeq(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_NEQ;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogLt(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_LT;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogGt(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_GT;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogLe(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_LE;
        insertInstIfNeeded(old, inst);

        return inst;

    }

    public static Value simplifyLogGe(Instruction old, BinopInst inst, Boolean rec) {
        assert inst.op == BinaryOp.LOG_GE;
        insertInstIfNeeded(old, inst);

        return inst;

    }
}
