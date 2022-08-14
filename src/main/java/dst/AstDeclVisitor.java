package dst;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ast.SysyBaseVisitor;
import ast.SysyParser.ConstDeclContext;
import ast.SysyParser.ConstDefContext;
import ast.SysyParser.FuncDefContext;
import ast.SysyParser.FuncParamContext;
import ast.SysyParser.FuncParamsContext;
import ast.SysyParser.VarDeclContext;
import ast.SysyParser.VarDefContext;
import dst.ds.BasicType;
import dst.ds.Block;
import dst.ds.Decl;
import dst.ds.DeclType;
import dst.ds.DstGeneratorContext;
import dst.ds.Expr;
import dst.ds.Func;
import dst.ds.FuncType;
import dst.ds.InitValType;
import dst.ds.InitValue;

public class AstDeclVisitor extends SysyBaseVisitor<List<Decl>> {
    /**
     * decl
     * : constDecl
     * | varDecl
     * ;
     */
    public List<Decl> visitDecl(ast.SysyParser.DeclContext ast, DstGeneratorContext ctx, Boolean isGlobal) {
        if (ast.constDecl() != null) {
            return this.visitConstDecl(ast.constDecl(), ctx, isGlobal);
        }
        if (ast.varDecl() != null) {
            return this.visitVarDecl(ast.varDecl(), ctx, isGlobal);
        }
        ctx.panic("Unreachable");
        return null;
    }

    /**
     * varDecl
     * : basicType varDef (',' varDef)* ';'
     * ;
     */
    private List<Decl> visitVarDecl(VarDeclContext ast, DstGeneratorContext ctx, Boolean isGlobal) {
        var basicType = BasicType.fromString(ast.basicType().getText());
        var ret = ast.varDef().stream().map(d -> this.visitVarDef(d, ctx, basicType, isGlobal))
                .collect(Collectors.toList());
        return ret;
    }

    /**
     * funcParam
     * : basicType ID ('[' ']' ('[' constExpr ']')*)?
     * ;
     */
    private Decl visitFuncParam(FuncParamContext ast, DstGeneratorContext ctx, Boolean isGlobalFunc) {
        var declType = DeclType.VAR;
        var isParam = true;
        var isGlobal = false;
        var id = ast.ID().getText();
        var basicType = BasicType.fromString(ast.basicType().getText());
        List<Expr> indexExprs = ast.constExpr().stream()
                .map(e -> ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(e, ctx))
                .collect(Collectors.toList());
        InitValue initVal = null;
        // List<Integer> dims = indexExprs.stream().map(i -> i.eval().intValue).collect(Collectors.toList());
        var ret = new Decl(declType, isParam, isGlobal, basicType, id, indexExprs, initVal);
        if (ast.getChildCount() > 2) { // has '[' and ']'
            ret.isDimensionOmitted = true;
        }
        return ret;
    }

    /**
     * varDef
     * : ID ('[' constExpr ']')*
     * | ID ('[' constExpr ']')* '=' initVal
     * ;
     */
    private Decl visitVarDef(VarDefContext ast, DstGeneratorContext ctx, BasicType basicType, Boolean isGlobal) {

        DeclType declType = DeclType.VAR;
        Boolean isParam = false;
        String id = ast.ID().getText();
        var dimsRaw = ast.constExpr().stream()
                .map(e -> ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(e, ctx))
                .collect(Collectors.toList());

        // 可能引用到const变量，留给SematicAnalyzer处理
        // // check all dims are constant and positive
        // for (var dim : dimsRaw) {            
        //     if (!dim.isConst) {
        //         ctx.panic("Array dimension must be constant");
        //     }
        //     if (dim.eval() == null || dim.value.intValue == null || dim.value.intValue < 0) {
        //         ctx.panic("Array dimension must exist and bec positive");
        //     }
        // }
        // List<Integer> dims = dimsRaw.stream().map(i -> i.eval().intValue).collect(Collectors.toList());

        var initVal = ctx.getVisitors().of(AstInitValVisitor.class).visitInitVal(ast.initVal(), ctx,
                basicType, InitValType.VAR_EXPR, true);

        return new Decl(declType, isParam, isGlobal, basicType, id, dimsRaw, initVal);
    }

    /**
     * constDecl
     * : 'const' basicType constDef (',' constDef)* ';'
     * ;
     */
    private List<Decl> visitConstDecl(ConstDeclContext ast, DstGeneratorContext ctx, Boolean isGlobal) {
        var basicType = BasicType.fromString(ast.basicType().getText());
        var ret = ast.constDef().stream().map(d -> this.visitConstDef(d, ctx, basicType, isGlobal))
                .collect(Collectors.toList());
        return ret;
    }

    /**
     * constDef
     * : ID ('[' constExpr ']')* '=' constInitVal
     * ;
     */
    private Decl visitConstDef(ConstDefContext ast, DstGeneratorContext ctx, BasicType basicType, Boolean isGlobal) {

        DeclType declType = DeclType.CONST;
        Boolean isParam = false;
        String id = ast.ID().getText();
        var dimsRaw = ast.constExpr().stream()
                .map(e -> ctx.getVisitors().of(AstExprVisitor.class).visitConstExpr(e, ctx))
                .collect(Collectors.toList());

        // check all dims are constant and positive
        // for (var dim : dimsRaw) {
        //     if (!dim.isConst) {
        //         ctx.panic("Array dimension must be constant");
        //     }
        //     if (dim.eval() == null || dim.value.intValue == null || dim.value.intValue < 0) {
        //         ctx.panic("Array dimension must exist and bec positive");
        //     }
        // }
        // List<Integer> dims = dimsRaw.stream().map(i -> i.eval().intValue).collect(Collectors.toList());

        var initVal = ctx.getVisitors().of(AstInitValVisitor.class).visitConstInitVal(ast.constInitVal(), ctx,
                basicType, InitValType.CONST_EXPR);

        return new Decl(declType, isParam, isGlobal, basicType, id, dimsRaw, initVal);
    }

    public Func visitFuncDef(FuncDefContext ast, DstGeneratorContext ctx, boolean isGlobal) {
        FuncType retType = FuncType.fromString(ast.funcType().getText());
        String id = ast.ID().getText();
        Block body = ctx.getVisitors().of(AstBlockVisitor.class).visitBlock(ast.block(), ctx);
        List<Decl> params = this.visitFuncParams(ast.funcParams(), ctx, isGlobal);
        return new Func(retType, id, params, body);
    }

    private List<Decl> visitFuncParams(FuncParamsContext funcParams, DstGeneratorContext ctx, Boolean isGlobalFunc) {
        if (funcParams == null) {
            return new ArrayList<>();
        }
        var ret = funcParams.funcParam().stream()
                .map(p -> ctx.getVisitors().of(AstDeclVisitor.class).visitFuncParam(p, ctx, isGlobalFunc))
                .collect(Collectors.toList());
        return ret;
    }

}
