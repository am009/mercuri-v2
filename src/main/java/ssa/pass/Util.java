package ssa.pass;

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

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

public class Util {
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

    public static Value simplify(Instruction inst, Boolean rec) {
        Global.logger.trace("simplify: " + inst);
        if (inst instanceof BinopInst) {
            var binop = (BinopInst) inst;
            return switch (binop.op) {
                case ADD -> simplifyAdd(binop, rec);
                case SUB -> simplifySub(binop, rec);
                case MUL -> simplifyMul(binop, rec);
                case DIV -> simplifyDiv(binop, rec);
                case MOD -> simplifyMod(binop, rec);
                case LOG_AND -> simplifyLogAnd(binop, rec);
                case LOG_OR -> simplifyLogOr(binop, rec);
                case LOG_EQ -> simplifyLogEq(binop, rec);
                case LOG_NEQ -> simplifyLogNeq(binop, rec);
                case LOG_LT -> simplifyLogLt(binop, rec);
                case LOG_GT -> simplifyLogGt(binop, rec);
                case LOG_LE -> simplifyLogLe(binop, rec);
                case LOG_GE -> simplifyLogGe(binop, rec);
                default -> {
                    throw new RuntimeException("unsupported binop: " + binop.op);
                }
            };
        } else {
            return inst;
        }
    }

    public static Value simplifyAdd(BinopInst inst, Boolean rec) {
        var lhs = inst.getOperand0();
        var rhs = inst.getOperand1();
        if (lhs instanceof GlobalVariable) {
            lhs = ((GlobalVariable) lhs).init;
        }
        if (rhs instanceof GlobalVariable) {
            rhs = ((GlobalVariable) rhs).init;
        }
        var targetEndInst = ListUtil.last(inst.parent.insts);
        Value c = foldConstant(lhs, rhs, inst.op);
        if (c != null) {
            return c;
        }

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
            }
            // X + (Y - Z) -> (X + Y) - Z or (X - Z) + y

            // try -- X + (Y - Z) -> (X + Y) - Z
            var subLhs = rhsInst.getOperand0();
            var subRhs = rhsInst.getOperand1();
            var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, lhs, subLhs);
            var simplifiledAddInst = simplify(tmpAddInst, false);
            if (simplifiledAddInst != tmpAddInst) {
                return simplify(new BinopInst(inst.parent, BinaryOp.SUB, simplifiledAddInst, subRhs), false);
            }

            // try --  X + (Y - Z) ->  (X - Z) + y
            var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, lhs, subRhs);
            var simplifiledSubInst = simplify(tmpSubInst, false);
            if (simplifiledSubInst != tmpSubInst) {
                return simplify(new BinopInst(inst.parent, BinaryOp.ADD, simplifiledSubInst, subLhs), false);
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
            }

            // (X - Y) + Z -> (X + Z) - Y or (Z - Y) + X
            var subLhs = lhsInst.getOperand0();
            var subRhs = lhsInst.getOperand1();

            var tmpAddInst = new BinopInst(inst.parent, BinaryOp.ADD, subLhs, rhs);
            var simplifiledAddInst = simplify(tmpAddInst, false);
            if (simplifiledAddInst != tmpAddInst) {
                return simplify(new BinopInst(inst.parent, BinaryOp.SUB, simplifiledAddInst, subRhs), false);
            }

            var tmpSubInst = new BinopInst(inst.parent, BinaryOp.SUB, subRhs, rhs);
            var simplifiledSubInst = simplify(tmpSubInst, false);
            if (simplifiledSubInst != tmpSubInst) {
                return simplify(new BinopInst(inst.parent, BinaryOp.ADD, simplifiledSubInst, subLhs), false);
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

    public static Value simplifySub(BinopInst inst, Boolean rec) {
        return inst;
    }

    public static Value simplifyMul(BinopInst inst, Boolean rec) {
        return inst;
    }

    public static Value simplifyDiv(BinopInst inst, Boolean rec) {
        return inst;
    }

    public static Value simplifyMod(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogAnd(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogOr(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogEq(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogNeq(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogLt(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogGt(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogLe(BinopInst inst, Boolean rec) {
        return inst;

    }

    public static Value simplifyLogGe(BinopInst inst, Boolean rec) {
        return inst;

    }
}
