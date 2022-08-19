package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public String name;
    public List<Func> funcs;
    public List<GlobalVariable> globs;
    public List<Func> builtins;

    public Module(String name) {
        this.name = name;
        funcs = new ArrayList<>();
        globs = new ArrayList<>();
        builtins = new ArrayList<>();
    }

    public String toString() {
        var builder = new StringBuilder("source_filename = \"" + name + "\"\n\n");
        builtins.forEach(func -> builder.append(func.toString()));
        builder.append("\n");
        globs.forEach(gv -> builder.append(gv.toString()).append("\n"));
        builder.append("\n");
        funcs.forEach(func -> builder.append(func.toString()).append("\n\n"));
        return builder.toString();
    }

    public Func getMain() {
        for (var func : funcs) {
            if (func.name == "main") {
                return func;
            }
        }
        assert false : "no main func";
        return null;
    }
}
