package ssa.ds;

public class FuncValue extends Value {
    public Func func;
    public FuncValue(Func func) {
        this.func = func;
        name = func.name;
    }

    @Override
    public String toValueString() {
        return "@" + name;
    }
}
