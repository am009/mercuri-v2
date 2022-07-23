package backend.arm;

import java.util.ArrayList;
import java.util.List;

import backend.AsmOperand;
import backend.StackOperand;
import dst.ds.FuncType;
import ssa.ds.PrimitiveTypeTag;
import ssa.ds.Type;

/**
 * Calling Convention的解析结果。
 */
public class VfpCallingConvention implements CallingConvention {
    // 结果有几种情况：1是r0-r3，2是在s0-s15，3是在内存里（StackOperand）。
    public List<AsmOperand> callParam;
    // 由于中间push了FP和LR，所以对于内存变量，访问的offset会有所不同
    public List<AsmOperand> selfArg;
    public AsmOperand retReg; // s0或者r0
    public long ncrn = 0;
    public long nsaa = 0; // 计算结束后也是需要占用的栈空间大小
    public long nextVfp = 0;

    public VfpCallingConvention() {
        callParam = new ArrayList<>();
        selfArg = new ArrayList<>();
    }

    public VfpCallingConvention resolve(List<Type> params, FuncType retTy) {
        if (retTy == FuncType.FLOAT) {
            retReg = new VfpReg(0);
        } else if (retTy == FuncType.INT) {
            retReg = new Reg(Reg.Type.r0);
        }

        for (var param: params) {
            assert param.baseType != PrimitiveTypeTag.DOUBLE;
            long size = 4;
            if ((!param.isPointer) && param.baseType.equals(PrimitiveTypeTag.FLOAT)) { // if is VFP CPRC (Co-processor Register Candidate)
                if (nextVfp < 16) {
                    var result = new VfpReg(nextVfp);
                    callParam.add(result);
                    selfArg.add(result);
                    nextVfp += 1;
                } else {
                    assert nextVfp == 16;
                    var result = new StackOperand(StackOperand.Type.CALL_PARAM, nsaa);
                    callParam.add(result);
                    // 
                    selfArg.add(new StackOperand(StackOperand.Type.SELF_ARG, nsaa + 8));
                    nsaa += size;
                }
            } else {
                if ((ncrn + (size / 4)) <= 4) { // 寄存器能分配下
                    var result = new Reg(Reg.Type.values[Math.toIntExact(ncrn)]);
                    callParam.add(result);
                    selfArg.add(result);
                    ncrn = ncrn + (size / 4);
                } else {
                    assert ncrn == 4;
                    callParam.add(new StackOperand(StackOperand.Type.CALL_PARAM, nsaa));
                    selfArg.add(new StackOperand(StackOperand.Type.SELF_ARG, nsaa + 8));
                    nsaa += size;
                }
            }
        }
        return this;
    }

    @Override
    public AsmOperand addParam(Type t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getStackSize() {
        return nsaa;
    }

    @Override
    public AsmOperand getRetReg() {
        return retReg;
    }
}
