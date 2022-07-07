package dst.ds;

public enum BasicType {
    INT,
    FLOAT,    
    STRING_LITERAL;

    public static BasicType fromString(String s) {
        switch (s) {
            case "int":
                return INT;
            case "float":
                return FLOAT;
            default:
                throw new IllegalArgumentException("Invalid BasicType: " + s);
        }
    }
}