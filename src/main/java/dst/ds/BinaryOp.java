package dst.ds;

import common.Pair;

public enum BinaryOp {
    // addExpr
    ADD,
    SUB,
    // mulExpr
    MUL,
    DIV,
    MOD,
    // logicExpr
    LOG_AND,
    LOG_OR,
    // eqExpr
    LOG_EQ,
    LOG_NEQ,
    // relExpr
    LOG_LT,
    LOG_GT,
    LOG_LE,
    LOG_GE;


    public boolean isShortCircuit() {
        switch (this) {
            case ADD:
            case DIV:
            case MOD:
            case MUL:
            case SUB:
            case LOG_GE:
            case LOG_GT:
            case LOG_LE:
            case LOG_LT:
            case LOG_EQ:
            case LOG_NEQ:
                return false;
            case LOG_AND:
            case LOG_OR:
                return true;
            default:
                throw new IllegalArgumentException("Unknown binary operator: " + this);
        }
    }

    public boolean isBoolean() {
        switch (this) {
            case ADD:
            case DIV:
            case MOD:
            case MUL:
            case SUB:
                return false;
            case LOG_AND:
            case LOG_EQ:
            case LOG_GE:
            case LOG_GT:
            case LOG_LE:
            case LOG_LT:
            case LOG_NEQ:
            case LOG_OR:
                return true;
            default:
                throw new IllegalArgumentException("Unknown binary operator: " + this);
        }
    }

    public static BinaryOp fromString(String s) {
        switch (s) {
            case "+":
                return ADD;
            case "-":
                return SUB;
            case "*":
                return MUL;
            case "/":
                return DIV;
            case "%":
                return MOD;
            case "&&":
                return LOG_AND;
            case "||":
                return LOG_OR;
            case "==":
                return LOG_EQ;
            case "!=":
                return LOG_NEQ;
            case "<":
                return LOG_LT;
            case ">":
                return LOG_GT;
            case "<=":
                return LOG_LE;
            case ">=":
                return LOG_GE;
            default:
                throw new IllegalArgumentException("Unknown binary operator: " + s);
        }
    }

    public String toString(boolean isFloat) {
        if (!isFloat) {
            switch (this) {
                case ADD:
                    return "add";
                case DIV:
                    return "sdiv";
                case LOG_AND:
                    return "and";
                case LOG_EQ:
                    return "icmp eq";
                case LOG_GE:
                    return "icmp sge";
                case LOG_GT:
                    return "icmp sgt";
                case LOG_LE:
                    return "icmp sle";
                case LOG_LT:
                    return "icmp slt";
                case LOG_NEQ:
                    return "icmp ne";
                case LOG_OR:
                    return "or";
                case MOD:
                    return "srem";
                case MUL:
                    return "mul";
                case SUB:
                    return "sub";
                default:
                    break;
            }
        } else { // is float
            switch (this) {
                case ADD:
                    return "fadd";
                case DIV:
                    return "fdiv";
                case LOG_AND:
                    break;
                case LOG_EQ:
                    return "fcmp oeq";
                case LOG_GE:
                    return "fcmp oge";
                case LOG_GT:
                    return "fcmp ogt";
                case LOG_LE:
                    return "fcmp ole";
                case LOG_LT:
                    return "fcmp olt";
                case LOG_NEQ:
                    return "fcmp une"; // 出现NAN时要返回true
                case LOG_OR:
                    break;
                case MOD:
                    return "frem";
                case MUL:
                    return "fmul";
                case SUB:
                    return "fsub";
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException();
    }
    private static  BinaryOp[] commutativeOps = new BinaryOp[]{ADD, MUL, LOG_AND, LOG_OR, LOG_EQ, LOG_NEQ};

    public boolean isCommutative() {
        for (var op : commutativeOps) {
            if (op == this) {
                return true;
            }
        }
        return false;
    }

    public boolean isReverse(BinaryOp op) {
        var a = this;
        var b = op;
        if(a == LOG_LT && b == LOG_GT) {
            return true;
        }
        if(a == LOG_GT && b == LOG_LT) {
            return true;
        }
        if(a == LOG_LE && b == LOG_GE) {
            return true;
        }
        if(a == LOG_GE && b == LOG_LE) {
            return true;
        }
        return false;
    }
}
