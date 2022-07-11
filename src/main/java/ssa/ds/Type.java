package ssa.ds;

import java.util.ArrayList;
import java.util.List;

// 根据dims区分是否是array类型。
public class Type implements Cloneable {
    public PrimitiveTypeTag baseType;
    public List<Integer> dims;
    public boolean isPointer;

    // 常用类型
    public static Type Void = new Type(PrimitiveTypeTag.VOID, null, false);
    public static Type Boolean = new Type(PrimitiveTypeTag.BOOLEAN, null, false);
    public static Type Char = new Type(PrimitiveTypeTag.CHAR, null, false);
    public static Type Int = new Type(PrimitiveTypeTag.INT, null, false);
    public static Type Float = new Type(PrimitiveTypeTag.FLOAT, null, false);
    public static Type String = new Type(PrimitiveTypeTag.CHAR, null, true);

    public Type(PrimitiveTypeTag baseType, List<Integer> dims, boolean isPointer) {
        this.baseType = baseType;
        this.dims = dims;
        this.isPointer = isPointer;
    }

    public boolean isArray() {
        if (dims != null && dims.size() > 0) {
            return true;
        }
        return false;
    }

    @Override
    public Type clone() {
        var dims_ = dims;
        if (dims != null) { // deep copy
            dims_ = new ArrayList<>(dims);
        }
        var ret = new Type(baseType, dims_, isPointer);
        return ret;
    }

    public Type subArrType() {
        var t = clone();
        t.isPointer = false;
        t.dims.remove(0);
        return t;
    }

    @Override
    public String toString() {
        var dims_ = dims;
        if (dims != null) { // deep copy
            dims_ = new ArrayList<>(dims);
        }
        return recursiveToString(baseType, dims_, isPointer);
    }

    // 递归写法少分配一些对象，如果递归使用subArrType则会为每个子类型分配临时对象
    // 会修改dims，因此调用前copy一份
    // eg: [2 x [3 x i32]]
    public static String recursiveToString(PrimitiveTypeTag baseType, List<Integer> dims, boolean isPointer) {
        if (dims == null || dims.size() == 0) {
            return baseType.toString() + (isPointer ? "*" : "");
        } else {
            var b = new StringBuilder("[");
            b.append(dims.get(0)).append(" x ");
            dims.remove(0);
            b.append(recursiveToString(baseType, dims, false));
            b.append("]");
            if (isPointer) {b.append("*");}
            return b.toString();
        }
    }
}