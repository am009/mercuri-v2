package dst.ds;

import java.util.List;

public class LoopStatement {
    public Expr condition;
    public List<BlockStatement> thenBlock;

    public LoopStatement(Expr condition, List<BlockStatement> thenBlock) {
        this.condition = condition;
        this.thenBlock = thenBlock;
    }
}
