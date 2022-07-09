package dst.ds;

import ir.ds.Scope;

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
    public EvaluatedValue eval(Scope scope) {
        if (aryType == AryType.Binary) {
            return binaryExpr.eval(scope);
        } else {
            return unaryExpr.eval(scope);
        }
    }
}
