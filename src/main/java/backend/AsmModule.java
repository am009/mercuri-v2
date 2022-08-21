package backend;

import java.util.ArrayList;
import java.util.List;

import ssa.ds.Module;

public class AsmModule{
    public String name;
    public List<AsmFunc> funcs;
    public List<AsmGlobalVariable> dataGlobs;
    // bss段存放初始值全零的变量
    public List<AsmGlobalVariable> bssGlobs;
    public Module ssaModule;

    public AsmModule(String name, Module ssaModule) {
        this.ssaModule = ssaModule;
        this.name = name;
        funcs = new ArrayList<>();
        dataGlobs = new ArrayList<>();
        bssGlobs = new ArrayList<>();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        // 打印需要用到的宏：目前不用了
        // sb.append(backend.arm.AsmPrinter.header);
        // 打印.text
        sb.append(String.format(backend.arm.AsmPrinter.textHeader, name));
        
        // 打印函数
        for(var func: funcs) {
            sb.append(func.toString());
        }
        
        // 打印.data段全局变量
        if (dataGlobs.size() > 0) {
            sb.append("\n.data\n");
            sb.append(".align 4\n");
            for (var gv: dataGlobs) {
                sb.append(gv.toString());
            }
        }
        // 打印.bss段全局变量
        if (bssGlobs.size() > 0) {
            sb.append("\n.bss\n");
            sb.append(".align 4\n");
            for (var gv: bssGlobs) {
                sb.append(gv.toString());
            }
        }

        return sb.toString();
    }

}