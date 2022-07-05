package ir.ds;

import java.util.ArrayList;
import java.util.List;

import dst.ds.Func;

public class FuncSymbol implements Symbol{
    public Func func;

    // public final List<BasicBlock> basicBlocks = new ArrayList<>();
    // public final List<FuncSymbol> caller = new ArrayList<>();
    // public final List<FuncSymbol> callee = new ArrayList<>();

    public FuncSymbol(Func func) {
        this.func = func;
    }

    @Override
    public String getName() {
        return func.id;
    }
    
}
