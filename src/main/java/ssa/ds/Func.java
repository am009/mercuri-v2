package ssa.ds;

import java.util.List;
import java.util.StringJoiner;

import dst.ds.FuncType;

// declaration (bbs==null) or definition
public class Func {
    public String name;
    public List<BasicBlock> bbs;
    public FuncValue val; // used by CallInst
    public boolean isVariadic = false; // 是否有可变参数
    public FuncType retType;
    public List<ParamValue> argType;

    public Func(String name, FuncType retTy, List<ParamValue> argTy) {
        this.name = name;
        this.retType = retTy;
        this.argType = argTy;
    }

    public FuncValue getValue() {
        if (val == null) {
            val = new FuncValue(this);
        }
        return val;
    }

    public Func setIsVariadic(boolean isVariadic) {
        this.isVariadic = isVariadic;
        return this;
    }

    public Type getRetType() {
        if (retType == FuncType.VOID) {
            return Type.Void;
        } else if (retType == FuncType.INT) {
            return Type.Int;
        } else if (retType == FuncType.FLOAT) {
            return Type.Float;
        }
        throw new RuntimeException("Get function return type failed.");
    }

    public String toString() {
        var builder = new StringBuilder();
        if (bbs == null) {
            builder.append("declare ");
        } else {
            builder.append("define ");
        }
        builder.append(retType.toString()).append(" ");
        builder.append("@").append(name);
        var sj = new StringJoiner(", ", "(", ")");
        if (bbs == null) {
            argType.forEach(pv -> sj.add(pv.type.toString()));
        } else {
            argType.forEach(pv -> sj.add(pv.toString()));
        }
        if (isVariadic) {
            sj.add("...");
        }
        builder.append(sj.toString());

        if (bbs == null) {
            builder.append("\n");
        } else { // body
            builder.append("{\n");
            var sj2 = new StringJoiner("\n");
            bbs.forEach(bb -> sj2.add(bb.toString()));
            builder.append(sj2.toString());
            builder.append("\n}");
        }
        return builder.toString();
    }

    // for vararg func call
    public String getFnTyString() {
        var builder = new StringBuilder();
        builder.append(retType.toString()).append(" ");
        var sj = new StringJoiner(", ", "(", ")");
        if (bbs == null) {
            argType.forEach(pv -> sj.add(pv.type.toString()));
        } else {
            argType.forEach(pv -> sj.add(pv.toString()));
        }
        if (isVariadic) {
            sj.add("...");
        }
        builder.append(sj.toString());
        return builder.toString();
    }
}
