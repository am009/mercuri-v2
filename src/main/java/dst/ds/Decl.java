package dst.ds;

import java.util.List;

public class Decl extends BlockStatement {
    // Overall info
    public Type type;

    public DeclType declType;

    public Boolean isParam;

    public Boolean isGlobal;

    public BasicType basicType;

    // Left hand side
    public String id; // name of the variable
    public List<Integer> dims;

    // Right hand side
    public InitValue initVal; // value of the variable

    public Decl(DeclType declType, Boolean isParam, Boolean isGlobal, BasicType basicType, String id,
            List<Integer> dims, InitValue initVal) {
        this.declType = declType;
        this.isParam = isParam;
        this.isGlobal = isGlobal;
        this.basicType = basicType;
        this.id = id;
        this.dims = dims;
        this.initVal = initVal;
    }

    public static Decl fromSimpleParam(String id, BasicType basicType) {
        return new Decl(DeclType.VAR, true, false, basicType, id, null, null);
    }

    public static Decl fromArrayParam(String id, BasicType basicType, List<Integer> dims) {
        return new Decl(DeclType.VAR, true, false, basicType, id, dims, null);
    }

    public Boolean isArray() {
        return dims != null && !dims.isEmpty();
    }

}
