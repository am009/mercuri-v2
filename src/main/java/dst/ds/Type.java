package dst.ds;

import java.util.ArrayList;
import java.util.List;

import ds.Global;

public class Type implements Cloneable {

    public BasicType basicType;
    public Boolean isArray;
    public Boolean isPointer; // if true, first dim is omited
    public List<Integer> dims;

    public Type(BasicType basicType, Boolean isArray, Boolean isPointer,
            List<Integer> dims) {
        this.basicType = basicType;
        this.isArray = isArray;
        this.isPointer = isPointer;
        this.dims = dims;
    }

    @Override
    public Type clone() {
        var ret = new Type(basicType, isArray, isPointer, dims);
        if (ret.dims != null) { // deep copy
            ret.dims = new ArrayList<>(ret.dims);
        }
        return ret;
    }

    public static Type Integer = new Type(BasicType.INT, false, false, null);
    public static Type Float = new Type(BasicType.FLOAT, false, false, null);
    public static Type String = new Type(BasicType.STRING_LITERAL, false, false, null);
    public static Type Void = new Type(null, false, false, null);

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
        if (type1.isArray != type2.isArray) {
            return false;
        }

        if (type1.dims != null && type2.dims != null) {
            // 省略一维后仅检查后面的维度
            var dims1 = type1.dims;
            var dims2 = type2.dims;
            if (type1.isPointer != type2.isPointer) {
                if (type1.isPointer) {
                    dims2 = dims2.subList(1, dims2.size());
                } else {
                    dims1 = dims1.subList(1, dims1.size());
                }
            }
            if (!dims1.equals(dims2)) {
                return false;
            }
        }
        return true;
    }

    // binary expr在语法检查时使用。因此暂时不需要关心数组类型
    public static Type getCommon(Type type1, Type type2) {
        if (isMatch(type1, type2)) {
            return type1;
        }
        if (isMatch(type1,Type.Float) && isMatch(type2,Type.Integer)) {
            return Type.Float;
        }
        if (isMatch(type1,Type.Integer) && isMatch(type2,Type.Float)) {
            return Type.Float;
        }
        if (type1.isArray && type2.isArray) {
            if (type1.basicType == type2.basicType) {
                return type1;
            }
        }
        Global.logger.warning("No common type: " + type1 + " " + type2);
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
