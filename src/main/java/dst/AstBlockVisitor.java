package dst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import ast.SysyBaseVisitor;
import ast.SysyParser.AssignStmtContext;
import ast.SysyParser.BlockContext;
import ast.SysyParser.BlockItemContext;
import ast.SysyParser.BlockStmtContext;
import ast.SysyParser.ExprStmtContext;
import ast.SysyParser.StmtContext;
import dst.ds.AssignStatement;
import dst.ds.Block;
import dst.ds.BlockStatement;
import dst.ds.BreakStatement;
import dst.ds.ContinueStatement;
import dst.ds.DstGeneratorContext;
import dst.ds.ExprStatement;
import dst.ds.IfElseStatement;
import dst.ds.LoopStatement;
import dst.ds.ReturnStatement;

public class AstBlockVisitor extends SysyBaseVisitor<BlockStatement> {

    public Block visitBlock(BlockContext ast, DstGeneratorContext ctx) {
        return new Block(ast.blockItem().stream().map(s -> this.visitBlockItem(s, ctx)).flatMap(List::stream)
                .collect(Collectors.toList()));
    }

    /**
     * blockItem
     * : decl
     * | stmt
     * ;
     */
    private List<BlockStatement> visitBlockItem(BlockItemContext s, DstGeneratorContext ctx) {
        if (s.decl() != null) {
            var list = ctx.getVisitors().of(AstDeclVisitor.class).visitDecl(s.decl(), ctx, false);
            // cast list of decl to list of block statement
            return list.stream().map(d -> (BlockStatement) d).collect(Collectors.toList());
        } else {
            var stmt = this.visitStatement(s.stmt(), ctx);
            return Collections.singletonList(stmt);
        }
    }

    /**
     * stmt
     * : lVal '=' expr ';' #assignStmt
     * | (expr)? ';' #exprStmt
     * | block #blockStmt
     * | KW_IF '(' cond ')' stmt #ifStmt
     * | KW_IF '(' cond ')' stmt KW_ELSE stmt #ifElseStmt
     * | KW_WHILE '(' cond ')' stmt #whileStmt
     * | KW_BREAK ';' #breakStmt
     * | KW_CONTINUE ';' #continueStmt
     * | KW_RETURN (expr)? ';' #returnStmt
     * ;
     * 
     */
    private BlockStatement visitStatement(StmtContext ast, DstGeneratorContext ctx) {
        var exprVisitor = ctx.getVisitors().of(AstExprVisitor.class);
        if (ast instanceof AssignStmtContext) {
            var assign = (AssignStmtContext) ast;
            var lVal = exprVisitor.visitLVal(assign.lVal(), ctx);
            var expr = exprVisitor.visitExpr(assign.expr(), ctx);
            return new AssignStatement(lVal, expr);
        }
        if (ast instanceof ExprStmtContext) {
            var expr = exprVisitor.visitExpr(((ExprStmtContext) ast).expr(), ctx);
            return new ExprStatement(expr);
        }
        if (ast instanceof BlockStmtContext) {
            var block = (BlockStmtContext) ast;
            return new Block(
                    block.block().blockItem().stream().map(s -> this.visitBlockItem(s, ctx)).flatMap(List::stream)
                            .collect(Collectors.toList()));
        }
        if (ast instanceof ast.SysyParser.IfStmtContext) {
            var ifStmt = (ast.SysyParser.IfStmtContext) ast;
            var cond = exprVisitor.visitCond(ifStmt.cond(), ctx);
            var thenStmt = this.visitStatement(ifStmt.stmt(), ctx);
            Block thenStmtBlock;
            if (!(thenStmt instanceof Block)) {
                thenStmtBlock = new Block(Collections.singletonList(thenStmt));
            } else {
                thenStmtBlock = (Block) thenStmt;
            }
            return new IfElseStatement(cond, thenStmtBlock, null);
        }
        if (ast instanceof ast.SysyParser.IfElseStmtContext) {
            var ifElseStmt = (ast.SysyParser.IfElseStmtContext) ast;
            var cond = exprVisitor.visitCond(ifElseStmt.cond(), ctx);
            var thenStmt = this.visitStatement(ifElseStmt.stmt(0), ctx);
            var elseStmt = this.visitStatement(ifElseStmt.stmt(1), ctx);
            Block thenStmtBlock;
            Block elseStmtBlock;
            if (!(thenStmt instanceof Block)) {
                thenStmtBlock = new Block(Collections.singletonList(thenStmt));
            } else {
                thenStmtBlock = (Block) thenStmt;
            }
            if (!(elseStmt instanceof Block)) {
                elseStmtBlock = new Block(Collections.singletonList(elseStmt));
            } else {
                elseStmtBlock = (Block) elseStmt;
            }
            return new IfElseStatement(cond, thenStmtBlock, elseStmtBlock);
        }
        if (ast instanceof ast.SysyParser.WhileStmtContext) {
            var whileStmt = (ast.SysyParser.WhileStmtContext) ast;
            var cond = exprVisitor.visitCond(whileStmt.cond(), ctx);
            var body = this.visitStatement(whileStmt.stmt(), ctx);
            Block bodyBlock;
            if (!(body instanceof Block)) {
                bodyBlock = new Block(Collections.singletonList(body));
            } else {
                bodyBlock = (Block) body;
            }
            return new LoopStatement(cond, bodyBlock);
        }
        if (ast instanceof ast.SysyParser.BreakStmtContext) {
            return new BreakStatement();
        }

        if (ast instanceof ast.SysyParser.ContinueStmtContext) {
            return new ContinueStatement();
        }

        if (ast instanceof ast.SysyParser.ReturnStmtContext) {
            var returnStmt = (ast.SysyParser.ReturnStmtContext) ast;
            var expr = exprVisitor.visitExpr(returnStmt.expr(), ctx);
            return new ReturnStatement(expr);
        }

        ctx.panic("Unreachable");
        return null;

    }

}
