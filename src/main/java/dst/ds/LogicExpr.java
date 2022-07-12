package dst.ds;

import ds.Global;
import ir.ds.Scope;

// 表示此处进入了条件判断
public class LogicExpr extends Expr {
    public Expr expr;

    public LogicExpr(Expr e) {
        expr = e;
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return expr.eval(scope);
    }
}
