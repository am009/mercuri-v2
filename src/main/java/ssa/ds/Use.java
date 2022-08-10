package ssa.ds;

// Use relationship from user to value
public class Use {
    public User user;
    public Value value;
    public int index;

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
        this.value.addUse(this);
        this.index = user.oprands.size();
    }
}
