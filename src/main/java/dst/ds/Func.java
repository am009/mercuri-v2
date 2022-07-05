package dst.ds;

import java.util.List;

public class Func {
    public FuncType retType;
    public String id;
    public List<Decl> params;
    public Boolean isVariadic; // 是否是可变参数，若是，则调用时超出 params 部分长度的均视为可变参数
    public Block body;

    public Func(FuncType retType, String id, List<Decl> params, Block body) {
        this.retType = retType;
        this.id = id;
        this.params = params;
        this.body = body;
    }

    public Func setIsVariadic(Boolean isVariadic) {
        this.isVariadic = isVariadic;
        return this;
    }
}
