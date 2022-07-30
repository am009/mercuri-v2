package ssa.pass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dst.ds.FuncType;
import ssa.ds.BasicBlock;
import ssa.ds.BinopInst;
import ssa.ds.CallInst;
import ssa.ds.Func;
import ssa.ds.Module;
import ssa.ds.ParamValue;
import ssa.ds.Type;

// TODO 定义Pass接口后使用统一接口
public class EABIArithmeicLowing {
    public static Map<String, Func> extFuncs = buildMap();

    public static Module process(Module m) {
        var self = new EABIArithmeicLowing();
        addBuiltinFuncs(m);
        for(var func: m.funcs) {
            for (var bb: func.bbs) {
                self.visitBasicBlock(bb);
            }
        }
        return m;
    }

    private static Map<String, Func> buildMap() {
        var ret = new HashMap<String, Func>();
        var func = new Func("__aeabi_mymod", FuncType.INT, List.of(
            new ParamValue("numerator", Type.Int),
            new ParamValue("denominator", Type.Int)
        ));
        ret.put("srem", func);
        return ret;
    }

    public static void addBuiltinFuncs(Module m) {
        for(var ent: extFuncs.entrySet()) {
            m.builtins.add(ent.getValue());
        }
    }

    public void visitBasicBlock(BasicBlock bb) {
        for (var inst: bb.insts) {
            if (inst instanceof BinopInst ) {
                var key = ((BinopInst)inst).op.toString(((BinopInst)inst).opType.baseType.isFloat());
                if (extFuncs.containsKey(key)) {
                    var func = extFuncs.get(key);
                    var op1 = inst.oprands.get(0);
                    var op2 = inst.oprands.get(1);
                    var call = new CallInst.Builder(inst.parent, func.getValue()).addArg(op1.value).addArg(op2.value).build();
                    inst.removeAllUseFromValue(); // 移除对operands的使用
                    inst.replaceAllUseWith(call); // 其他使用该指令的指令
                    // 从基本块中移除
                    var ind = bb.insts.indexOf(inst);
                    assert ind != -1;
                    bb.insts.set(ind, call);
                }
            }
        }
    }
}
