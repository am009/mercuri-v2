package dst.ds;

public enum FuncType {
    VOID,
    INT,
    FLOAT,;

    public static FuncType fromString(String text) {
        if (text.equals("void")) {
            return VOID;
        }
        if (text.equals("int")) {
            return INT;
        }
        if (text.equals("float")) {
            return FLOAT;
        }
        throw new IllegalArgumentException("Unknown enum value: " + text);
    }

    public static Type toType(FuncType retType) {
        if (retType == VOID) {
            return null;
        }
        if (retType == INT) {
            return Type.Integer;
        }
        if (retType == FLOAT) {
            return Type.Float;
        }
        throw new IllegalArgumentException("Unknown enum value: " + retType);
    }
}
