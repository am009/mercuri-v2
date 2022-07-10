package dst.ds;


// 代表 while(cond) {Block}结构
public class LoopStatement extends BlockStatement{
    public LogicExpr condition;
    public Block bodyBlock;

    public LoopStatement(LogicExpr condition, Block bodyBlock) {
        this.condition = condition;
        this.bodyBlock = bodyBlock;
    }
}
