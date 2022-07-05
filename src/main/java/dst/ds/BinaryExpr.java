package dst.ds;

public class BinaryExpr extends Expr {

    /*
     * BinaryExpr
     * FuncCall
     * LiteralExpr
     * LogicExpr
     * LValExpr
     * UnaryExpr
     */

    public Expr left;
    public Expr right;
    public BinaryOp op;

    public BinaryExpr(Expr left, Expr right, BinaryOp op) {
        this.left = left;
        this.right = right;
        this.op = op;
        this.eval();
    }

    @Override
    public EvaluatedValue eval() {
        if (this.value != null) {
            return this.value;
        }
        this.value = EvaluatedValue.fromOperation(left.value, right.value, op);
        return this.value;
    }
}