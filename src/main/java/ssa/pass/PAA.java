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
import ssa.ds.FuncValue;
import ssa.ds.GetElementPtr;
import ssa.ds.GlobalVariable;
import ssa.ds.Instruction;
import ssa.ds.LoadInst;
import ssa.ds.MemPhiInst;
import ssa.ds.Module;
import ssa.ds.PlaceHolder;
import ssa.ds.StoreInst;
import ssa.ds.Type;
import ssa.ds.Value;

public class PAA {

    public static ArrayList<ArrayDefUses> arrays = new ArrayList<>();

    private static Module m;
    /**
     * 全局值被哪些用户函数所依赖
     */
    private static HashMap<GlobalVariable, HashSet<Func>> glob2userFunc = new HashMap<>();
    /**
     * 反向索引，函数依赖了哪些全局值
     */
    private static HashMap<Func, HashSet<GlobalVariable>> func2relatedGlobs = new HashMap<>();
    private static HashSet<Func> visitedFunc = new HashSet<>();

    private static HashMap<Instruction, Boolean> hasAlias = new HashMap<>();
    private static HashMap<BasicBlock, Boolean> visitedBlocks = new HashMap<>();

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
    // pointer 通常是一个数组的 Alloca, 或者 GEP, 或者 LoadInst 或者 GlobalVariable
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
            if (pointer instanceof AllocaInst && ((AllocaInst) pointer).type.isArray()) {
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
        if(arr1 == arr2) {
            return true;
        }
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

        var funcGlobs = func2relatedGlobs.get(callinst.target());
        if (isGlobal(arr) && funcGlobs.contains(arr)) {
            return true;
        }

        for (var arg : callinst.args()) {
            if (arg.value instanceof GetElementPtr) {
                GetElementPtr GetElementPtr = (GetElementPtr) arg.value;
                if (alias(arr, getArrayValue(GetElementPtr))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void runLoadDependStore(Func Func) {
        HashMap<Value, Integer> array2id = new HashMap<>();
        ArrayList<ArrayList<BasicBlock>> array2DefBlk = new ArrayList<>();

        // initialize
        // 此处确定了 array 的 load 处（use）
        for (var bb : Func.bbs) {
            for (var inst : bb.insts) {
                if (inst instanceof LoadInst) {
                    var loadInst = (LoadInst) inst;
                    if (loadInst.getPtr() instanceof AllocaInst) {
                        AllocaInst allocaInst = (AllocaInst) loadInst.getPtr();
                        if (!allocaInst.type.isPointer) {
                            continue;
                        }
                    }
                    // FIXME: 确定一下，这里好像不一定是数组，全局变量（指针）也算
                    Value array = getArrayValue(loadInst.getOperand0());
                    if (array2id.get(array) == null) {
                        ArrayDefUses newArray = new ArrayDefUses(array);
                        arrays.add(newArray);
                        array2id.put(array, arrays.size() - 1);
                        array2DefBlk.add(new ArrayList<>());
                    }
                    arrays.get(array2id.get(array)).loads.add(loadInst);
                }
            }
        }
        // 针对所有 array, 分析它的定值点
        for (ArrayDefUses arrayDefUse : arrays) {
            Value array = arrayDefUse.array;
            int index = array2id.get(array);
            for (var bb : Func.bbs) {
                for (var inst : bb.insts) {
                    if (inst instanceof StoreInst) {
                        StoreInst storeInst = (StoreInst) inst;
                        if (alias(array, getArrayValue(storeInst.getPtr()))) {
                            hasAlias.put(storeInst, true);
                            arrayDefUse.defs.add(storeInst);
                            array2DefBlk.get(index).add(bb);
                        }
                    } else if (inst instanceof CallInst) {
                        FuncValue func = (FuncValue) inst.getOperand0();
                        CallInst callInst = (CallInst) inst;
                        if (func.func.hasSideEffect && callAlias(array, callInst)) {
                            hasAlias.put(callInst, true);
                            arrayDefUse.defs.add(callInst);
                            array2DefBlk.get(index).add(bb);
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
        HashMap<MemPhiInst, Integer> phi2Array = new HashMap<>();
        for (ArrayDefUses arrayDefUse : arrays) {
            Value array = arrayDefUse.array;
            int index = array2id.get(array);

            for (var bb : Func.bbs) {
                visitedBlocks.put(bb, false);
            }

            W.addAll(array2DefBlk.get(index));

            while (!W.isEmpty()) {
                BasicBlock bb = W.remove();
                for (BasicBlock y : bb.domiFrontier) {
                    if (visitedBlocks.get(y)) {
                        continue;
                    }
                    visitedBlocks.put(bb, true);
                    MemPhiInst memPhiInst = new MemPhiInst(y, y.pred().size() + 1);
                    bb.insts.add(0, memPhiInst);
                    phi2Array.put(memPhiInst, index);
                    if (!array2DefBlk.get(index).contains(y)) {
                        W.add(y);
                    }
                }
            }
        }

        ArrayList<Value> array2Value = new ArrayList<>();
        for (int i = 0; i < arrays.size(); i++) {
            array2Value.add(new PlaceHolder());
        }
        for (var bb : Func.bbs) {
            visitedBlocks.put(bb, false);
        }

        Stack<RenameData> renameDataStack = new Stack<>();
        renameDataStack.push(new RenameData(Func.entry(), null, array2Value));
        while (!renameDataStack.isEmpty()) {
            RenameData data = renameDataStack.pop();
            ArrayList<Value> currValues = new ArrayList<>(data.values);
            if(currValues.isEmpty()) {
                continue;
            }
            for (var inst : data.bb.insts) {
                if (!(inst instanceof MemPhiInst)) {
                    break;
                }

                MemPhiInst memPhiInst = (MemPhiInst) inst;
                int predIndex = data.bb.pred().indexOf(data.pred);
                memPhiInst.setIncomingVals(predIndex, data.values.get(phi2Array.get(memPhiInst)));
            }

            if (visitedBlocks.get(data.bb)) {
                continue;
            }
            visitedBlocks.put(data.bb, true);
            for (var inst : data.bb.insts) {

                if (inst instanceof MemPhiInst) {
                    MemPhiInst memPhiInst = (MemPhiInst) inst;
                    int index = phi2Array.get(memPhiInst);
                    currValues.set(index, memPhiInst);
                } else if (inst instanceof LoadInst) {
                    // set useStore as corresponding value
                    LoadInst loadInst = (LoadInst) inst;
                    Integer index = array2id.get(getArrayValue(loadInst.getPtr()));
                    if (index == null) {
                        continue;
                    }
                    loadInst.useStore = currValues.get(index);
                } else if (inst instanceof StoreInst || inst instanceof CallInst) {
                    Integer index = null;
                    for (ArrayDefUses arrayDefUse : arrays) {
                        if (arrayDefUse.defs.contains(inst)) {
                            index = array2id.get(arrayDefUse.array);
                            if (index != null) {
                                currValues.set(index, inst);
                            }
                        }
                    }
                }
            }

            for (BasicBlock bb : data.bb.succ()) {
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
        HashMap<MemPhiInst, Integer> phiToLoadMap = new HashMap<>();
        for (ArrayDefUses array : arrays) {
            for (LoadInst loadInst : array.loads) {
                loads.add(loadInst);
                int index = loads.size() - 1;
                loadsLookup.put(loadInst, index);

                for (var bb : Func.bbs) {
                    visitedBlocks.put(bb, false);
                }

                W.add(loadInst.parent);

                while (!W.isEmpty()) {
                    BasicBlock bb = W.remove();
                    for (BasicBlock y : bb.domiFrontier) {
                        if (!visitedBlocks.get(y)) {
                            visitedBlocks.put(bb, true);
                            MemPhiInst memPhiInst = new MemPhiInst(y, y.pred().size() + 1);
                            bb.insts.add(0, memPhiInst);
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
            values.add(new Value());
        }
        for (var bb : Func.bbs) {
            visitedBlocks.put(bb, false);
        }

        Stack<RenameData> renameDataStack = new Stack<>();
        renameDataStack.push(new RenameData(Func.entry(), null, values));
        while (!renameDataStack.isEmpty()) {
            RenameData data = renameDataStack.pop();
            ArrayList<Value> currValues = new ArrayList<>(data.values);

            // mem-phi update incoming values
            for (var inst : data.bb.insts) {
                if (!(inst instanceof MemPhiInst)) {
                    break;
                }

                MemPhiInst memPhiInst = (MemPhiInst) inst;
                Integer index = phiToLoadMap.get(memPhiInst);
                if (index != null) {
                    int predIndex = data.bb.pred().indexOf(data.pred);
                    memPhiInst.setIncomingVals(predIndex, data.values.get(index));
                }
            }

            if (visitedBlocks.get(data.bb)) {
                continue;
            }
            visitedBlocks.put(data.bb, true);

            // construct LoadDepInst
            var it = data.bb.insts.iterator();
            while (it.hasNext()) {
                var inst = it.next();
                if (inst instanceof MemPhiInst) {
                    var memPhiInst = (MemPhiInst) inst;
                    var index = phiToLoadMap.get(memPhiInst);
                    if (index != null) {
                        currValues.set(index, memPhiInst);
                    }
                }
                if (inst instanceof LoadInst) {
                    LoadInst loadInst = (LoadInst) inst;
                    currValues.set(loadsLookup.get(loadInst), loadInst);
                }
                if (inst instanceof StoreInst || inst instanceof CallInst) {
                    if (inst instanceof StoreInst) {
                        var storeInst = (StoreInst) inst;
                        if (null == hasAlias.get(storeInst)) {
                            continue;
                        }
                    }
                    if (inst instanceof CallInst) {
                        var callInst = (CallInst) (inst);
                        if (null == hasAlias.get(callInst)) {
                            continue;
                        }
                    }
                    for (var memInst : currValues) {
                        if (!(memInst instanceof PlaceHolder)) {
                            //   LoadDepInst loadDepInst = new LoadDepInst(inst, TAG_.LoadDep, factory.getVoidTy(),
                            //       1);
                            //   loadDepInst.setLoadDep(memInst);
                        }
                    }
                }
            }

            for (BasicBlock bb : data.bb.succ()) {
                renameDataStack.push(new RenameData(bb, data.bb, currValues));
            }
        }

        while (true) {
            boolean clear = true;
            for (var bb : Func.bbs) {
                var it = bb.insts.iterator();
                while (it.hasNext()) {
                    var inst = it.next();
                    if (!(inst instanceof MemPhiInst)) {
                        break;
                    }
                    MemPhiInst memPhi = (MemPhiInst) inst;
                    if (memPhi.getUses().isEmpty() || memPhi.getUses().get(0) == null) {
                        bb.removeInst(memPhi);
                        clear = false;
                    }
                }
            }
            if (clear) {
                break;
            }
        }
    }

    private static void calcGlobalVariableUserFuncs() {
        for (var glob : m.globs) {
            var parents = new HashSet<Func>();
            for (var use : glob.getUses()) {
                var user = use.user;
                if (user instanceof Instruction) {
                    var inst = (Instruction) user;
                    if (inst.parent == null) {
                        assert (false);
                    }
                    if (inst.parent.owner == null) {
                        assert (false);
                    }
                    var func = inst.parent.owner;
                    //FIXME: 为啥 ayame 要排除 main
                    parents.add(func);
                } else {
                    assert (false);
                }
            }
            glob2userFunc.put(glob, parents);
        }
    }

    private static boolean bfsFuncs(Func start, GlobalVariable gv) {
        if (visitedFunc.contains(start)) {
            return false;
        }
        visitedFunc.add(start);
        if (glob2userFunc.get(gv).contains(start)) {
            return true;
        }
        var result = false;
        for (var callee : start.callees) {
            result |= bfsFuncs(callee, gv);
        }
        return result;
    }

    private static void calcFun2relatedGlobs() {
        for(var func: m.builtins) {
            func2relatedGlobs.put(func, new HashSet<GlobalVariable>());
        }
        for (var func : m.funcs) {
            func2relatedGlobs.put(func, new HashSet<GlobalVariable>());
            for (var glob : m.globs) {
                visitedFunc.clear();
                if (bfsFuncs(func, glob)) {
                    func2relatedGlobs.get(func).add(glob);
                }
            }
        }
    }

    public static void run(Func Func) {
        DomInfo.computeDominanceInfo(Func);
        DomInfo.computeDominanceFrontier(Func);

        m = Func.owner;
        glob2userFunc = new HashMap<>();
        visitedFunc = new HashSet<>();
        func2relatedGlobs = new HashMap<>();
        calcGlobalVariableUserFuncs();
        calcFun2relatedGlobs();

        arrays = new ArrayList<>();
        runLoadDependStore(Func);
        runStoreDependLoad(Func);
    }

    public static void clear(Func Func) {
        for (var bb : Func.bbs) {

            for (var inst : bb.insts) {

                if (inst instanceof MemPhiInst) {
                    bb.removeInst(inst);
                }
                if (inst instanceof LoadInst) {
                    LoadInst loadInst = (LoadInst) inst;
                    loadInst.useStore = null;
                }
                if (inst instanceof StoreInst) {
                    StoreInst storeInst = (StoreInst) inst;
                    hasAlias.remove(storeInst);
                }
                if (inst instanceof CallInst) {
                    CallInst callInst = (CallInst) inst;
                    hasAlias.remove(callInst);
                }
            }
        }

        for (var bb : Func.bbs) {
            var it = bb.insts.iterator();
            while (it.hasNext()) {
                var inst = it.next();
                if (inst instanceof MemPhiInst) {
                    bb.removeInst(inst);
                }
            }
        }
    }
}
