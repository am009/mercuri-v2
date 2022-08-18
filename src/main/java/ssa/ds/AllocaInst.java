package ssa.ds;
/**
 * Alloca 指令用于在栈中分配内存
 */
public class AllocaInst extends Instruction {
    public Type ty;
    public long numElement = 1;
    
    public static class Builder {
        private AllocaInst inst;

        public Builder(BasicBlock parent) {
            inst = new AllocaInst();
            inst.parent = parent;
        }

        public Builder addType(Type ty) {
            assert !ty.isPointer; // 不支持指针
            inst.ty = ty;
            inst.type = inst.ty.clone();
            inst.type.isPointer = true; // 返回值是指针类型
            return this;
        }

        public AllocaInst build() {
            return inst;
        }
    }

    @Override
    public String getOpString() {
        return "alloca " + ty.toString();
    }

    public Type getAllocaType() {
        return ty;
    }
}
