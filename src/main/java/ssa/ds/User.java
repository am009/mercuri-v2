package ssa.ds;

import java.util.ArrayList;
import java.util.List;

public class User extends Value{
    public List<Use> oprands = new ArrayList<>();

    public void removeAllUseFromValue() {
        for (var u: oprands) {
            assert u.user == this;
            u.value.removeUse(u);
        }
    }

    public void replaceUseWith(Use oldu, Use newu) {
        int ind = oprands.indexOf(oldu);
        assert ind != -1;
        oprands.set(ind, newu);
    }
}

