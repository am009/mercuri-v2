package dst.ds;

public class BinaryExpr extends Expr {
    public Expr left;
    public Expr right;
    public BinaryOp op;
    
    public BinaryExpr(Expr left, Expr right, BinaryOp op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    @Override
    public InitVal eval() {
        // TODO Auto-generated method stub
        return null;
    }
}