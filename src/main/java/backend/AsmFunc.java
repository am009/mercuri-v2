package backend;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import backend.arm.Reg;
import backend.arm.VfpReg;
import ssa.ds.Func;

public class AsmFunc implements Iterable<AsmBlock> {
    public String label;
    // 基本块之间用双向链表连接，代表在内存中的顺序分布，方便操作。（因为部分跳转指令依赖其中一个目标就放在后面）
    public AsmBlock entry;

    // bbs应该不常用，遍历直接用链表。bbs方便用来判断基本块是否属于函数，以及获取基本块数量。
    public List<AsmBlock> bbs;
    public Func ssaFunc;

    public StackManager sm;

    // 寄存器分配的起始约束
    public List<Map.Entry<Reg, VirtReg>> argConstraint;
    public List<Map.Entry<VfpReg, VirtReg>> fpArgConstraint;

    public AsmFunc(Func ssaFunc) {
        this.ssaFunc = ssaFunc;
        label = ssaFunc.name;
        this.bbs = new ArrayList<>();
        sm = new StackManager();
    }

    @Override
    public Iterator<AsmBlock> iterator() {
        Iterator<AsmBlock> it = new Iterator<AsmBlock>() {

            private AsmBlock current = entry;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public AsmBlock next() {
                var ret = current;
                current = current.next;
                return ret;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        return it;
    }

    @Override
    public String toString() {
        return backend.arm.AsmPrinter.emitFunc(this);
    }
}
