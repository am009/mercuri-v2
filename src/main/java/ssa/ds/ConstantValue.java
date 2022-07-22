package ssa.ds;

import java.util.List;
import java.util.StringJoiner;

import common.Util;

// 要么是普通常数，Number不为空，children为空
// 要么children不为空，Number为空
public class ConstantValue extends Value {
    public Number val;

    public List<ConstantValue> children;

    // for array
    public ConstantValue(Type type, List<ConstantValue> children) {
        this.type = type;
        this.children = children;
    }

    public boolean isArray() {
        if (children != null && children.size() > 0) {
            return true;
        }
        return false;
    }

    public static ConstantValue getDefault(Type ty) {
        if (ty.equals(Type.Boolean)) {
            return ConstantValue.ofBoolean(false);
        } else if (ty.equals(Type.Int)) {
            return ConstantValue.ofInt(0); 
        } else if (ty.equals(Type.Float)) {
            return ConstantValue.ofFloat(0f);
        } else {
            throw new UnsupportedOperationException("Cannot get default ConstanValue for complex types.");
        }
    }


    // for simple value
    public static ConstantValue ofInt(int i) {
        var ret = new ConstantValue(Type.Int.clone(), null);
        ret.val = Integer.valueOf(i);
        return ret;
    }

    public static ConstantValue ofBoolean(boolean i) {
        var ret = new ConstantValue(Type.Boolean.clone(), null);
        ret.val = Integer.valueOf(i ? 1 : 0);
        return ret;
    }

    public static ConstantValue ofChar(int i) {
        var ret = new ConstantValue(Type.Char.clone(), null);
        ret.val = Integer.valueOf(i);
        return ret;
    }

    public static ConstantValue ofFloat(Float f) {
        var ret = new ConstantValue(Type.Float.clone(), null);
        ret.val = f;
        return ret;
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        b.append(type.toString()).append(" ").append(toValueString());
        return b.toString();
    }

    // 没有Type部分
    public String toValueString() {
        var b = new StringBuilder();
        if (!isArray()) {
            if (val instanceof Float) {
                // To more exact representation
                b.append(Util.floatToLLVM((Float)val));
            } else if (val instanceof Double) {
                b.append(Util.doubleToLLVM((Double)val));
            } else {
                b.append(val.toString());
            }
        } else {
            var sj = new StringJoiner(", ", "[", "]");
            for (var c: children) {
                sj.add(c.toString());
            }
            b.append(sj.toString());
        }
        return b.toString();
    }
    
    // used by asm gen
    public String valToAsmString() {
        assert !isArray();
        if (val instanceof Float) {
            return Util.floatToASM((Float)val);
        } else if (val instanceof Double) {
            return Util.doubleToLLVM((Double)val);
        } else {
            return val.toString();
        }
    }
}
