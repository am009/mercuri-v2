package dst.ds;

import java.util.List;

import ds.Global;

public class Type {

    public BasicType basicType;
    public Boolean isConst;
    public Boolean isArray;
    public Integer numElements;
    public Boolean isVarlen; // if true, omit first dim
    public List<Integer> dims;

    public Type(BasicType basicType, Boolean isConst, Boolean isArray, Integer numElements, Boolean isVarlen,
            List<Integer> dims) {
        this.basicType = basicType;
        this.isConst = isConst;
        this.isArray = isArray;
        this.numElements = numElements;
        this.isVarlen = isVarlen;
        this.dims = dims;
    }

    public static Type Integer = new Type(BasicType.INT, false, false, null, false, null);
    public static Type Float = new Type(BasicType.FLOAT, false, false, null, false, null);
    public static Type String = new Type(BasicType.STRING_LITERAL, false, false, null, false, null);
    public static Type Void = new Type(null, false, false, null, false, null);

    public static Type fromFuncType(FuncType ft) {
        switch (ft) {
            case FLOAT:
                return Type.Float;
            case INT:
                return Type.Integer;
            case VOID:
                return Type.Void;
            default:
                throw new IllegalArgumentException("Unknown func type: " + ft);
        }
    }

    public static boolean isMatch(Type type1, Type type2) {
        if (type1 == null || type2 == null) {
            Global.logger.warning("Type is null");
            return false;
        }
        if (type1.basicType != type2.basicType) {
            return false;
        }
        if (type1.isConst != type2.isConst) {
            return false;
        }
        if (type1.isArray != type2.isArray) {
            return false;
        }
        if (type1.numElements != type2.numElements) {
            return false;
        }
        if (type1.isVarlen != type2.isVarlen) {
            return false;
        }
        if (type1.dims.equals(type2.dims)) {
            return false;
        }
        return true;
    }

    public static Type getCommon(Type type1, Type type2) {
        if (isMatch(type1, type2)) {
            return type1;
        }
        if (type1 == Type.Float && type2 == Type.Integer) {
            return Type.Float;
        }
        if (type1 == Type.Integer && type2 == Type.Float) {
            return Type.Float;
        }
        // TODO: completely support array
        if (type1.isArray && type2.isArray) {
            if (type1.basicType == type2.basicType) {
                return type1;
            }
        }
        return null;
    }

    public static Type frombasicType(BasicType basicType2) {
        switch (basicType2) {
            case INT:
                return Type.Integer;
            case FLOAT:
                return Type.Float;
            case STRING_LITERAL:
                return Type.String;
            default:
                throw new IllegalArgumentException("Unknown basic type: " + basicType2);
        }
    }
}
