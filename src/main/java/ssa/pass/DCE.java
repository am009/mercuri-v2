package ssa.pass;

import ssa.ds.Func;
import ssa.ds.Module;

public class DCE {
    Module ssaModule;

    public DCE(Module ssaModule) {
        this.ssaModule = ssaModule;
    }

    public static void process(Module ssaModule) {
        var dce = new DCE(ssaModule);
        dce.execute();
    }

    private void execute() {
        ssaModule.funcs.forEach(func -> this.executeOnFunc(func));
    }

    private void executeOnFunc(Func func) {

    }
}
