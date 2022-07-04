package dst.ds;

public class LogicExpr extends Expr {
    public enum AryType {
        Binary, Unary
    }

    public AryType aryType;
    public BinaryExpr binaryExpr;
    public UnaryExpr unaryExpr;

    public LogicExpr(BinaryExpr binaryExpr) {
        this.aryType = AryType.Binary;
        this.binaryExpr = binaryExpr;
    }

    public LogicExpr(UnaryExpr unaryExpr) {
        this.aryType = AryType.Unary;
        this.unaryExpr = unaryExpr;
    }

    @Override
    public EvaluatedValue eval() {
        if (aryType == AryType.Binary) {
            return binaryExpr.eval();
        } else {
            return unaryExpr.eval();
        }
    }
}
