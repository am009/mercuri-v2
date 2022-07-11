package dst.ds;

import ir.ds.Scope;

// 语义分析时插入，代表隐式类型转换
public class CastExpr extends Expr {
    // 目前只有两种情况
    public enum CastType {
        I2F, F2I
    }
    public CastType castType;
    public String reason; // 解释为什么转换的注释

    public Expr child;

    public CastExpr(Expr child, CastType castType, String reason) {
        this.child = child;
        this.castType = castType;
        if (castType == CastType.I2F) {
            type = Type.Float;
        } else if(castType == CastType.F2I) {
            type = Type.Integer;
        }
        this.reason = reason;
    }

    @Override
    public EvaluatedValue eval(Scope scope) {
        var val = child.eval(scope);
        if (castType == CastType.F2I) {
            assert val.basicType == BasicType.FLOAT;

            val = EvaluatedValue.ofInt((int) (val.floatValue.floatValue()));
            return val;
        } else if (castType == CastType.I2F) {
            assert val.basicType == BasicType.INT;
            val = EvaluatedValue.ofFloat(val.intValue);
            return val;
        }
        throw new RuntimeException();
    }
}
