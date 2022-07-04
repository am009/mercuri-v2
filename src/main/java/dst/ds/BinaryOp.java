package dst.ds;

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
}
