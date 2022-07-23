package backend.arm;

import java.util.List;

import backend.AsmOperand;
import dst.ds.FuncType;
import ssa.ds.Type;

public interface CallingConvention {
    public CallingConvention resolve(List<Type> params, FuncType retTy);

    public AsmOperand addParam(Type t);

    // 参数需要占用的栈空间大小
    public long getStackSize();

    public AsmOperand getRetReg();
}
