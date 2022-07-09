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

    @Override
    public String toString() {
        switch (this) {
            case INT:
                return "i32";
            case FLOAT:
                return "float";
            case STRING_LITERAL:
                return "i8*";
            default:
                throw new IllegalArgumentException("Invalid BasicType: " + super.toString());
        }
    }
}
