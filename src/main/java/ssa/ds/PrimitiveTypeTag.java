package ssa.ds;

public enum PrimitiveTypeTag {
    VOID, // 函数返回值
    BOOLEAN, // i1
    CHAR, // i8
    INT, // i32
    FLOAT, // float f32
    DOUBLE; // 函数调用的时候要提升到double传参

    public boolean isFloat() {
        return this == FLOAT || this == DOUBLE;
    }

    public String toAsmString() {
        switch (this) {
            case CHAR:
                return "byte";
            case INT:
                return "long";
            case FLOAT:
                return "long";
            default:
                throw new IllegalArgumentException("Cannot call getSize on: " + super.toString());
        }
    }

    // 也是用于后端代码生成
    public long getByteSize() {
        switch (this) {
            case CHAR:
                return 1;
            case INT:
                return 4;
            case FLOAT:
                return 4;
            default:
                throw new IllegalArgumentException("Cannot call getSize on: " + super.toString());
        }
    }

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
            case DOUBLE:
                return "double";
            default:
                throw new IllegalArgumentException("Invalid PrimitiveTypeTag: " + super.toString());
        }
    }
}
