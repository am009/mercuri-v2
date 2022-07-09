package dst.ds;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import ir.ds.DeclSymbol;
import ir.ds.Scope;

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
    public EvaluatedValue eval(Scope scope) {
        // DeclSymbol 或者 FuncSymbol，应该只会是DeclSymbol
        var sym = (DeclSymbol) scope.resolve(id);
        if (!isArray) { // 普通的常量变量，如果是数组则会取到null
            return sym.decl.initVal.evaledVal;
        } else { // 数组常量变量取下标
            // 计算下标
            var inds = indices.stream().map(i -> i.eval(scope).intValue).collect(Collectors.toList());

            assert sym.decl.initVal.isArray;
            var current = sym.decl.initVal;
            for (int i: inds) {
                current = current.values.get(i);
            }
            assert !current.isArray;
            return current.evaledVal;
        }
    }

    @Override
    public String toString() {
        if (isArray) {
            var sj = new StringJoiner("][", "[", "]");
            for (var e: indices) {
                sj.add(e.toString());
            }
            return id + sj.toString();
        } else {
            return id;
        }
    }
}
