package ssa;

import java.util.HashMap;
import java.util.Map;

import ssa.ds.BasicBlock;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.Type;
import ssa.ds.Value;

/**
 * NumValueNamer 包含一个计数器状态变量，用于给所有的 Value 提供一个唯一的临时数字名称。
 * 纯数字名称仅用于临时名称，因此其他部分命名时需要避开纯数字的名字。 
 * 
 * basicBlock的label还是生成的时候去命名，在ctx里放个index防止重名
 * 全局变量和函数参数也应当在生成时命名。语法里好像函数参数的名字不能省略。
 */
public class NumValueNamer {

    public static void process(Module m) {
        var instance = new NumValueNamer();
        instance.visitModule(m);
    }

    long count = 0;

    public void visitModule(Module m) {
        m.funcs.forEach(f -> visitFunc(f));
    }

    public void visitFunc(Func f) {
        count = 0;
        f.bbs.forEach(bb -> visitBlock(bb));
    }

    public void visitBlock(BasicBlock bb) {
        bb.insts.forEach(i -> visitInst(i));
    }

    public void visitInst(Instruction i) {
        visitValue(i);
        i.getUses().forEach(use -> visitValue(use.value));
    }

    public boolean isTempName(String name) {
        return name == null || name.matches("[0-9]+");
    }

    public Map<Value, Boolean> visited = new HashMap<>();

    public void visitValue(Value i) {
        if ((!i.type.equals(Type.Void)) && isTempName(i.name) && (!(i instanceof ConstantValue))) {
            // 当后面的指令引用前面指令的返回值的时候，会再次访问前面的指令。
            if (!visited.containsKey(i)) {
                i.name = String.valueOf(count++);
                visited.put(i, true);
            }
        }
    }
}
