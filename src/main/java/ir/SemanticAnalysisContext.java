package ir;

import ir.ds.Module;
import ir.ds.Scope;

public class SemanticAnalysisContext {

    public Module module;
    public Scope curScope;

    public SemanticAnalysisContext(Module module) {
        this.module = module;
        this.curScope = module.globalScope;
    }

    public void enterScope() {
        this.curScope = this.curScope.enter();
    }

    public void leaveScope() {
        this.curScope = this.curScope.leave();
    }

}
