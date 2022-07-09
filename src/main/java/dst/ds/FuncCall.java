package dst.ds;

import ir.ds.FuncSymbol;
import ir.ds.Scope;

// FuncCall is one of the unary operation
public class FuncCall extends Expr {
    public String funcName;

    public FuncSymbol funcSymbol;

    public Expr[] args;

    public FuncCall(String funcName, Expr[] args) {
        this.funcName = funcName;
        this.args = args;
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        return null;
    }
}
