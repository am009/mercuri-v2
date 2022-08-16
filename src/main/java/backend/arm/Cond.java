package backend.arm;

public enum Cond {
    AL,
    EQ,
    NE,
    GE,
    GT,
    LE,
    LT;
    
    public Cond negate() {
         switch(this) {
            case EQ:
                return NE;
            case NE:
                return EQ;
            case GE:
                return LT;
            case GT:
                return LE;
            case LE:
                return GT;
            case LT:
                return GE;
            default:
            case AL:
                return null;
         }
    }

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
