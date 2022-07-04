package dst.ds;

// FuncCall is one of the unary operation
public class FuncCall extends Expr {
    public String funcName;

    public FuncCall(String funcName, Expr[] args) {
        this.funcName = funcName;
        this.args = args;
    }

    public Expr[] args;

    @Override
    public EvaluatedValue eval() {
        return null;
    }
}
