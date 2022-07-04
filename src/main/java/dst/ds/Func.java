package dst.ds;

import java.util.List;

public class Func {
    FuncType retType;
    String id;
    List<Decl> params;
    Block body;

    public Func(FuncType retType, String id, List<Decl> params, Block body) {
        this.retType = retType;
        this.id = id;
        this.params = params;
        this.body = body;
    }
}
