package dst.ds;

import java.util.List;

public class Decl extends BlockStatement {
    // Overall info
    // type在语义分析时填入
    public Type type;

    // 是否是const通过decl.declType == DeclType.CONST判断
    public DeclType declType;

    public Boolean isParam;

    public Boolean isGlobal;

    public BasicType basicType;

    // Left hand side
    public String id; // name of the variable
    public List<Expr> dims; // 语义分析后转为List<Integer>存放到Type.dims中
    // public List<Integer> evaledDims;

    // 函数参数为数组省略签名，因为语义分析的时候才填入Type，所以这里也需要一个成员
    public boolean isDimensionOmitted = false;

    // Right hand side
    public InitValue initVal; // value of the variable

    public Decl(DeclType declType, Boolean isParam, Boolean isGlobal, BasicType basicType, String id,
            List<Expr> dims, InitValue initVal) {
        this.declType = declType;
        this.isParam = isParam;
        this.isGlobal = isGlobal;
        this.basicType = basicType;
        this.id = id;
        if (dims != null && dims.size() > 0) {
            this.dims = dims;
        }
        this.initVal = initVal;
    }

    public static Decl fromSimpleParam(String id, BasicType basicType) {
        return new Decl(DeclType.VAR, true, false, basicType, id, null, null);
    }

    public static Decl fromArrayParam(String id, BasicType basicType, List<Expr> dims) {
        return new Decl(DeclType.VAR, true, false, basicType, id, dims, null);
    }

    public Boolean isArray() {
        return dims != null && !dims.isEmpty();
    }

}
