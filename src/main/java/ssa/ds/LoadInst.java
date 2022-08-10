package ssa.ds;

public class LoadInst extends Instruction {
    
    public LoadInst(BasicBlock p, Value ptr) {
        parent = p;
        oprands.add(new Use(this, ptr));
        assert ptr.type.isPointer;
        type = ptr.type.clone();
        type.isPointer = false;
    }

    public Value getPtr() {
        return oprands.get(0).value;
    }

    // load [volatile] <ty>, <ty>* <pointer>
    @Override
    public String getOpString() {
        assert type.isPointer == false;
        var b = new StringBuilder("load ");
        b.append(type.toString()).append(", ");
        type.isPointer = true;
        b.append(type.toString());
        type.isPointer = false;
        return b.toString();
    }

}
