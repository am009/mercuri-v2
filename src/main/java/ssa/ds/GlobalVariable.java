package ssa.ds;

import dst.ds.InitValue;
import dst.ds.Type;

public class GlobalVariable extends Value {
    // TODO array related initValue Design
    public InitValue init;

    public boolean isConst;

    public GlobalVariable(String name, Type ty) {
        this.name = name;
        this.type = ty;
    }

    @Override
    public String toString() {
        var b = new StringBuilder("@");
        b.append(name).append(" = ");
        if (isConst) {
            b.append("constant ");
        } else {
            b.append("global ");
        }
        
        b.append(type.toString());
        if (init != null) {
            b.append(" ");
            // TODO array related initValue Design
            b.append(init.toString());
        }
        b.append("\n");
        return b.toString();
    }
}
