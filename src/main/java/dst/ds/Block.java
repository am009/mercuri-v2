package dst.ds;

import java.util.ArrayList;
import java.util.List;

public class Block extends BlockStatement{
    public List<BlockStatement> statements;

    public Block(List<BlockStatement> statements) {
        this.statements = statements;
    }

    public static Block Empty = new Block(new ArrayList<BlockStatement>());
}
