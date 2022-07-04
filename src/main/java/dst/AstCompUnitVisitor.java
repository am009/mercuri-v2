package dst;

import java.util.List;
import java.util.stream.Collectors;

import ast.SysyBaseVisitor;
import dst.ds.CompUnit;
import dst.ds.Decl;
import dst.ds.DstGeneratorContext;
import dst.ds.Func;

public class AstCompUnitVisitor extends SysyBaseVisitor<CompUnit> {
    public CompUnit visitCompUnit(DstGeneratorContext ctx) {
        var tu = ctx.getRootAst().transUnit();
        var declVisitor = ctx.getVisitors().of(AstDeclVisitor.class);
        var isGlobal = true;
        List<Decl> decls = List.of();
        if (tu.decl() != null) {
            decls = tu.decl().stream().map(d -> declVisitor.visitDecl(d, ctx, isGlobal)).flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        List<Func> funcs = List.of();
        if (tu.funcDef() != null) {
            funcs = tu.funcDef().stream().map(f -> declVisitor.visitFuncDef(f, ctx, isGlobal))
                    .collect(Collectors.toList());
        }
        var ret = new CompUnit(ctx.getFilename(), decls, funcs);
        return ret;
    }
}
