package ssa.ds;

public enum PrimitiveTypeTag {
    VOID, // 函数返回值
    BOOLEAN, // i1
    CHAR, // i8
    INT, // i32
    FLOAT; // float f32


    @Override
    public String toString() {
        switch (this) {
            case VOID:
                return "void";
            case BOOLEAN:
                return "i1";
            case CHAR:
                return "i8";
            case INT:
                return "i32";
            case FLOAT:
                return "float";
            default:
                throw new IllegalArgumentException("Invalid PrimitiveTypeTag: " + super.toString());
        }
    }
}
