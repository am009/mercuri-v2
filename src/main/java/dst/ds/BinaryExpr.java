package dst.ds;

import ir.ds.Scope;

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
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return EvaluatedValue.fromOperation(left.eval(scope), right.eval(scope), op);
    }
}