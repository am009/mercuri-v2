package backend.arm;

import java.util.ArrayList;
import java.util.List;

import backend.AsmOperand;
import backend.StackOperand;
import dst.ds.FuncType;
import ssa.ds.PrimitiveTypeTag;
import ssa.ds.Type;

public class BaseCallingConvention implements CallingConvention {
    public List<AsmOperand> callParam;
    // 由于目前只有vararg的函数，只需要调用，不需要支持这种函数的生成，因此暂时不增加用于函数访问自身参数的结果。
    public AsmOperand retReg; // s0或者r0

    long ncrn = 0;
    long nsaa = 0; // 计算结束后也是需要占用的栈空间大小

    public BaseCallingConvention() {
        callParam = new ArrayList<>();
    }

    // https://github.com/ARM-software/abi-aa/blob/60a8eb8c55e999d74dac5e368fc9d7e36e38dda4/aapcs32/aapcs32.rst#id50
    // vararg时不使用任何vfp寄存器传参
    // For a variadic function the base standard is always used both for argument passing and result return.
    public BaseCallingConvention resolve(List<Type> params, FuncType retTy) {
        if (retTy != FuncType.VOID) {
            retReg = new Reg(Reg.Type.r0);
        }
        for (var param: params) {
            addParam(param);
        }
        return this;
    }

    // 为了方便可变参函数，需要动态计算
    public AsmOperand addParam(Type t) {
        AsmOperand ret;
        // 仅有的需要8字节对齐的情况：vararg传入被提升到double的float
        long size = 4;
        if ((!t.isPointer) && t.baseType.equals(PrimitiveTypeTag.DOUBLE)) {
            size = 8;
            ncrn = (ncrn+1)/2* 2;
            if (ncrn >= 4) { nsaa = (nsaa+7)/8*8; }
        }
        if ((ncrn + (size / 4)) <= 4) { // 寄存器能分配下
            ret = new Reg(Reg.Type.values[Math.toIntExact(ncrn)]);
            callParam.add(ret);
            ncrn = ncrn + (size / 4);
        } else {
            assert ncrn == 4;
            ret = new StackOperand(StackOperand.Type.CALL_PARAM, nsaa);
            callParam.add(ret);
            nsaa += size;
        }
        return ret;
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
