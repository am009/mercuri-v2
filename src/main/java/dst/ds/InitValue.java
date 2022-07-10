package dst.ds;

import java.util.List;
import java.util.StringJoiner;

// InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '
// 所以有两种情况，叶子节点的Exp，和作为array的情况
public class InitValue {

    public Expr value;
    public EvaluatedValue evaledVal; // 如果是Const，语义分析时填入

    // is array init value?
    public Boolean isArray = false;
    public List<InitValue> values;

    public InitValType initType; // 仅标识是Const还是Var，是否是数组还是看isArray

    public static InitValue ofArray(InitValType initType, List<InitValue> values) {
        InitValue i = new InitValue();
        i.initType = initType;
        i.isArray = true;
        i.values = values;
        return i;
    }

    public static InitValue ofExpr(InitValType initType, Expr value) {
        InitValue i = new InitValue();
        i.initType = initType;
        i.value = value;
        return i;
    }

    // // 给“叶子”节点的Expr调用eval。
    // public EvaluatedValue eval(Scope scope) {
    //     if (! isArray) {
    //         return value.eval(scope);
    //     } else {
    //         return InitValue.ofArray(initType, values.stream().map(e -> e.eval(scope)).collect(Collectors.toList()));
    //     }
    // }

    // for easier debugging
    @Override
    public String toString() {
        if (isArray) {
            StringJoiner sj = new StringJoiner(",", "{", "}");
            for (var v: values) {
                sj.add(v.toString());
            }
            return sj.toString();
        } else {
            if (evaledVal != null) {
                return evaledVal.toString();
            } else if (value != null) {
                return value.toString();
            } else { // default value
                return "0";
            }
        }
    }
}
