package backend;

/** Low address, stack grow upwards
 * ┌─────────────┐
 * │             │
 * │             │
 * │space for arg│- sp
 * │     ...     │
 * │ spilled reg │-12
 * │     ...     │-a
 * │ alloca local│-4
 * ├─   fp/lr   ─┤0 - fp
 * │    fp/lr    │+4
 * │    arg1     │+8
 * │    arg2     │+12
 * │    ...      │
 * ├─────────────┤
 * │             │
 * High address
 * 所以要注意，SELF_ARG的偏移要+8跳过push的sp和fp
 * LOCAL要先加地址再返回偏移
 * CALL_PARAM要先返回偏移再增加，对应CallingConvention的解析
 */
public class StackOperand extends AsmOperand {
    public enum Type {
        LOCAL, // fp - xx
        SPILL, // fp - xx
        CALL_PARAM, // sp + xx
        SELF_ARG, // fp + xx
        ;
    }
    public Type type;
    public long offset;

    public StackOperand(Type ty, long offset) {
        isFloat = false;
        type = ty;
        this.offset = offset;
    }

    @Override
    public String toString() {
        return backend.arm.AsmPrinter.emitStackOperand(this);
    }
}
