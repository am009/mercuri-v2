package backend.arm;

import java.util.List;
import java.util.Map;

import backend.AsmOperand;
import ssa.ds.ParamValue;

/**
 * 输入一个AsmFunc，负责解析每个参数所在的位置。
 * 对于普通函数用VFP variant的PCS，对于vararg的还是得用BasePCS，而且float参数还要提升到double。
 */
public class VFPCallingConvention {
    public static Map<ParamValue, AsmOperand> resolve(List<ParamValue> params) {
        return null;
    }
}
