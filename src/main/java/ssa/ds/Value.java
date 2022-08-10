package ssa.ds;

import java.util.ArrayList;
import java.util.List;

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

    // TODO replaceAllUseWith
    public void replaceAllUseWith(Value v) {
        for (var u: uses) {
            assert u.value == this;
            u.user.replaceUseWith(u, new Use(u.user, v));
        }
        uses.clear();
    }

    public String toValueString() {
        if (type.baseType == PrimitiveTypeTag.VOID) {
            return "";
        }
        if (name != null) {
            return "%" + name.toString();
        } else {
            return "%?"; // 没有命名
        }
    }
}
