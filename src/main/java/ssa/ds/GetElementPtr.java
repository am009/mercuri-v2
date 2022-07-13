package ssa.ds;

import java.util.ArrayList;
import java.util.List;

// https://llvm.org/docs/LangRef.html#getelementptr-instruction
// https://llvm.org/docs/GetElementPtr.html
public class GetElementPtr extends Instruction {
    Type base;

    public GetElementPtr(BasicBlock p, Value ptr) {
        assert ptr.type.isPointer;
        parent = p;
        oprands.add(new Use(this, ptr));
        // 非参数时第一维是0 (是参数时必然省略第一维)
        if (!(ptr instanceof ParamValue)) {
            oprands.add(new Use(this, ConstantValue.ofInt(0)));
        }
        type = ptr.type.clone();
        this.base = ptr.type.clone();
        this.base.isPointer = false;
    }

    public GetElementPtr addIndex(Value v) {
        assert v.type.baseType == PrimitiveTypeTag.INT && (!v.type.isArray());
        if (oprands.size() > 1) { // 只取一维类型不变
            type = type.subArrType();
            type.isPointer = true;
        }
        oprands.add(new Use(this, v));
        return this;
    }

    static String format = "%s = getelementptr %s, %s* %s";
    // getelementptr <ty>, <ty>* <ptrval>{, <ty> <idx>}*
    @Override
    public String toString() {
        var s = String.format(format, toValueString(), base.toString(), base.toString(), oprands.get(0).value.toValueString());
        var b = new StringBuilder(s);
        oprands.subList(1, oprands.size()).forEach(use -> b.append(", ").append(use.value.type.toString()).append(" ").append(use.value.toValueString()));
        if (comments != null) {
            b.append("     ; ").append(comments);
        }
        return b.toString();
    }
}
