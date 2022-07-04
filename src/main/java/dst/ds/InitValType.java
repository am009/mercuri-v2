package dst.ds;

public enum InitValType {
    CONST_EXPR,
    VAR_EXPR,
    CONST_ARRAY_EXPR,
    VAR_ARRAY_EXPR,
    ;

    public static InitValType basicInitTypeOf(InitValType initValType) {
        switch (initValType) {
            case CONST_ARRAY_EXPR:
                return CONST_EXPR;
            case VAR_ARRAY_EXPR:
                return VAR_EXPR;
            default:
                throw new IllegalArgumentException("Invalid InitValType: " + initValType);
        }
    }

    public static Boolean isConst(InitValType initValType) {
        switch (initValType) {
            case CONST_EXPR:
            case CONST_ARRAY_EXPR:
                return true;
            case VAR_EXPR:
            case VAR_ARRAY_EXPR:
                return false;
            default:
                throw new IllegalArgumentException("Invalid InitValType: " + initValType);
        }
    }

    public static Boolean isArray(InitValType initValType) {
        switch (initValType) {
            case CONST_ARRAY_EXPR:
            case VAR_ARRAY_EXPR:
                return true;
            case CONST_EXPR:
            case VAR_EXPR:
                return false;
            default:
                throw new IllegalArgumentException("Invalid InitValType: " + initValType);
        }
    }
}
