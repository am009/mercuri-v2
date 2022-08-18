package ssa.pass;

import java.util.List;

public class MemDepAnalysis {
    // 例子：[2][3] 是 [1][2][3] 的别名
    public static boolean isDimAlias(List<Integer> dim1, List<Integer> dim2) {
        if(dim1.size() > dim2.size() ) {
            return isDimAlias(dim2, dim1);
        }
        for(int i = dim1.size(); i >= 0; i--) {
            if(dim1.get(i) != dim2.get(i)) {
                return false;
            }
        }
        return true;
    }
}
