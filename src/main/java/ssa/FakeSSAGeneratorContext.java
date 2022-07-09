package ssa;

import java.util.HashMap;
import java.util.Map;

import ssa.ds.Module;
import ssa.ds.Value;

public class FakeSSAGeneratorContext {
    public Module module;
    // map from dst to created ssa value  (key compared by instance)
    public Map<dst.ds.Decl, Value> var_map;
    public Map<dst.ds.Func, Value> func_map;

    public FakeSSAGeneratorContext(Module module) {
        this.module = module;
        var_map = new HashMap<>();
        func_map = new HashMap<>();
    }

}
