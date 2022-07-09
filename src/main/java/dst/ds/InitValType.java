package dst.ds;

public enum InitValType {
    CONST_EXPR,
    VAR_EXPR,
    ;

    public static Boolean isConst(InitValType initValType) {
        switch (initValType) {
            case CONST_EXPR:
                return true;
            case VAR_EXPR:
                return false;
            default:
                throw new IllegalArgumentException("Invalid InitValType: " + initValType);
        }
    }
}
