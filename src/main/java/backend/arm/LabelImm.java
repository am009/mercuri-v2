package backend.arm;

public class LabelImm extends Imm {
    enum State {
        LABEL, // 正常的状态
        HIGH, // 取高16位的状态
        LOW
    }
    State state = State.LABEL;
    String label;

    public LabelImm(String label) {
        this.label = label;
    }

    @Override
    public long highestOneBit() {
        return (1L << 31);
    }

    @Override
    public Imm getLow16() {
        var ret = new LabelImm(label);
        ret.state = State.LOW;
        return ret;
    }

    @Override
    public Imm getHigh16() {
        var ret = new LabelImm(label);
        ret.state = State.HIGH;
        return ret;
    }

    @Override
    public String toString() {
        switch (state) {
            case HIGH: return "#:upper16:" + label;
            case LABEL: return label;
            case LOW: return "#:lower16:" + label;
            default: return null;
            
        }
    }

}
