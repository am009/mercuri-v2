package dst.ds;


public class LoopStatement extends BlockStatement{
    public LogicExpr condition;
    public Block thenBlock;

    public LoopStatement(LogicExpr condition, Block thenBlock) {
        this.condition = condition;
        this.thenBlock = thenBlock;
    }
}
