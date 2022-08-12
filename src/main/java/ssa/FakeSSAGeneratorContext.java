package ssa;

import java.util.HashMap;
import java.util.Map;

import dst.ds.LoopStatement;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.BasicBlockValue;
import ssa.ds.Func;
import ssa.ds.FuncValue;
import ssa.ds.Instruction;
import ssa.ds.Module;
import ssa.ds.TerminatorInst;
import ssa.ds.Value;

public class FakeSSAGeneratorContext {
    public Module module;

    // 由于引用关系已经被语义分析填好了，所以不需要区分作用域。不同函数肯定是不同的Decl
    // 等效于Func和Decl中多了一个Value成员。
    // map from dst to created ssa value  (key compared by instance)

    // 从 dst decl 到 alloca 指令的映射，或者常量到 ConstantValue 的映射等等
    public Map<dst.ds.Decl, Value> varMap;
    public Map<dst.ds.Func, FuncValue> funcMap; // including builtin funcs
    // 全局变量由于直接取的是地址，所以单独分开
    public Map<dst.ds.Decl, Value> globVarMap;
    // 等效于 LoopStatement 多了一个 Value 成员，保存 break 和 Continue 需要的 BasicBlockValue
    // 通过此表查询某个 LoopStatement break 或 continue 后跳到哪儿
    public Map<LoopStatement, BasicBlockValue> breakMap;
    public Map<LoopStatement, BasicBlockValue> continueMap;

    // 方便指令生成时，指定从哪个基本块继续生成。
    public Func currentFunc;
    public BasicBlock current;

    public FakeSSAGeneratorContext(Module module) {
        this.module = module;
        varMap = new HashMap<>();
        funcMap = new HashMap<>();
        globVarMap = new HashMap<>();
        breakMap = new HashMap<>();
        continueMap = new HashMap<>();
    }
    /**
     * 将指令追加到当前上下文的基本块
     */
    public Instruction addToCurrentBB(Instruction i) {
        if (i instanceof TerminatorInst && current.hasTerminator()) {
            // 只能生成到一个新的空基本块了
            var newBB = new BasicBlock("tmp_"+nextBBIdx());
            currentFunc.bbs.add(newBB);
            newBB.insts.add(i);
            current = newBB;
            return i;
        } else {
            return current.addBeforeTerminator(i);
        }
    }

    /**
     * 将(Alloca)指令放到函数的entry基本块开头的一堆Alloca指令后面。
     */
    public AllocaInst addAllocaToEntry(AllocaInst i) {
        int ind = 0;
        var list = currentFunc.entry().insts;
        for (var inst: list) {
            if (!(inst instanceof AllocaInst)) {
                break;
            }
            ind += 1;
        }
        list.add(ind, i);
        return i;
    }

    /**
     * 以下函数生成新的数字编号
     */

    int strInd = 0;
    public int nextStrIdx() {
        return strInd++;
    }

    int BBInd = 0;
    public int nextBBIdx() {
        return BBInd++;
    }

    int varInd = 0;
    public int nextVarIdx() {
        return varInd++;
    }

    // 根据标识符 id 生成一个名字，以一个不冲突的数字为后缀
    public String nameLocal(String id) {
        if (id.length() > 10) {
            id = id.substring(0,10);
        }
        return id+"_"+nextVarIdx();
    }

}
