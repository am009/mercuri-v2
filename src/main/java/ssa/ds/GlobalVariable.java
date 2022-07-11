package ssa.ds;

// 直接使用GlobalVariable时代表全局变量的地址，访问内部值需要一个Load指令。
public class GlobalVariable extends Value {
    // TODO array related initValue Design
    Type varType; // Value内的Type为指针类型，这里保存原始类型
    public ConstantValue init;

    public boolean isConst;

    public GlobalVariable(String name, Type ty) {
        this.name = name;
        this.varType = ty;
        type = varType.clone();
        if (type.isPointer) {
            // 因为目前还不支持指针的指针类型
            throw new RuntimeException("Global Pointer Variable not supported");
        } else {
            type.isPointer = true;
        }
    }

    @Override
    public String toString() { // 转LLVM IR的全局变量
        var b = new StringBuilder("@");
        b.append(name).append(" = ");
        if (isConst) {
            b.append("constant ");
        } else {
            b.append("global ");
        }
        
        b.append(varType.toString());
        if (init != null) {
            b.append(" ").append(init.toValueString());
        } else {
            b.append(" zeroinitializer");
        }
        b.append("\n");
        return b.toString();
    }

    @Override
    public String toValueString() {
        return "@"+name;
    }
}
