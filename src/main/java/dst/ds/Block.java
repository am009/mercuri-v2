package dst.ds;

import java.util.List;

public class Block extends BlockStatement{
    List<BlockStatement> statements;

    public Block(List<BlockStatement> statements) {
        this.statements = statements;
    }
}
