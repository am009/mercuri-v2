package backend;

/**
 * 作为AsmFunc成员管理函数栈上空间分配，配合StackOperand使用。
 * 要求分配Local（指令选择阶段），在分配spill前完成（寄存器分配阶段）
 * Low address, stack grow upwards
 * ┌─────────────┐
 * │             │
 * │             │
 * │space for arg│- sp
 * │     ...     │
 * │ spilled reg │-12
 * │     ...     │-a
 * │ alloca local│-4
 * ├─   bp/lr   ─┤0 - bp
 * │    bp/lr    │+4
 * │    arg1     │+8
 * │    arg2     │+12
 * │    ...      │
 * ├─────────────┤
 * │             │
 * High address
 */
public class StackManager {
    boolean allocaFinished = false;
    long localSize = 0;
    long spillSize = 0;
    long maxArgSize = 0;

    // 返回的是相对BP的offset，使用时：bp-offset
    public long allocLocal(long size) {
        if (allocaFinished) {
            throw new RuntimeException("Cannot alloc alloca space after spill space");
        }
        localSize += size;
        return localSize;
    }

    // 返回的是相对BP的offset，使用时：bp-offset
    public long allocSpill(long size) {
        allocaFinished = true;
        spillSize += size;
        return spillSize+localSize;
    }

    public void preserveArgSize(long size) {
        if (size > maxArgSize) {
            maxArgSize = size;
        }
    }

    public long totalStackSize() {
        long ret = localSize+spillSize+maxArgSize;
        // 向上取整到8的倍数
        ret = (ret + 7) / 8 * 8;
        return ret;
    }

}
