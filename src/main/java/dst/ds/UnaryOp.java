package dst.ds;

public enum UnaryOp {
    POS,
    NEG,
    NOT;

    public static UnaryOp fromString(String s) {
        switch (s) {
            case "+":
                return POS;
            case "-":
                return NEG;
            case "!":
                return NOT;
            default:
                throw new IllegalArgumentException("Invalid UnaryOp: " + s);
        }
    }
}
