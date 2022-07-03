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
    AND,
    OR,
    // eqExpr
    EQ,
    NEQ,
    // relExpr
    LT,
    GT,
    LE,
    GE;

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
                return AND;
            case "||":
                return OR;
            case "==":
                return EQ;
            case "!=":
                return NEQ;
            case "<":
                return LT;
            case ">":
                return GT;
            case "<=":
                return LE;
            case ">=":
                return GE;
            default:
                throw new IllegalArgumentException("Unknown binary operator: " + s);
        }
    }
}
