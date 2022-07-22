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
 * ├─   bp/lr   ─┤0 - bp
 * │    bp/lr    │+4
 * │    arg1     │+8
 * │    arg2     │+12
 * │    ...      │
 * ├─────────────┤
 * │             │
 * High address
 */
public class StackOperand extends AsmOperand {
    public enum Type {
        LOCAL, // bp - xx
        SPILL, // bp - local - xx
        CALL_ARG, // sp + xx
        SELF_ARG, // bp + xx
        ;
    }
    long offset;
    String comment;
}
