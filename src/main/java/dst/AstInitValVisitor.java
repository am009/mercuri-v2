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
            InitValType initType, List<Integer> dims) {
        if (ast.expr() != null) {
            return InitValue.ofExpr(initType, ctx.getVisitors().of(AstExprVisitor.class).visitExpr(ast.expr(), ctx));
        } else if (ast.initVal() != null) {
            var fromIndex = 1;
            var toIndex = ast.initVal().size() - 1;
            final var finalInitType = fromIndex == toIndex ? InitValType.basicInitTypeOf(initType) : initType;
            var exprs = ast.initVal().stream()
                    .map(i -> this.visitInitVal(i, ctx, basicType, finalInitType, dims.subList(1, toIndex)))
                    .collect(Collectors.toList());
            return InitValue.ofArray(initType, dims, exprs);
        }

        ctx.panic("Unreachable");
        return null;
    }

    public InitValue visitConstInitVal(ConstInitValContext ast, DstGeneratorContext ctx, BasicType basicType,
            InitValType initType, List<Integer> dims) {
        if (ast.constExpr() != null) {
            return InitValue.ofExpr(initType, ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(ast.constExpr(), ctx));
        } else if (ast.constInitVal() != null) {
            var fromIndex = 1;
            var toIndex = ast.constInitVal().size() - 1;
            final var finalInitType = fromIndex == toIndex ? InitValType.basicInitTypeOf(initType) : initType;
            var exprs = ast.constInitVal().stream()
                    .map(i -> this.visitConstInitVal(i, ctx, basicType, finalInitType, dims.subList(1, toIndex)))
                    .collect(Collectors.toList());
            return InitValue.ofArray(initType, dims, exprs);
        }

        ctx.panic("Unreachable");
        return null;
    }

}
