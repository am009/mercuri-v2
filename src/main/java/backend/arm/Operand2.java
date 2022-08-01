package backend.arm;

public class Operand2 {
    // Flexible Operand 2 目前仅当作8bit常量使用
    public static boolean isImmFit(Imm m) {
        if (m.highestOneBit() < 255) {
            return true;
        }
        return false;
    }
}
