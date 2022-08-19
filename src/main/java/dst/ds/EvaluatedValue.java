package dst.ds;

public class EvaluatedValue {
    public BasicType basicType;
    // For simple values, the value is stored in this field.
    public Integer intValue;
    public Float floatValue;
    public String stringValue;

    // For array values
    /**
     * int arr[2][3][4] = {1, 2, 3, 4, {5}, {6}, {7, 8}};
     *
     * int arr[2][3][4] = {
     * {{1, 2, 3, 4}, {5, 0, 0, 0}, {6, 0, 0, 0}},
     * {{7, 8, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}
     * };
     * 
     * EvaluatedValue of arr[2][3][4] is
     * [
     * EvaluatedValue of 0.arr[3][4],
     * EvaluatedValue of 1.arr[3][4]
     * ]
     * where EvaluatedValue of 0.arr[3][4] is [
     * EvaluatedValue of 0.arr[4]
     * EvaluatedValue of 1.arr[4]
     * EvaluatedValue of 2.arr[4]
     * ]
     * where EvaluatedValue of 0.arr[4] is [
     * EvaluatedValue of 0.arr = 1,
     * EvaluatedValue of 1.arr = 2,
     * EvaluatedValue of 2.arr = 3,
     * EvaluatedValue of 3.arr = 4,
     * ]
     */

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

    public static EvaluatedValue ofString(String text) {
        EvaluatedValue evaluatedValue = new EvaluatedValue();
        evaluatedValue.basicType = BasicType.STRING_LITERAL;
        evaluatedValue.stringValue = text;
        return evaluatedValue;
    }

    public static EvaluatedValue getDefault(BasicType b) {
        if (b == BasicType.INT) {
            return EvaluatedValue.ofInt(0);
        } else if (b == BasicType.FLOAT) {
            return EvaluatedValue.ofFloat(0);
        } else if (b == BasicType.STRING_LITERAL) {
            return EvaluatedValue.ofString("");
        }
        throw new UnsupportedOperationException();
    }

    public boolean isDefault() {
        if (basicType == BasicType.INT) {
            return intValue.equals(0);
        } else if (basicType == BasicType.FLOAT) {
            // return floatValue.equals(0.0f);
            return Math.signum(floatValue) == 0;
        } else if (basicType == BasicType.STRING_LITERAL) {
            return stringValue.equals("");
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        switch (basicType) {
            case FLOAT:
                return String.valueOf(floatValue);
            case INT:
                return String.valueOf(intValue);
            case STRING_LITERAL:
                return stringValue;
            default:
                return super.toString();

        }
    }

    public static EvaluatedValue fromOperation(EvaluatedValue eval, UnaryOp op) {
        if (eval == null) {
            return null;
        }
        switch (op) {
            case NEG:
                if (eval.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(-eval.intValue);
                } else if (eval.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(-eval.floatValue);
                }
                return null;
            case NOT:
                if (eval.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(!(eval.intValue > 0) ? 1 : 0);
                } else if (eval.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(!(eval.floatValue > 0) ? 1 : 0);
                }
                return null;
            default:
                break;
        }
        return null;
    }

    public static EvaluatedValue fromOperation(EvaluatedValue left, EvaluatedValue right, BinaryOp op) {
        if (left == null || right == null) {
            return null;

        }

        switch (op) {
            case ADD:
                // int + int or float + float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue + right.intValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.floatValue + right.floatValue);
                }
                // int + float or float + int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.intValue + right.floatValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofFloat(left.floatValue + right.intValue);
                }
                return null;
            case SUB:
                // int - int or float - float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue - right.intValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.floatValue - right.floatValue);
                }
                // int - float or float - int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.intValue - right.floatValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofFloat(left.floatValue - right.intValue);
                }
                return null;
            case MUL:
                // int * int or float * float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue * right.intValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.floatValue * right.floatValue);
                }
                // int * float or float * int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.intValue * right.floatValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofFloat(left.floatValue * right.intValue);
                }
                return null;
            case DIV:
                // int / int or float / float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue / right.intValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.floatValue / right.floatValue);
                }
                // int / float or float / int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    if (right.floatValue == 0) {
                        throw new RuntimeException("Division by zero");
                    }
                    return EvaluatedValue.ofFloat(left.intValue / right.floatValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    if (right.intValue == 0) {
                        throw new RuntimeException("Division by zero");
                    }
                    return EvaluatedValue.ofFloat(left.floatValue / right.intValue);
                }
                return null;
            case MOD:
                // int % int or float % float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue % right.intValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofFloat(left.floatValue % right.floatValue);
                }
                // int % float or float % int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    if (right.floatValue == 0) {
                        throw new RuntimeException("Division by zero");
                    }
                    return EvaluatedValue.ofFloat(left.intValue % right.floatValue);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    if (right.intValue == 0) {
                        throw new RuntimeException("Division by zero");
                    }
                    return EvaluatedValue.ofFloat(left.floatValue % right.intValue);
                }
                return null;

            case LOG_LT:
                // int < int or float < float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue < right.intValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.floatValue < right.floatValue ? 1 : 0);
                }
                // int < float or float < int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.intValue < right.floatValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.floatValue < right.intValue ? 1 : 0);
                }
                return null;
            case LOG_GT:
                // int > int or float > float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue > right.intValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.floatValue > right.floatValue ? 1 : 0);
                }
                // int > float or float > int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.intValue > right.floatValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.floatValue > right.intValue ? 1 : 0);
                }
                return null;
            case LOG_LE:
                // int <= int or float <= float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue <= right.intValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.floatValue <= right.floatValue ? 1 : 0);
                }
                // int <= float or float <= int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.intValue <= right.floatValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.floatValue <= right.intValue ? 1 : 0);
                }
                return null;
            case LOG_GE:

                // int >= int or float >= float
                if (left.basicType == BasicType.INT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.intValue >= right.intValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.floatValue >= right.floatValue ? 1 : 0);
                }
                // int >= float or float >= int
                if (left.basicType == BasicType.INT && right.basicType == BasicType.FLOAT) {
                    return EvaluatedValue.ofInt(left.intValue >= right.floatValue ? 1 : 0);
                } else if (left.basicType == BasicType.FLOAT && right.basicType == BasicType.INT) {
                    return EvaluatedValue.ofInt(left.floatValue >= right.intValue ? 1 : 0);
                }
                return null;
            case LOG_AND:
            case LOG_OR:
                assert false: "Logical operators are converted";
            case LOG_EQ:
            case LOG_NEQ:
                Number leftNum, rightNum;
                Boolean leftBool, rightBool;
                if (left.basicType == BasicType.INT) {
                    leftNum = left.intValue;
                    leftBool = left.intValue != 0;
                } else if (left.basicType == BasicType.FLOAT) {
                    leftNum = left.floatValue;
                    leftBool = left.floatValue != 0;
                } else {
                    throw new RuntimeException("Invalid type for left operand");
                }
                if (right.basicType == BasicType.INT) {
                    rightNum = right.intValue;
                    rightBool = right.intValue != 0;
                } else if (right.basicType == BasicType.FLOAT) {
                    rightNum = right.floatValue;
                    rightBool = right.floatValue != 0;
                } else {
                    throw new RuntimeException("Invalid type for left operand");
                }
                switch (op) {
                    case LOG_AND:
                        return EvaluatedValue.ofInt(leftBool && rightBool ? 1 : 0);
                    case LOG_OR:
                        return EvaluatedValue.ofInt(leftBool || rightBool ? 1 : 0);
                    case LOG_EQ:
                        return EvaluatedValue.ofInt(leftBool == rightBool ? 1 : 0);
                    case LOG_NEQ:
                        return EvaluatedValue.ofInt(leftBool != rightBool ? 1 : 0);
                    default:
                        return null;
                }
            default:
                break;
        }
        return null;
    }
}
