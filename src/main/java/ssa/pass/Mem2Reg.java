package ssa.pass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ds.Global;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.PhiInst;
import ssa.ds.StoreInst;
import ssa.ds.Type;
import ssa.ds.Use;
import ssa.ds.Value;

/**
 * mem2reg会消除简单的alloca，消除load指令，使用点替换为对应store指令保存的值。
 */
public class Mem2Reg {
    public static Module process(Module m) {
        for (var func: m.funcs) {
            var self = new Mem2Reg(func);
            self.doAnalysis();
        }
        return m;
    }

    public static class IncompletePhi {
        public PhiInst phi;
        public BasicBlock bb;
        public AllocaInst ptr;

        public IncompletePhi(PhiInst phi, BasicBlock bb, AllocaInst ptr) {
            this.phi = phi; this.bb = bb; this.ptr = ptr;
        }
    }

    int nameIndex = 0;
    Func func;
    Set<AllocaInst> promotable = new HashSet<>();
    Map<AllocaInst, Map<BasicBlock, Value>> currentDef = new HashMap<>();
    Map<PhiInst, Value> deadPhis = new HashMap<>();
    ArrayList<IncompletePhi> incompletePhis = new ArrayList<>();
    ArrayList<PhiInst> validPhis = new ArrayList<>(); // 解决不能在遍历基本块指令时增加指令的问题，后面统一加入基本块
    Set<BasicBlock> filledBlock = new HashSet<>();

    public Mem2Reg(Func f) {
        this.func = f;
    }

    public void doAnalysis() {
        if (func.isDeclaration()) return;
        // 1. 遍历搜索Alloca指令（目前都在Entry块），收集所有可以提升的alloca（仅被load和store访问）。
        if (!scanPromotable()) return;
        /**
         * 2. 为了避免引用到local value numbering还没搞完的基本块，对于需要前向递归搜索的节点，
         *    插了一个Phi就先停下，直接作为incompletePhi保存，之后都结束了再回来处理（参考mimic）
         */
        for (var bb: func.bbs) { //对基本块顺序的基本要求是，对于只有一个前驱的节点，前驱先于该节点被遍历
            for(var it = bb.insts.iterator(); it.hasNext();) {
                var inst = it.next();
                if (inst instanceof LoadInst) {
                    var load = (LoadInst) inst;
                    if (! promotable.contains(load.getPtr())) {
                        continue;
                    }
                    var ptr = (AllocaInst)load.getPtr();
                    Value v = readVariable(bb, ptr);
                    // inst的use就不用移除了，因为另外一边是alloca，之后也会被移除。
                    inst.replaceAllUseWith(v);
                    it.remove();
                } else if (inst instanceof StoreInst) {
                    var sto = (StoreInst) inst;
                    if (! promotable.contains(sto.getPtr())) {
                        continue;
                    }
                    writeVariable(bb, (AllocaInst)sto.getPtr(), sto.getVal());
                    // remove store instruction
                    // 主要是从sto.getVal()的users中移除自身。
                    inst.removeAllOperandUseFromValue();
                    it.remove();
                }
            }
            filledBlock.add(bb);
        }
        // 3. 处理incompletePhi
        while(incompletePhis.size() > 0) {
            var front = incompletePhis.get(0);
            // front.phi.comments = "from " + front.ptr.toString();
            // front.bb.insts.add(0, front.phi);
            var val = addPhiOperands(front.phi, front.bb, front.ptr);
            // writeVariable(front.bb, front.ptr, val);
            incompletePhis.remove(0);
        }
        // 把所有phi指令加入基本块
        for (var phi: validPhis) {
            phi.parent.insts.add(0, phi);
        }
        // 4. 最后移除alloca
        for (var bb: func.bbs) {
            for(var it = bb.insts.iterator(); it.hasNext();) {
                var inst = it.next();
                if (promotable.contains(inst)) {
                    it.remove();
                }
            }
        }
    }

    private Value addPhiOperands(PhiInst phi, BasicBlock bb, AllocaInst ptr) {
        for (var pred: bb.pred()) {
            var val = readVariable(pred, ptr);
            assert val.type.equals(phi.type);
            phi.oprands.add(new Use(phi, val));
        }
        return tryRemoveTrivialPhi(phi, ptr);
    }

    private Value tryRemoveTrivialPhi(PhiInst phi, AllocaInst ptr) {
        Value same = null;
        for (var use: phi.oprands) {
            var op = use.value;
            if (op == same || op == phi) {
                continue;
            }
            if (same != null) {
                return phi;
            } else {
                same = op;
            }
        }
        if (same == null) { // 所有operand都是phi自己
            same = getUndefValue(phi.type);
        }
        List<PhiInst> toRecursive = new ArrayList<>();
        for (var u: phi.getUses()) {
            if (u.user instanceof PhiInst && u.user != phi) {
                toRecursive.add((PhiInst)u.user);
            }
        }
        phi.replaceAllUseWith(same);
        phi.comments = "replaced by "+same.toValueString();
        // validPhis.remove(phi);
        
        // 在currentDef里也要替换。
        // 但是为了避免每次遍历一遍整个currentDef，使用deadPhis作为一个缓存层，同时使用路径压缩。
        deadPhis.put(phi, same);

        for (var p: toRecursive) {
            tryRemoveTrivialPhi(p, ptr); // TODO 应该会用到我这个phi的只会是同一个变量吧。
        }
        return same;
    }

    private Value readVariable(BasicBlock bb, AllocaInst ptr) {
        var map = currentDef.get(ptr);
        if (map != null && map.containsKey(bb)) {
            var ret = map.get(bb);
            ret = findInDeadPhis(ret);
            return ret;
        }
        return readVariableRecursive(bb, ptr);
    }

    private Value readVariableRecursive(BasicBlock bb, AllocaInst ptr) {
        var pred = bb.pred();
        Value val;
        if (!isSealedBlock(bb)) {
            val = createIncompletePhi(ptr, bb);
            incompletePhis.add(new IncompletePhi((PhiInst)val, bb, ptr));
        } else if (pred.size() == 0) {
            if(bb == func.entry()) {
                Global.logger.error("Possible use of uninitialized variable: %s", ptr);
            } else {
                // 前面运行了死基本块消除，所以应该不会出现这种情况
                Global.logger.warning("possible dead block: %s", bb.getValue().toValueString());
            }
            val = getUndefValue(ptr.getAllocaType());
        }
        else if(pred.size() == 1) {
            val = readVariable(pred.get(0), ptr);
        } else {
            // Break potential cycles with operandless phi
            val = createIncompletePhi(ptr, bb);
            writeVariable(bb, ptr, val);
            val = addPhiOperands((PhiInst)val, bb, ptr);
        }
        writeVariable(bb, ptr, val);
        return val;
    }

    /**
     * 如果基本块有前驱还没有遍历到，则not sealed。
     */
    private boolean isSealedBlock(BasicBlock bb) {
        for (var pred: bb.pred()) {
            if (filledBlock.contains(pred)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private Value getUndefValue(Type ty) {
        Value val;
        assert ty.isBaseType();
        if (ty.baseType.isFloat()) {
            val = ConstantValue.ofFloat(0f);
        } else {
            val = ConstantValue.ofInt(0);
        }
        return val;
    }

    private PhiInst createIncompletePhi(AllocaInst ptr, BasicBlock bb) {
        var phi = new PhiInst(bb);
        // bb.insts.add(0, phi); //此处不能添加，因为影响指令的遍历
        validPhis.add(phi);
        if (ptr.name != null) {
            phi.name = ptr.name + "_" + String.valueOf(nameIndex++);
        }
        phi.type = ptr.getAllocaType();
        return phi;
    }

    private void writeVariable(BasicBlock bb, AllocaInst ptr, Value val) {
        currentDef.computeIfAbsent(ptr, x -> new HashMap<BasicBlock,Value>()).put(bb, val);
    }

    private boolean scanPromotable() {
        for (var inst: func.entry().insts) {
            if (! (inst instanceof AllocaInst)) {
                continue;
            }
            boolean isPromotable = true;
            for (var u: inst.getUses()) {
                // u.value == inst
                // load i8, i8* ptr
                if (u.user instanceof LoadInst) {
                    continue;
                }
                // store val, ptr
                if (u.user instanceof StoreInst && ((StoreInst)u.user).getPtr() == inst) {
                    continue;
                }
                Global.logger.trace("%s not promotable due to %s", inst, u.user);
                isPromotable = false;
                break;
            }
            if (isPromotable) {
                promotable.add((AllocaInst) inst);
            }
        }
        return promotable.size() != 0;
    }

    private Value findInDeadPhis(Value in) {
        if (! (in instanceof PhiInst)) {
            return in;
        }
        if (!deadPhis.containsKey(in)) {
            return in;
        }
        Value current = in;
        ArrayList<PhiInst> toCompress = new ArrayList<>();
        while (deadPhis.containsKey(current)) {
            toCompress.add((PhiInst)current);
            current = deadPhis.get(current);
        }
        // 路径压缩
        for (PhiInst p: toCompress) {
            deadPhis.put(p, current);
        }
        return current;
    }
}
