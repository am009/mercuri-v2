package dst.ds;

import ir.ds.FuncSymbol;

public class ReturnStatement extends BlockStatement {
    public Expr retval;

    /** After semantic analysis, the symbol of the function that this return statement belongs to */
    public FuncSymbol funcSymbol;

    public ReturnStatement(Expr retval) {
        this.retval = retval;
    }
}
