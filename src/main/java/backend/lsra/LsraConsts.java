package backend.lsra;

public class LsraConsts {

    // 通用寄存器数量
    public static final int CORE_REG_COUNT = 11;
    // 浮点寄存器的序号偏移, 也即, 如果要得到 0 号浮点寄存器, 则其实编号是 16
    public static final int VFP_REG_OFFSET = 16;
    // 浮点寄存器数量
    public static final int VFP_REG_COUNT = 32;
    public static final int PHY_REG_UPBOUND = VFP_REG_OFFSET + VFP_REG_COUNT - 1;
    // 栈上寄存器(spill)
    public static final int SPILL_OFFSET = 128;
    public static final int SPILL_COUNT = Integer.MAX_VALUE;
}
