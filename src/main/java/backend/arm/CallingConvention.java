package backend.arm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import backend.AsmOperand;
import ssa.ds.ParamValue;

/**
 * Calling Convention的解析结果。
 */
public class CallingConvention {
    // 结果有几种情况：1是r0-r3，2是在s0-s15，3是在内存里（StackOperand）。
    public Map<ParamValue, AsmOperand> result;

    public CallingConvention() {
        result = new HashMap<>();
    }

    public AsmOperand getLoc(ParamValue pv) {
        return result.get(pv);
    }

    public static CallingConvention resolveVFP(List<ParamValue> params) {
        return null;
    }

    public static CallingConvention resolveBase(List<ParamValue> params) {
        return null;
    }

}
