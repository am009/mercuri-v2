package ssa;

import java.util.HashMap;
import java.util.Map;

import dst.ds.LoopStatement;
import ssa.ds.BasicBlock;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.Value;

public class FakeSSAGeneratorContext {
    public Module module;
    // 由于引用关系已经被语义分析填好了，所以不需要区分作用域。不同函数肯定是不同的Decl
    // 等效于Func和Decl中多了一个Value成员。
    // map from dst to created ssa value  (key compared by instance)
    public Map<dst.ds.Decl, Value> varMap;
    public Map<dst.ds.Func, Value> funcMap;
    // 全局变量由于直接取的是地址，所以单独分开
    public Map<dst.ds.Decl, Value> globVarMap;
    // 等效于LoopStatement多了一个Value成员，保存break和Continue需要的BasicBlockValue
    public Map<LoopStatement, Value> breakMap;
    public Map<LoopStatement, Value> continueMap;

    // 方便指令生成时，指定从哪个基本块继续生成。
    public BasicBlock current;

    public FakeSSAGeneratorContext(Module module) {
        this.module = module;
        varMap = new HashMap<>();
        funcMap = new HashMap<>();
        globVarMap = new HashMap<>();
    }

    public Instruction addToCurrent(Instruction i) {
        return current.addBeforeJump(i);
    }

    int strInd = 0;
    public int getStrInd() {
        return strInd++;
    }

}
