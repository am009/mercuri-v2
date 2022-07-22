package backend;

import backend.arm.LabelImm;
import ssa.ds.GlobalVariable;
import ssa.ds.PrimitiveTypeTag;

// 对应一系列的data段。
public class AsmGlobalVariable {
    public GlobalVariable ssaGlob;
    public PrimitiveTypeTag base;
    // 用于填充导出的链接器符号的大小，和bss段时占用空间的大小。以字节为单位
    public long size;
    // 还是在打印的时候根据ssaGlob动态打印initVal吧，方便带上注释。
    // List<Number> initVal;

    // 代表对应地址常量的Immediate值
    public LabelImm imm;

    public AsmGlobalVariable(GlobalVariable g) {
        ssaGlob = g;
        base = g.varType.baseType;
        imm = new LabelImm(g.name);
    }

    @Override
    public String toString() {
        return backend.arm.AsmPrinter.emitGlobVar(this);
    }
}
