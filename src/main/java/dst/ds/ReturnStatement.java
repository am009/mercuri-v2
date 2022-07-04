package dst.ds;

public class ReturnStatement extends BlockStatement {
    public Expr retval;

    public ReturnStatement(Expr retval) {
        this.retval = retval;
    }
}
