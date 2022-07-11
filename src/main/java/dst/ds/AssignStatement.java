package dst.ds;

import java.util.List;

import ir.ds.DeclSymbol;

public class AssignStatement extends BlockStatement {
    // left
    public LValExpr left;
    // right
    public Expr expr;

    // after semantic analysis, the symbol of the left side of the assignment statement
    public DeclSymbol symbol;

    public AssignStatement(String id, boolean isArray, List<Expr> indexExprs, Expr expr) {
        left = new LValExpr(id, isArray, indexExprs);
        this.expr = expr;
    }

    public AssignStatement(LValExpr lValExpr, Expr expr) {
        left = lValExpr;
        this.expr = expr;
    }
}
