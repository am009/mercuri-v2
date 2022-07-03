package dst;

import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;

import ast.SysyBaseVisitor;
import ast.SysyParser.ConstDeclContext;
import ast.SysyParser.VarDeclContext;
import ast.SysyParser.VarDefContext;
import ds.UnreachableException;
import dst.ds.BasicType;
import dst.ds.Decl;
import dst.ds.DstGeneratorContext;

public class AstDeclVisitor extends SysyBaseVisitor<List<Decl>> {
    /**
     * decl
     * : constDecl
     * | varDecl
     * ;
     */
    public List<Decl> visitDecl(ast.SysyParser.DeclContext ast, DstGeneratorContext ctx) throws UnreachableException {
        if (ast.constDecl() != null) {
            return this.visitConstDecl(ast.constDecl(), ctx);
        }
        if (ast.varDecl() != null) {
            return this.visitVarDecl(ast.varDecl(), ctx);
        }
        ctx.panic("Unreachable");
        return null;
    }

    /**
     * varDecl
     * : basicType varDef (',' varDef)* ';'
     * ;
     */
    private List<Decl> visitVarDecl(VarDeclContext ast, DstGeneratorContext ctx) {
        var basicType = BasicType.fromString(ast.basicType().getText());
        var ret = ast.varDef().stream().map(d -> this.visitVarDef(d, ctx, basicType)).collect(Collectors.toList());
        return ret;
    }

    private Decl visitVarDef(VarDefContext ast, DstGeneratorContext ctx, BasicType basicType) {
        var id = ast.ID().getText();
        var indexExprs = ast.constExpr().stream().map(e -> ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(e, ctx)).collect(Collectors.toList());
    }

    /**
     * constDecl
     * : 'const' basicType constDef (',' constDef)* ';'
     * ;
     */
    private List<Decl> visitConstDecl(ConstDeclContext ast, DstGeneratorContext ctx) {
        return null;
    }

}
