package ssa.pass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;
import java.util.stream.Collectors;

import dst.ds.BasicType;
import ssa.ds.AllocaInst;
import ssa.ds.BasicBlock;
import ssa.ds.CallInst;
import ssa.ds.ConstantValue;
import ssa.ds.Func;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.Module;
import ssa.ds.PrimitiveTypeTag;
import ssa.ds.StoreInst;
import ssa.ds.Type;
import ssa.ds.Value;

public class PAA {

    public static ArrayList<ArrayDefUses> arrays = new ArrayList<>();

    private static Module m;
    private static HashMap<GlobalVariable, ArrayList<Func>> gvUserFunc = new HashMap<>();
    private static HashMap<Func, HashSet<GlobalVariable>> func2relatedGVs = new HashMap<>();
    private static HashSet<Func> visitfunc = new HashSet<>();

    public static class ArrayDefUses {

        public Value array;
        public ArrayList<LoadInst> loads;
        public ArrayList<Instruction> defs;

        public ArrayDefUses() {
            this.loads = new ArrayList<>();
            this.defs = new ArrayList<>();
        }

        public ArrayDefUses(Value array) {
            this.array = array;
            this.loads = new ArrayList<>();
            this.defs = new ArrayList<>();
        }
    }

    private static class RenameData {

        public BasicBlock bb;
        public BasicBlock pred;
        public ArrayList<Value> values;

        public RenameData(BasicBlock bb, BasicBlock pred, ArrayList<Value> values) {
            this.bb = bb;
            this.pred = pred;
            this.values = values;
        }
    }

    // Use array as memory unit
    public static Value getArrayValue(Value pointer) {
        while (pointer instanceof GetElementPtr || pointer instanceof LoadInst) {
            if (pointer instanceof GetElementPtr) {
                pointer = ((Instruction) pointer).getOperand0();
            } else {
                pointer = ((LoadInst) pointer).getOperand0();
            }
        }
        // pointer should be an AllocaInst or GlobalVariable
        if (pointer instanceof AllocaInst || pointer instanceof GlobalVariable) {
            // TODO： 我们这里是不是 pointer？
            // 如果是，那么不应该用 getAllocaType，应该用 getType
            if (pointer instanceof AllocaInst && ((AllocaInst) pointer).type.isPointer) {
                for (var use : pointer.getUses()) {
                    if (use.user instanceof StoreInst) {
                        pointer = ((StoreInst) use.user).getPtr();
                    }
                }
            }
            return pointer;
        } else {
            return null;
        }
    }

    public static boolean isGlobal(Value array) {
        return array instanceof GlobalVariable;
    }

    public static boolean isParam(Value array) {
        // allocaType 为 i32ptr，表示是一个参数数组
        if (array instanceof AllocaInst) {
            AllocaInst allocaInst = (AllocaInst) array;
            return allocaInst.type.isPointer;
        }
        return false;
    }

    public static boolean isLocal(Value array) {
        return !isGlobal(array) && !isParam(array);
    }

    public static boolean isGlobalArray(Value array) {
        if (!isGlobal(array)) {
            return false;
        }
        var gv = (GlobalVariable) array;
        return !gv.isConst && gv.varType.isArray();
    }

    public static boolean isParamArrayAliasOfGlobalArray(Value globalArray, Value paramArray) {
        if (!isGlobal(globalArray) || !isParam(paramArray)) {
            return false;
        }
        ArrayList<Integer> dimsGlob = new ArrayList<>();
        ArrayList<Integer> dimsParam = new ArrayList<>();

        ConstantValue globalArr = ((GlobalVariable) globalArray).init;
        dimsGlob.addAll(globalArray.type.dims);
        int dimNumGlob = dimsGlob.size();
        for (var i = dimNumGlob - 2; i >= 0; i--) {
            dimsGlob.set(i, dimsGlob.get(i) * dimsGlob.get(i + 1));
        }

        AllocaInst allocaInst = (AllocaInst) paramArray;
        Type ptrTy = allocaInst.type;
        // TODO: 看不懂下面什么意思
        // if (ptrTy.baseType == PrimitiveTypeTag.FLOAT || ptrTy.baseType == PrimitiveTypeTag.INT) {
        // return true;
        // }
        if (!ptrTy.isPointer) {
            return true;
        }
        dimsParam.add(0);
        dimsParam.addAll(paramArray.type.dims);
        int dimNumParam = dimsParam.size();

        for (var i = dimNumParam - 2; i >= 0; i--) {
            dimsParam.set(i, dimsParam.get(i) * dimsParam.get(i + 1));
        }

        // dims从右向左累乘
        boolean allSame = true;
        var minDim = Math.min(dimNumGlob, dimNumParam);
        for (var i = 0; i < minDim; i++) {
            // dims2[0] 始终为 0
            if (i == 0 && minDim == dimNumParam) {
                continue;
            }
            allSame = dimsGlob.get(i + dimNumGlob - minDim) == dimsParam.get(i + dimNumParam - minDim);
        }
        return allSame;
    }

    public static boolean alias(Value arr1, Value arr2) {
        // 都是param: 名字相等
        // param - glob: dim_alias
        // global - global: AllocaInst 相同
        // local - local: AllocaInst 相同
        if ((isGlobal(arr1) && isGlobal(arr2)) || (isParam(arr1) && isParam(arr2)) || (isLocal(arr1)
                && isLocal(arr2))) {
            return arr1 == arr2;
        }
        if (isGlobal(arr1) && isParam(arr2) && ((GlobalVariable) arr1).init != null) {
            return isParamArrayAliasOfGlobalArray(arr1, arr2);
        }
        if (isParam(arr1) && isGlobal(arr2) && ((GlobalVariable) arr2).init != null) {
            return isParamArrayAliasOfGlobalArray(arr2, arr1);
        }
        return false;
    }

    public static boolean callAlias(Value arr, CallInst callinst) {
        if (isParam(arr)) {
            return true;
        }

        if (isGlobal(arr) && func2relatedGVs.get(callinst.target()).contains(arr)) {
            return true;
        }

        for (Value arg : callinst.getOperands()) {
            if (arg instanceof GetElementPtr) {
                GetElementPtr GetElementPtr = (GetElementPtr) arg;
                if (alias(arr, getArrayValue(GetElementPtr))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void runLoadDependStore(Func Func) {
        HashMap<Value, Integer> arraysLookup = new HashMap<>();
        ArrayList<ArrayList<BasicBlock>> defBlocks = new ArrayList<>();

        // initialize
        for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
            BasicBlock bb = bbNode.getVal();
            for (INode<Instruction, BasicBlock> instNode : bb.getList()) {
                Instruction inst = instNode.getVal();
                if (inst.tag == TAG_.Load) {
                    LoadInst loadInst = (LoadInst) inst;
                    if (loadInst.getPointer() instanceof AllocaInst) {
                        AllocaInst allocaInst = (AllocaInst) loadInst.getPointer();
                        if (allocaInst.getAllocatedType().equals(factory.getI32Ty())) {
                            continue;
                        }
                    }
                    Value array = getArrayValue(loadInst.getOperands().get(0));
                    if (arraysLookup.get(array) == null) {
                        ArrayDefUses newArray = new ArrayDefUses(array);
                        arrays.add(newArray);
                        arraysLookup.put(array, arrays.size() - 1);
                        defBlocks.add(new ArrayList<>());
                    }
                    arrays.get(arraysLookup.get(array)).loads.add(loadInst);
                }
            }
        }

        for (ArrayDefUses arrayDefUse : arrays) {
            Value array = arrayDefUse.array;
            int index = arraysLookup.get(array);
            for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
                BasicBlock bb = bbNode.getVal();
                for (INode<Instruction, BasicBlock> instNode : bb.getList()) {
                    Instruction inst = instNode.getVal();
                    // 这里对 Load/Store/Call 进行分组，粒度决定了后面分析的精度和速度
                    if (inst.tag == TAG_.Store) {
                        StoreInst storeInst = (StoreInst) inst;
                        // FIXME: 传参进来的数组，不能对着 alloca 的地方 alias，不然会出现对传进来的数组的 store 影响了对 alloca 的地方的指针的 load
                        if (alias(array, getArrayValue(storeInst.getOperands().get(1)))) {
                            storeInst.hasAlias = true;
                            arrayDefUse.defs.add(storeInst);
                            defBlocks.get(index).add(bb);
                        }
                    } else if (inst.tag == TAG_.Call) {
                        Func func = (Func) inst.getOperands().get(0);
                        CallInst callInst = (CallInst) inst;
                        if (func.isHasSideEffect() && callAlias(array, callInst)) {
                            callInst.hasAlias = true;
                            arrayDefUse.defs.add(callInst);
                            defBlocks.get(index).add(bb);
                        }
                    }
                }
            }

            //      if (arrayDefUse.defs.isEmpty() && isGlobalArray(array)) {
            //        ((GlobalVariable)array).setConst();
            //      }
        }

        // insert mem-phi-instructions
        Queue<BasicBlock> W = new LinkedList<>();
        HashMap<MemPhi, Integer> phiToArrayMap = new HashMap<>();
        for (ArrayDefUses arrayDefUse : arrays) {
            Value array = arrayDefUse.array;
            int index = arraysLookup.get(array);

            for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
                bbNode.getVal().setDirty(false);
            }

            W.addAll(defBlocks.get(index));

            while (!W.isEmpty()) {
                BasicBlock bb = W.remove();
                for (BasicBlock y : bb.getDominanceFrontier()) {
                    if (!y.isDirty()) {
                        y.setDirty(true);
                        MemPhi memPhiInst = new MemPhi(TAG_.MemPhi, factory.getVoidTy(),
                                y.getPredecessor_().size() + 1, array, y);
                        phiToArrayMap.put(memPhiInst, index);
                        if (!defBlocks.get(index).contains(y)) {
                            W.add(y);
                        }
                    }
                }
            }
        }

        ArrayList<Value> values = new ArrayList<>();
        for (int i = 0; i < arrays.size(); i++) {
            values.add(new UndefValue());
        }
        for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
            bbNode.getVal().setDirty(false);
        }

        Stack<RenameData> renameDataStack = new Stack<>();
        renameDataStack.push(new RenameData(Func.getList_().getEntry().getVal(), null, values));
        while (!renameDataStack.isEmpty()) {
            RenameData data = renameDataStack.pop();
            ArrayList<Value> currValues = new ArrayList<>(data.values);
            for (INode<Instruction, BasicBlock> instNode : data.bb.getList()) {
                Instruction inst = instNode.getVal();
                if (inst.tag != TAG_.MemPhi) {
                    break;
                }

                MemPhi memPhiInst = (MemPhi) inst;
                int predIndex = data.bb.getPredecessor_().indexOf(data.pred);
                memPhiInst.setIncomingVals(predIndex, data.values.get(phiToArrayMap.get(memPhiInst)));
            }

            if (data.bb.isDirty()) {
                continue;
            }
            data.bb.setDirty(true);
            for (INode<Instruction, BasicBlock> instNode : data.bb.getList()) {
                Instruction inst = instNode.getVal();
                if (inst.tag == TAG_.MemPhi) {
                    MemPhi memPhiInst = (MemPhi) inst;
                    int index = phiToArrayMap.get(memPhiInst);
                    currValues.set(index, memPhiInst);
                } else if (inst.tag == TAG_.Load) {
                    // set useStore as corresponding value
                    LoadInst loadInst = (LoadInst) inst;
                    Integer index = arraysLookup.get(getArrayValue(loadInst.getPointer()));
                    if (index == null) {
                        continue;
                    }
                    loadInst.setUseStore(currValues.get(index));
                } else if (inst.tag == TAG_.Store || inst.tag == TAG_.Call) {
                    Integer index = null;
                    for (ArrayDefUses arrayDefUse : arrays) {
                        if (arrayDefUse.defs.contains(inst)) {
                            index = arraysLookup.get(arrayDefUse.array);
                            if (index != null) {
                                currValues.set(index, inst);
                            }
                        }
                    }
                }
            }

            for (BasicBlock bb : data.bb.getSuccessor_()) {
                renameDataStack.push(new RenameData(bb, data.bb, currValues));
            }
        }
    }

    // avoid gcm breaks the dependence
    public static void runStoreDependLoad(Func Func) {
        ArrayList<LoadInst> loads = new ArrayList<>();
        HashMap<LoadInst, Integer> loadsLookup = new HashMap<>();
        //    ArrayList<ArrayList<BasicBlock>> defBlocks = new ArrayList<>();

        // insert mem-phi-instructions
        Queue<BasicBlock> W = new LinkedList<>();
        HashMap<MemPhi, Integer> phiToLoadMap = new HashMap<>();
        for (ArrayDefUses array : arrays) {
            for (LoadInst loadInst : array.loads) {
                loads.add(loadInst);
                int index = loads.size() - 1;
                loadsLookup.put(loadInst, index);

                for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
                    bbNode.getVal().setDirty(false);
                }

                W.add(loadInst.getBB());

                while (!W.isEmpty()) {
                    BasicBlock bb = W.remove();
                    for (BasicBlock y : bb.getDominanceFrontier()) {
                        if (!y.isDirty()) {
                            y.setDirty(true);
                            MemPhi memPhiInst = new MemPhi(TAG_.MemPhi, factory.getVoidTy(),
                                    y.getPredecessor_().size() + 1, new UndefValue(), y);
                            phiToLoadMap.put(memPhiInst, index);
                            W.add(y);
                        }
                    }
                }
            }
        }

        // construct LoadDepInst
        ArrayList<Value> values = new ArrayList<>();
        for (int i = 0; i < loads.size(); i++) {
            values.add(new UndefValue());
        }
        for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
            bbNode.getVal().setDirty(false);
        }

        Stack<RenameData> renameDataStack = new Stack<>();
        renameDataStack.push(new RenameData(Func.getList_().getEntry().getVal(), null, values));
        while (!renameDataStack.isEmpty()) {
            RenameData data = renameDataStack.pop();
            ArrayList<Value> currValues = new ArrayList<>(data.values);

            // mem-phi update incoming values
            for (var instNode : data.bb.getList()) {
                var inst = instNode.getVal();
                if (inst.tag != TAG_.MemPhi) {
                    break;
                }

                MemPhi memPhiInst = (MemPhi) inst;
                Integer index = phiToLoadMap.get(memPhiInst);
                if (index != null) {
                    int predIndex = data.bb.getPredecessor_().indexOf(data.pred);
                    memPhiInst.setIncomingVals(predIndex, data.values.get(index));
                }
            }

            if (data.bb.isDirty()) {
                continue;
            }
            data.bb.setDirty(true);

            // construct LoadDepInst
            for (var instNode = data.bb.getList().getEntry(); instNode != null;) {
                var tmp = instNode.getNext();
                var inst = instNode.getVal();
                switch (inst.tag) {
                    case MemPhi -> {
                        MemPhi memPhiInst = (MemPhi) inst;
                        Integer index = phiToLoadMap.get(memPhiInst);
                        if (index != null) {
                            currValues.set(index, memPhiInst);
                        }
                    }
                    case Load -> {
                        LoadInst loadInst = (LoadInst) inst;
                        currValues.set(loadsLookup.get(loadInst), loadInst);
                    }
                    case Store, Call -> {
                        if ((inst.tag == TAG_.Store && ((StoreInst) inst).hasAlias) || (inst.tag == TAG_.Call
                                && ((CallInst) inst).hasAlias)) {
                            for (var memInst : currValues) {
                                if (!memInst.getName().equals("UndefValue")) {
                                    LoadDepInst loadDepInst = new LoadDepInst(inst, TAG_.LoadDep, factory.getVoidTy(),
                                            1);
                                    loadDepInst.setLoadDep(memInst);
                                }
                            }
                        }
                    }
                }
                instNode = tmp;
            }

            for (BasicBlock bb : data.bb.getSuccessor_()) {
                renameDataStack.push(new RenameData(bb, data.bb, currValues));
            }
        }

        while (true) {
            boolean clear = true;
            for (var bbNode : Func.getList_()) {
                var bb = bbNode.getVal();
                for (var instNode = bb.getList().getEntry(); instNode != null;) {
                    var tmp = instNode.getNext();
                    var inst = instNode.getVal();

                    if (!(inst instanceof MemPhi)) {
                        break;
                    }
                    MemPhi memPhi = (MemPhi) inst;
                    if (memPhi.getUsesList().isEmpty() || memPhi.getUsesList().get(0) == null) {
                        memPhi.node.removeSelf();
                        memPhi.CORemoveAllOperand();
                        clear = false;
                    }

                    instNode = tmp;
                }
            }
            if (clear) {
                break;
            }
        }
    }

    private static void loadUserFuncs() {
        m.__globalVariables.forEach(
                gv -> {
                    ArrayList<Func> parents = new ArrayList<>();
                    for (Use use : gv.getUsesList()) {
                        var func = ((Instruction) use.getUser()).getBB().getParent();
                        if (!(func.getCallerList().isEmpty() && !func.getName().equals("main"))) {
                            parents.add(((Instruction) use.getUser()).getBB().getParent());
                        }
                    }
                    parents = (ArrayList<Func>) parents.stream().distinct().collect(Collectors.toList());
                    gvUserFunc.put(gv, parents);
                });
    }

    private static boolean bfsFuncs(Func start, GlobalVariable gv) {
        if (visitfunc.contains(start)) {
            return false;
        }
        visitfunc.add(start);
        if (gvUserFunc.get(gv).contains(start)) {
            return true;
        }
        var result = false;
        for (Func callee : start.getCalleeList()) {
            result |= bfsFuncs(callee, gv);
        }
        return result;
    }

    private static void findRelatedFunc() {
        for (INode<Func, Module> Func : m.__Funcs) {
            var val = Func.getVal();
            func2relatedGVs.put(val, new HashSet<>());
            for (GlobalVariable globalVariable : m.__globalVariables) {
                visitfunc.clear();
                if (bfsFuncs(val, globalVariable)) {
                    func2relatedGVs.get(val).add(globalVariable);
                }
            }
        }
    }

    public static void run(Func Func) {
        DomInfo.computeDominanceInfo(Func);
        DomInfo.computeDominanceFrontier(Func);

        m = Func.getNode().getParent().getVal();
        gvUserFunc = new HashMap<>();
        visitfunc = new HashSet<>();
        func2relatedGVs = new HashMap<>();
        loadUserFuncs();
        findRelatedFunc();

        arrays = new ArrayList<>();
        runLoadDependStore(Func);
        runStoreDependLoad(Func);
    }

    public static void clear(Func Func) {
        for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
            BasicBlock bb = bbNode.getVal();
            for (INode<Instruction, BasicBlock> instNode : bb.getList()) {
                Instruction inst = instNode.getVal();
                switch (inst.tag) {
                    case MemPhi, LoadDep -> {
                        inst.CORemoveAllOperand();
                        inst.COReplaceAllUseWith(null);
                    }
                    case Load -> {
                        LoadInst loadInst = (LoadInst) inst;
                        if (loadInst.getNumOP() == 2) {
                            loadInst.removeUseStore();
                        }
                    }
                    case Store -> {
                        StoreInst storeInst = (StoreInst) inst;
                        storeInst.hasAlias = false;
                    }
                    case Call -> {
                        CallInst callInst = (CallInst) inst;
                        callInst.hasAlias = false;
                    }
                }
            }
        }

        for (INode<BasicBlock, Func> bbNode : Func.getList_()) {
            BasicBlock bb = bbNode.getVal();
            for (var instNode = bb.getList().getEntry(); instNode != null;) {
                var tmp = instNode.getNext();
                Instruction inst = instNode.getVal();
                if (inst instanceof MemPhi || inst instanceof LoadDepInst) {
                    instNode.removeSelf();
                }
                instNode = tmp;
            }
        }
    }
}
