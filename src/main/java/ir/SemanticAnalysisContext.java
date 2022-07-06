package ir;

import ds.Global;
import ir.ds.Module;
import ir.ds.Scope;

public class SemanticAnalysisContext {

    public Module module;
    public Scope curScope;
    public Integer loopLevel;

    public SemanticAnalysisContext(Module module) {
        this.module = module;
        this.curScope = module.globalScope;
    }

    public void enterScope() {
        this.curScope = this.curScope.enter();
        Global.logger.trace("enter scope");
    }

    public void leaveScope() {
        this.curScope = this.curScope.leave();
        Global.logger.trace("leave scope");
    }

    public void enterLoop() {
        this.loopLevel++;
    }

    public void leaveLoop() {
        this.loopLevel--;
    }

    public boolean inLoop() {
        return this.loopLevel > 0;
    }

    public boolean inFunc() {
        return !this.curScope.isGlobal();
    }
}
