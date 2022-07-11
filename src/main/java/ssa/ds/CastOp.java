package ssa.ds;

// https://mapping-high-level-constructs-to-llvm-ir.readthedocs.io/en/latest/basic-constructs/casts.html
public enum CastOp {
    TYPE, // bitcast from pointer to pointer
    ZEXT,
    SEXT,
    TRUNC, // down cast
    I2F, // sitofp
    F2I, // fptosi
}
