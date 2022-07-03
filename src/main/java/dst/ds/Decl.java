package dst.ds;

public class Decl extends BlockStatement {
    DeclType declType;

    Boolean isParam;
    Func ownerFunc;

    Boolean isGlobal;
    BasicType basicType;
    String id; // name of the variable
    InitVal initVal; // value of the variable
}
