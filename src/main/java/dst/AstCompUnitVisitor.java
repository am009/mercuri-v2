package dst;

import java.util.List;
import java.util.stream.Collectors;

import ast.SysyBaseVisitor;
import ast.SysyParser;
import ds.Global;
import dst.ds.CompUnit;
import dst.ds.DstGeneratorContext;

public class AstCompUnitVisitor extends SysyBaseVisitor<CompUnit> {
    public CompUnit visitCompUnit(SysyParser.CompUnitContext ast, DstGeneratorContext ctx, String filename) {
        var tu = ast.transUnit();
        var declVisitor = ctx.getVisitors().of(AstDeclVisitor.class);
        var decls = tu.decl().stream().map(d -> declVisitor.visitDecl(d, ctx)).flatMap(List::stream)
                .collect(Collectors.toList());

        var ret = new CompUnit(filename, decls, funcs);
        return ret;
    }
}
