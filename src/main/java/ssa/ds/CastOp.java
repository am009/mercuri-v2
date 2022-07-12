package ssa.ds;

// https://mapping-high-level-constructs-to-llvm-ir.readthedocs.io/en/latest/basic-constructs/casts.html
public enum CastOp {
    TYPE, // bitcast from pointer to pointer
    ZEXT,
    SEXT,
    FPEXT,
    TRUNC, // down cast
    I2F, // sitofp
    F2I; // fptosi

    @Override
    public String toString() {
        switch (this) {
            case F2I:
                return "fptosi";
            case I2F:
                return "sitofp";
            case SEXT:
                return "sext";
            case TRUNC:
                return "trunc";
            case TYPE:
                return "bitcast";
            case ZEXT:
                return "zext";
            case FPEXT:
                return "fpext";
            default:
                return super.toString();
        }
    }
}
