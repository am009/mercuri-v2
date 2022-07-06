package dst.ds;

import java.util.List;

import ir.ds.DeclSymbol;

public class LValExpr extends Expr {
    public String id;
    public boolean isArray;
    public List<Expr> indices;

    public DeclSymbol declSymbol;

    public LValExpr(String id, boolean isArray, List<Expr> indices) {
        this.id = id;
        this.isArray = isArray;
        this.indices = indices;
    }

    @Override
    public EvaluatedValue eval() {
        return null;
    }
}
