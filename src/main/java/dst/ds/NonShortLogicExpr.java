package dst.ds;

import ir.ds.Scope;

// 短路求值比较特殊，因此用一个节点划分短路求值和非短路求值的条件表达式的分界线
public class NonShortLogicExpr extends Expr {
    public Expr expr;

    public NonShortLogicExpr(Expr e) {
        expr = e;
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return expr.eval(scope);
    }
}