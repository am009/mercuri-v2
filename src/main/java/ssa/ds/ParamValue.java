package ssa.ds;

public class ParamValue extends Value{
    public String name;

    public ParamValue(String name, Type t) {
        this.type = t;
        this.name = name;
    }

    // 函数定义时调用
    public String toString() {
        var b = new StringBuilder();
        b.append(type.toString());
        b.append(" %").append(name);
        return b.toString();
    }
}
