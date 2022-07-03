package dst.ds;

public class EvaluatedValue {
    public BasicType basicType;
    public int intValue;
    public float floatValue;

    public static EvaluatedValue ofInt(int value) {
        EvaluatedValue evaluatedValue = new EvaluatedValue();
        evaluatedValue.basicType = BasicType.INT;
        evaluatedValue.intValue = value;
        return evaluatedValue;
    }

    public static EvaluatedValue ofFloat(float value) {
        EvaluatedValue evaluatedValue = new EvaluatedValue();
        evaluatedValue.basicType = BasicType.FLOAT;
        evaluatedValue.floatValue = value;
        return evaluatedValue;
    }
}
