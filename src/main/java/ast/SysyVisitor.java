// Generated from /root/repo/mercuri/src/main/java/ast/Sysy.g4 by ANTLR 4.8
package ast;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SysyParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface SysyVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link SysyParser#compUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompUnit(SysyParser.CompUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#transUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTransUnit(SysyParser.TransUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecl(SysyParser.DeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstDecl(SysyParser.ConstDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#basicType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBasicType(SysyParser.BasicTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstDef(SysyParser.ConstDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constInitVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstInitVal(SysyParser.ConstInitValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#varDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDecl(SysyParser.VarDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#varDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDef(SysyParser.VarDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#initVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInitVal(SysyParser.InitValContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncDef(SysyParser.FuncDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncType(SysyParser.FuncTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcParams}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncParams(SysyParser.FuncParamsContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncParam(SysyParser.FuncParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(SysyParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#blockItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockItem(SysyParser.BlockItemContext ctx);
	/**
	 * Visit a parse tree produced by the {@code assignStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignStmt(SysyParser.AssignStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code exprStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprStmt(SysyParser.ExprStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code blockStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockStmt(SysyParser.BlockStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ifStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStmt(SysyParser.IfStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ifElseStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfElseStmt(SysyParser.IfElseStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code whileStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStmt(SysyParser.WhileStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code breakStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakStmt(SysyParser.BreakStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code continueStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinueStmt(SysyParser.ContinueStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code returnStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnStmt(SysyParser.ReturnStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpr(SysyParser.ExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#cond}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCond(SysyParser.CondContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#lVal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLVal(SysyParser.LValContext ctx);
	/**
	 * Visit a parse tree produced by the {@code primaryExprQuote}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExprQuote(SysyParser.PrimaryExprQuoteContext ctx);
	/**
	 * Visit a parse tree produced by the {@code primaryExprLVal}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExprLVal(SysyParser.PrimaryExprLValContext ctx);
	/**
	 * Visit a parse tree produced by the {@code primaryExprNumber}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExprNumber(SysyParser.PrimaryExprNumberContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#number}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNumber(SysyParser.NumberContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryPrimaryExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryPrimaryExpr(SysyParser.UnaryPrimaryExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryFunc}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryFunc(SysyParser.UnaryFuncContext ctx);
	/**
	 * Visit a parse tree produced by the {@code unaryOpExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryOpExpr(SysyParser.UnaryOpExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#unaryOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryOp(SysyParser.UnaryOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#funcArgs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncArgs(SysyParser.FuncArgsContext ctx);
	/**
	 * Visit a parse tree produced by the {@code funcArgExpr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncArgExpr(SysyParser.FuncArgExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code funcArgStr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncArgStr(SysyParser.FuncArgStrContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#mulExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMulExpr(SysyParser.MulExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#addExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddExpr(SysyParser.AddExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#relExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelExpr(SysyParser.RelExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#eqExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqExpr(SysyParser.EqExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#logicAndExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicAndExpr(SysyParser.LogicAndExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#logicOrExp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicOrExp(SysyParser.LogicOrExpContext ctx);
	/**
	 * Visit a parse tree produced by {@link SysyParser#constExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstExpr(SysyParser.ConstExprContext ctx);
}