package dst.ds;

import java.util.List;

public class Decl extends BlockStatement {
    // Overall info

    DeclType declType;

    Boolean isParam;

    Boolean isGlobal;

    BasicType basicType;

    // Left hand side
    String id; // name of the variable
    List<Integer> dims;

    // Right hand side
    InitValue initVal; // value of the variable

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

}
