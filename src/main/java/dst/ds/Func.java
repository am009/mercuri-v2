package dst.ds;

import java.util.List;

public class Func {
    FuncType retType;
    String id;
    List<BlockStatement> params;

    public Func(FuncType retType, String id, List<BlockStatement> params) {
        this.retType = retType;
        this.id = id;
        this.params = params;
    }
}
