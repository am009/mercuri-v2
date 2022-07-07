package ssa.ds;

import java.util.ArrayList;
import java.util.List;

import dst.ds.Type;

public class Value {
    public Type type;
    private List<Use> uses = new ArrayList<>();
    public String name;


    public void addUse(Use use) {
        uses.add(use);
    }

    public List<Use> getUses() {
        return uses;
    }

    public void removeUse(Use use) {
        uses.remove(use);
    }

}
