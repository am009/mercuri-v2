package dst.ds;

import java.util.List;

public class IfStatement extends BlockStatement{
    public Expr condition;
    public List<BlockStatement> thenBlock;
    public List<BlockStatement> elseBlock;
}
