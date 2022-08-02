package ssa.ds;

import ds.Global;

/**
 * Store 指令向 alloca 申请到的栈内存中写入数据
 * 
 * %i = alloca i32
 * store i32 0, i32* %i
 * 
 * store 指令两个参数：
 * 1. 要 store 的值
 * 2. 要 store 到的地址
 */
public class StoreInst extends Instruction {

    public StoreInst() {
        type = Type.Void;
    }

    public static class Builder {
        private StoreInst inst;

        public Builder(BasicBlock parent) {
            inst = new StoreInst();
            inst.parent = parent;
        }
        /**
         * 设置 Store 指令的参数
         * @param val 要 store 什么值
         * @param ptr 要 store 到哪儿
         * @return
         */
        public Builder addOperand(Value val, Value ptr) {
            inst.oprands.add(new Use(inst, val));
            inst.oprands.add(new Use(inst, ptr));
            if (!StoreInst.checkType(val.type, ptr.type)) {
                Global.logger.warning("Store Inst type mismatch !!! "+this.toString());
            }
            return this;
        }

        public StoreInst build() {
            return inst;
        }
    }

    public static boolean checkType(Type val, Type ptr) {
        // 必须 store 到地址
        if (!ptr.isPointer){
            return false;
        }
        Type ty = ptr.clone();
        ty.isPointer = false;
        // 地址解引用后必须是 val 的同类型
        return ty.equals(val);
    }

    // store <ty> <value>, <ty>* <pointer>
    @Override
    public String toString() {
        var b = new StringBuilder("store ");
        var v1 = oprands.get(0).value;
        var v2 = oprands.get(1).value;
        b.append(v1.type.toString());
        b.append(" ").append(v1.toValueString());
        b.append(", ").append(v2.type.toString());
        b.append(" ").append(v2.toValueString());
        return b.toString();
    }
}
