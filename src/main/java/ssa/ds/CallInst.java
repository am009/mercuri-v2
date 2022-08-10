package ssa.ds;

import java.util.StringJoiner;

public class CallInst extends Instruction {

    public static class Builder {
        private CallInst inst;

        public Builder(BasicBlock parent, FuncValue func) {
            inst = new CallInst();
            inst.parent = parent;
            inst.oprands.add(new Use(inst, func));
            inst.type = func.func.getRetType();
        }

        public Builder addArg(Value arg) {
            inst.oprands.add(new Use(inst, arg));
            return this;
        }

        public CallInst build() {
            return inst;
        }
    }

    public boolean isVariadic() {
        FuncValue fv = (FuncValue) oprands.get(0).value;
        return fv.func.isVariadic;
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        var val = toValueString();
        if (val.length() > 0) { // return non void
            b.append(val).append(" = ");
        }
        b.append("call");
        FuncValue funcval = (FuncValue) oprands.get(0).value;
        if (!isVariadic()) {
            b.append(" ").append(type.toString());
        } else {
            b.append(" ").append(funcval.func.getFnTyString());
        }
        
        b.append(" ").append(funcval.toValueString());
        var sj = new StringJoiner(", ", "(", ")");
        oprands.subList(1, oprands.size()).forEach(use -> sj.add(use.value.type.toString() + " " +use.value.toValueString()));
        b.append(sj.toString());
        if (comments != null) {
            b.append("     ; ").append(comments);
        }
        return b.toString();
    }

    public boolean isPure() {
        // TODO: 跨调用分析
        // var fv = ((FuncValue) oprands.get(0).value);
        // var targetFunc = fv.func;
        // if(func.hasSideEffect() || func.usingGlobalVar()) {
        //     return false;
        // }
        // for(var val: this.oprands) {

        // }
        return false;
    }
}
