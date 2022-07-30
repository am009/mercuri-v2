package backend.arm;

import backend.AsmFunc;
import backend.AsmGlobalVariable;
import backend.StackOperand;
import ssa.ds.ConstantValue;

public class AsmPrinter {
    public static String header = ".macro MOV32 reg, target\n"
        +"\tMOVW \\reg, #:lower16:\\target\n"
        +"\tMOVT \\reg, #:upper16:\\target\n"
        +".endm\n\n";

    public static String textHeader = ".text\n"
        +".syntax unified\n"
        // + ".cpu cortex-a72\n"
        +".arch armv7-a\n"
        +".arch_extension idiv\n"
        +".arch_extension vfpv3-fp16\n"
        +".file 1 \"%s\"\n\n" // 这里%s会不会有转义的问题，比如结尾是反斜杠，然后链接器那边报错
        +"__aeabi_mymod:\n"
        +"\tpush\t{r11, lr}\n"
        +"\tbl\t__aeabi_idivmod\n"
        +"\tmov\tr0, r1\n"
        +"\tpop\t{r11, lr}\n"
        +"\tbx\tlr\n\n";

    public static String funcHeader = "\t.global\t%s\n"
        +"\t.type\t%s, %%function\n"
        +"\t.p2align\t2\n"
        +"\t.code\t32\n"
        +"%s:\n";

    public static String varHeader = ".global\t%s\n"
        +"\t.type\t%s, %%object\n"
        +"\t.size\t%s, %d\n";

    static String emitConstant(ConstantValue init) {
        if(! init.isArray()) { // 简单情况，单个数字
            return String.format("\t.%s\t%s\t\t@ %s %s \n", init.type.baseType.toAsmString(), init.valToAsmString(), init.val.getClass().getName(), init.val.toString());
        } else {
            var sb = new StringBuilder();
            sb.append("\t@ {\n");
            for (var child: init.children) {
                sb.append(emitConstant(child));
            }
            sb.append("\t@ }\n");
            return sb.toString();
        }
    }

    public static String emitGlobVar(AsmGlobalVariable gv) {
        var sb = new StringBuilder();
        var ssaGv = gv.ssaGlob;
    
        // visiable to linker
        // 链接器符号相关
        sb.append(String.format(varHeader, ssaGv.name, ssaGv.name, ssaGv.name, gv.size));
        // sb.append(".global\t").append(ssaGv.name).append("\n");
        // sb.append("\t.type\t").append(ssaGv.name).append(", %object\n");
        // sb.append("\t.size\t").append(ssaGv.name).append(", ").append(gv.size).append("\n");
        
        // label
        sb.append(ssaGv.name).append(":\n");
    
        // type comment
        sb.append("\t@ ").append(ssaGv.varType.toString()).append("\n");
    
        if (ssaGv.init != null) {
            sb.append(emitConstant(ssaGv.init));
        } else {
            sb.append(String.format("\t.zero\t%s\n", gv.size));
        }
    
        return sb.toString();
    }

    public static String emitFunc(AsmFunc func) {
        var sb = new StringBuilder();
        sb.append(String.format(funcHeader, func.label, func.label, func.label));
        for (var bb: func) {
            sb.append(bb.toString());
        }

        return sb.toString();
    }

    public static String emitStackOperand(StackOperand stackOperand) {
        var offset = stackOperand.offset;
        Reg.Type base;
        switch (stackOperand.type) {
            case SPILL:
                offset = -offset;
                base = Reg.Type.fp;
                break;
            case LOCAL:
                offset = -offset;
                base = Reg.Type.fp;
                break;

            case CALL_PARAM:
                base = Reg.Type.sp;
                break;
            case SELF_ARG:
                base = Reg.Type.fp;
                break;
            default:
                throw new UnsupportedOperationException();
        }
        return String.format("%s, #%s", base.toString(), common.Util.toSignedHexString(offset));
    }
    
}
