package ir.ds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Symbol table for IR
 * Ref: Engineering a Compiler, Second Edition, ch 5, pg 255
 */
public class Scope {
    public final Map<String, Symbol> values = new HashMap<>();
    public final List<Scope> children = new ArrayList<>();
    public Scope parent;
    public Scope root;

    // Only for creating a global scope
    public Scope() {
        this.root = this;
    }

    public Scope enter() {
        Scope derived = new Scope();
        derived.root = root;
        derived.parent = this;
        this.children.add(derived);
        return derived;
    }

    public Scope leave() {
        if(this.parent == null){
            throw new IllegalStateException("Cannot leave global scope");
        }
        return this.parent;
    }

    public boolean register(Symbol symbol) {
        if (this.resolve(symbol.getName()) != null) {
            return false;
        }
        values.put(symbol.getName(), symbol);
        return true;
    }

    public Symbol resolve(String name) {
        Symbol symbol = values.get(name);
        if (symbol == null && parent != null) {
            symbol = parent.resolve(name);
        }
        return symbol;
    }

    public boolean isGlobal() {
        return this.parent == null;
    }

}
