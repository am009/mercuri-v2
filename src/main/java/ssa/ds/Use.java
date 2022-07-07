package ssa.ds;

// Use relationship from user to value
public class Use {
    User user;
    Value value;

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
        this.value.addUse(this);
    }
}
