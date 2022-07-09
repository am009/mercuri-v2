package dst;

import java.util.List;
import java.util.stream.Collectors;

import ast.SysyBaseVisitor;
import ast.SysyParser.ConstInitValContext;
import ast.SysyParser.InitValContext;
import dst.ds.BasicType;
import dst.ds.DstGeneratorContext;
import dst.ds.InitValType;
import dst.ds.InitValue;

public class AstInitValVisitor extends SysyBaseVisitor<InitValue> {

    /**
     * initVal
     * : expr
     * | '{' (initVal (',' initVal)*)? '}'
     * ;
     */
    public InitValue visitInitVal(InitValContext ast, DstGeneratorContext ctx, BasicType basicType,
            InitValType initType) {
        if (ast == null) { // 无initVal
            return null;
        }
        if (ast.expr() != null) {
            return InitValue.ofExpr(initType, ctx.getVisitors().of(AstExprVisitor.class).visitExpr(ast.expr(), ctx));
        } else if (ast.initVal() != null) {
            // int c[4][2] = {1, 2, 3, 4, 5, 6, 7, 8}; 多种复杂的数组初始化情况下不一定有和dims的对应关系。语义分析的时候再来处理。
            // var fromIndex = 1;
            // var toIndex = ast.initVal().size() - 1;
            // final var finalInitType = fromIndex == toIndex ? InitValType.basicInitTypeOf(initType) : initType;
            var exprs = ast.initVal().stream()
                    .map(i -> this.visitInitVal(i, ctx, basicType, initType))
                    .collect(Collectors.toList());
            return InitValue.ofArray(initType, exprs);
        }

        ctx.panic("Unreachable");
        return null;
    }

    public InitValue visitConstInitVal(ConstInitValContext ast, DstGeneratorContext ctx, BasicType basicType,
            InitValType initType) {
        // Const 声明在语法上是必带'='和之后的initVal的，所以ast不会为null
        if (ast.constExpr() != null) {
            return InitValue.ofExpr(initType, ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(ast.constExpr(), ctx));
        } else if (ast.constInitVal() != null) {
            // var fromIndex = 1;
            // var toIndex = dims.size();
            // final var finalInitType = fromIndex == toIndex ? InitValType.basicInitTypeOf(initType) : initType;
            // dims.subList(1, toIndex) --> pop front
            var exprs = ast.constInitVal().stream()
                    .map(i -> this.visitConstInitVal(i, ctx, basicType, initType))
                    .collect(Collectors.toList());
            return InitValue.ofArray(initType, exprs);
        }

        ctx.panic("Unreachable");
        return null;
    }

}
