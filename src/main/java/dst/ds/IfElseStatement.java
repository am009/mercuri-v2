package dst.ds;


public class IfElseStatement extends BlockStatement{
    public LogicExpr condition;
    public Block thenBlock;
    public Block elseBlock;

    public IfElseStatement(LogicExpr condition, Block thenBlock, Block elseBlock) {
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBlock = elseBlock;
    }
}
