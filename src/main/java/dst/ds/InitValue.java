package dst.ds;

import java.util.List;

public class InitValue {

    public Expr value;

    // is array init value?
    public Boolean isArray = false;
    public List<Integer> dims;
    public List<InitValue> values;

    public InitValType initType;

    public static InitValue ofArray(InitValType initType, List<Integer> dims, List<InitValue> values) {
        InitValue i = new InitValue();
        i.initType = initType;
        i.isArray = true;
        i.dims = dims;
        i.values = values;
        return i;
    }

    public static InitValue ofExpr(InitValType initType, Expr value) {
        InitValue i = new InitValue();
        i.initType = initType;
        i.value = value;
        return i;
    }

}
