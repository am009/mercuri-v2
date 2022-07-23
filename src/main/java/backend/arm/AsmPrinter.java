package backend.arm;

import backend.AsmFunc;
import backend.AsmGlobalVariable;
import ssa.ds.ConstantValue;

public class AsmPrinter {
    public static String header = ".macro MOV32 reg, target\n"
        +"\tMOVW \\reg, #:lower16:\\target\n"
        +"\tMOVT \\reg, #:upper16:\\target\n"
        +".endm\n\n";

    public static String textHeader = ".text\n"
        +".syntax unified\n"
        +".file 1 \"%s\"\n\n"; // 这里%s会不会有转义的问题，比如结尾是反斜杠，然后链接器那边报错

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
        sb.append(varHeader.formatted(ssaGv.name, ssaGv.name, ssaGv.name, gv.size));
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
        sb.append(funcHeader.formatted(func.label, func.label, func.label));
        for (var bb: func) {
            sb.append(bb.toString());
        }

        return sb.toString();
    }
    
}