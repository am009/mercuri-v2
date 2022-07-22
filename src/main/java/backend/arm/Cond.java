package backend.arm;

public enum Cond {
    AL,
    EQ,
    NE,
    GE,
    GT,
    LE,
    LT;
    
    @Override
    public String toString() {
        switch(this) {
            case AL:
                return "";
            case EQ:
                return "EQ";
            case NE:
                return "NE";
            case GE:
                return "GE";
            case GT:
                return "GT";
            case LE:
                return "LE";
            case LT:
                return "LT";
            default:
                throw new UnsupportedOperationException();
        }
    }
}
