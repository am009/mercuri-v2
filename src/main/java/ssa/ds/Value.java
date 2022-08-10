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
            var newu = new Use(u.user, v);
            u.user.replaceUseWith(u, newu);
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
