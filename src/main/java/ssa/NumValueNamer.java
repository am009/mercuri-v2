package ssa;

import ssa.ds.BasicBlock;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.Type;
import ssa.ds.Value;

// basicBlock的label还是生成的时候去命名，在ctx里放个index防止重名
// 全局变量和函数参数也应当在生成时命名。语法里好像函数参数的名字不能省略。
public class NumValueNamer {
    long count = 0;

    public void visitModule(Module m) {
        m.funcs.forEach(f -> visitFunc(f));
    }

    public void visitFunc(Func f) {
        f.bbs.forEach(bb -> visitBlock(bb));
    }

    public void visitBlock(BasicBlock bb) {
        bb.insts.forEach(i -> visitInst(i));
    }

    public void visitInst(Instruction i) {
        visitValue(i);
        i.getUses().forEach(use -> visitValue(use.value));
    }

    public void visitValue(Value i) {
        if ((!i.type.equals(Type.Void)) && i.name == null) {
            i.name = String.valueOf(count++);
        }
    }
}
