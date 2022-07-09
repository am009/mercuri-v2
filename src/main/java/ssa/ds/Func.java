package ssa.ds;

import java.util.List;
import java.util.StringJoiner;

import dst.ds.FuncType;
import dst.ds.Type;

// declaration (bbs==null) or definition
public class Func {
    public String name;
    public List<BasicBlock> bbs;
    public FuncValue val; // used by CallInst
    public boolean isVariadic = false;
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
        argType.forEach(ty -> sj.add(ty.toString()));
        if (isVariadic) {
            sj.add("...");
        }
        builder.append(sj.toString());

        if (bbs == null) {
            builder.append("\n");
        } else { // body
            builder.append("{\n  ");
            var sj2 = new StringJoiner("\n  ");
            bbs.forEach(bb -> sj2.add(bb.toString()));
            builder.append(sj.toString());
            builder.append("\n}");
        }
        return builder.toString();
    }
}
