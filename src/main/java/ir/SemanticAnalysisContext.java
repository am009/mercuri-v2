package ir;

import java.util.LinkedList;
import java.util.List;

import ds.Global;
import dst.ds.LoopStatement;
import ir.ds.Module;
import ir.ds.Scope;

public class SemanticAnalysisContext {

    public Module module;
    /** Current scope */
    public Scope scope;
    /** Current loop stack */
    public List<LoopStatement> loopStack = new LinkedList<>();

    public SemanticAnalysisContext(Module module) {
        this.module = module;
        this.scope = module.globalScope;
    }

    public void enterScope() {
        this.scope = this.scope.enter();
        Global.logger.trace("enter scope");
    }

    public void leaveScope() {
        this.scope = this.scope.leave();
        Global.logger.trace("leave scope");
    }

    public void enterLoop(LoopStatement loop) {
        this.loopStack.add(loop);
    }

    public void leaveLoop() {
        this.loopStack.remove(this.loopStack.size() - 1);
    }

    public boolean inLoop() {
        return !this.loopStack.isEmpty();
    }

    public LoopStatement currentLoop() {
        return this.loopStack.get(this.loopStack.size() - 1);
    }

    public boolean inFunc() {
        return !this.scope.isGlobal();
    }
}
