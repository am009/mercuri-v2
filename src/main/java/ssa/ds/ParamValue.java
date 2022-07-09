package ssa.ds;

import dst.ds.Type;

public class ParamValue extends Value{
    public Type ty;
    public String name;

    public ParamValue(String name, Type t) {
        this.ty = t;
        this.name = name;
    }

    public String toString() {
        var b = new StringBuilder();
        b.append(ty.toString()).append(" ");
        b.append(name);
        return b.toString();
    }
}
