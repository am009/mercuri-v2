// Generated from /root/repo/mercuri/src/main/java/ast/Sysy.g4 by ANTLR 4.8
package ast;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link SysyParser}.
 */
public interface SysyListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link SysyParser#compUnit}.
	 * @param ctx the parse tree
	 */
	void enterCompUnit(SysyParser.CompUnitContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#compUnit}.
	 * @param ctx the parse tree
	 */
	void exitCompUnit(SysyParser.CompUnitContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#transUnit}.
	 * @param ctx the parse tree
	 */
	void enterTransUnit(SysyParser.TransUnitContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#transUnit}.
	 * @param ctx the parse tree
	 */
	void exitTransUnit(SysyParser.TransUnitContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#decl}.
	 * @param ctx the parse tree
	 */
	void enterDecl(SysyParser.DeclContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#decl}.
	 * @param ctx the parse tree
	 */
	void exitDecl(SysyParser.DeclContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#constDecl}.
	 * @param ctx the parse tree
	 */
	void enterConstDecl(SysyParser.ConstDeclContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#constDecl}.
	 * @param ctx the parse tree
	 */
	void exitConstDecl(SysyParser.ConstDeclContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#basicType}.
	 * @param ctx the parse tree
	 */
	void enterBasicType(SysyParser.BasicTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#basicType}.
	 * @param ctx the parse tree
	 */
	void exitBasicType(SysyParser.BasicTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#constDef}.
	 * @param ctx the parse tree
	 */
	void enterConstDef(SysyParser.ConstDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#constDef}.
	 * @param ctx the parse tree
	 */
	void exitConstDef(SysyParser.ConstDefContext ctx);
	/**
	 * Enter a parse tree produced by the {@code constExprInitVal}
	 * labeled alternative in {@link SysyParser#constInitVal}.
	 * @param ctx the parse tree
	 */
	void enterConstExprInitVal(SysyParser.ConstExprInitValContext ctx);
	/**
	 * Exit a parse tree produced by the {@code constExprInitVal}
	 * labeled alternative in {@link SysyParser#constInitVal}.
	 * @param ctx the parse tree
	 */
	void exitConstExprInitVal(SysyParser.ConstExprInitValContext ctx);
	/**
	 * Enter a parse tree produced by the {@code constCompInitVal}
	 * labeled alternative in {@link SysyParser#constInitVal}.
	 * @param ctx the parse tree
	 */
	void enterConstCompInitVal(SysyParser.ConstCompInitValContext ctx);
	/**
	 * Exit a parse tree produced by the {@code constCompInitVal}
	 * labeled alternative in {@link SysyParser#constInitVal}.
	 * @param ctx the parse tree
	 */
	void exitConstCompInitVal(SysyParser.ConstCompInitValContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#varDecl}.
	 * @param ctx the parse tree
	 */
	void enterVarDecl(SysyParser.VarDeclContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#varDecl}.
	 * @param ctx the parse tree
	 */
	void exitVarDecl(SysyParser.VarDeclContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#varDef}.
	 * @param ctx the parse tree
	 */
	void enterVarDef(SysyParser.VarDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#varDef}.
	 * @param ctx the parse tree
	 */
	void exitVarDef(SysyParser.VarDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#initVal}.
	 * @param ctx the parse tree
	 */
	void enterInitVal(SysyParser.InitValContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#initVal}.
	 * @param ctx the parse tree
	 */
	void exitInitVal(SysyParser.InitValContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#funcDef}.
	 * @param ctx the parse tree
	 */
	void enterFuncDef(SysyParser.FuncDefContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#funcDef}.
	 * @param ctx the parse tree
	 */
	void exitFuncDef(SysyParser.FuncDefContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#funcType}.
	 * @param ctx the parse tree
	 */
	void enterFuncType(SysyParser.FuncTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#funcType}.
	 * @param ctx the parse tree
	 */
	void exitFuncType(SysyParser.FuncTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#funcParams}.
	 * @param ctx the parse tree
	 */
	void enterFuncParams(SysyParser.FuncParamsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#funcParams}.
	 * @param ctx the parse tree
	 */
	void exitFuncParams(SysyParser.FuncParamsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#funcParam}.
	 * @param ctx the parse tree
	 */
	void enterFuncParam(SysyParser.FuncParamContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#funcParam}.
	 * @param ctx the parse tree
	 */
	void exitFuncParam(SysyParser.FuncParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(SysyParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(SysyParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#blockItem}.
	 * @param ctx the parse tree
	 */
	void enterBlockItem(SysyParser.BlockItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#blockItem}.
	 * @param ctx the parse tree
	 */
	void exitBlockItem(SysyParser.BlockItemContext ctx);
	/**
	 * Enter a parse tree produced by the {@code assignStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterAssignStmt(SysyParser.AssignStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code assignStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitAssignStmt(SysyParser.AssignStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code exprStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterExprStmt(SysyParser.ExprStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code exprStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitExprStmt(SysyParser.ExprStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code blockStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterBlockStmt(SysyParser.BlockStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code blockStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitBlockStmt(SysyParser.BlockStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ifStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterIfStmt(SysyParser.IfStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ifStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitIfStmt(SysyParser.IfStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ifElseStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterIfElseStmt(SysyParser.IfElseStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ifElseStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitIfElseStmt(SysyParser.IfElseStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code whileStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterWhileStmt(SysyParser.WhileStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code whileStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitWhileStmt(SysyParser.WhileStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code breakStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterBreakStmt(SysyParser.BreakStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code breakStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitBreakStmt(SysyParser.BreakStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code continueStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterContinueStmt(SysyParser.ContinueStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code continueStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitContinueStmt(SysyParser.ContinueStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code returnStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void enterReturnStmt(SysyParser.ReturnStmtContext ctx);
	/**
	 * Exit a parse tree produced by the {@code returnStmt}
	 * labeled alternative in {@link SysyParser#stmt}.
	 * @param ctx the parse tree
	 */
	void exitReturnStmt(SysyParser.ReturnStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterExpr(SysyParser.ExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitExpr(SysyParser.ExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#cond}.
	 * @param ctx the parse tree
	 */
	void enterCond(SysyParser.CondContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#cond}.
	 * @param ctx the parse tree
	 */
	void exitCond(SysyParser.CondContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#lVal}.
	 * @param ctx the parse tree
	 */
	void enterLVal(SysyParser.LValContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#lVal}.
	 * @param ctx the parse tree
	 */
	void exitLVal(SysyParser.LValContext ctx);
	/**
	 * Enter a parse tree produced by the {@code primaryExprQuote}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterPrimaryExprQuote(SysyParser.PrimaryExprQuoteContext ctx);
	/**
	 * Exit a parse tree produced by the {@code primaryExprQuote}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitPrimaryExprQuote(SysyParser.PrimaryExprQuoteContext ctx);
	/**
	 * Enter a parse tree produced by the {@code primaryExprLVal}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterPrimaryExprLVal(SysyParser.PrimaryExprLValContext ctx);
	/**
	 * Exit a parse tree produced by the {@code primaryExprLVal}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitPrimaryExprLVal(SysyParser.PrimaryExprLValContext ctx);
	/**
	 * Enter a parse tree produced by the {@code primaryExprNumber}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterPrimaryExprNumber(SysyParser.PrimaryExprNumberContext ctx);
	/**
	 * Exit a parse tree produced by the {@code primaryExprNumber}
	 * labeled alternative in {@link SysyParser#primaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitPrimaryExprNumber(SysyParser.PrimaryExprNumberContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#number}.
	 * @param ctx the parse tree
	 */
	void enterNumber(SysyParser.NumberContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#number}.
	 * @param ctx the parse tree
	 */
	void exitNumber(SysyParser.NumberContext ctx);
	/**
	 * Enter a parse tree produced by the {@code unaryPrimaryExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryPrimaryExpr(SysyParser.UnaryPrimaryExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code unaryPrimaryExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryPrimaryExpr(SysyParser.UnaryPrimaryExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code unaryFunc}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryFunc(SysyParser.UnaryFuncContext ctx);
	/**
	 * Exit a parse tree produced by the {@code unaryFunc}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryFunc(SysyParser.UnaryFuncContext ctx);
	/**
	 * Enter a parse tree produced by the {@code unaryOpExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryOpExpr(SysyParser.UnaryOpExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code unaryOpExpr}
	 * labeled alternative in {@link SysyParser#unaryExpr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryOpExpr(SysyParser.UnaryOpExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#unaryOp}.
	 * @param ctx the parse tree
	 */
	void enterUnaryOp(SysyParser.UnaryOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#unaryOp}.
	 * @param ctx the parse tree
	 */
	void exitUnaryOp(SysyParser.UnaryOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#funcArgs}.
	 * @param ctx the parse tree
	 */
	void enterFuncArgs(SysyParser.FuncArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#funcArgs}.
	 * @param ctx the parse tree
	 */
	void exitFuncArgs(SysyParser.FuncArgsContext ctx);
	/**
	 * Enter a parse tree produced by the {@code funcArgExpr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 */
	void enterFuncArgExpr(SysyParser.FuncArgExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code funcArgExpr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 */
	void exitFuncArgExpr(SysyParser.FuncArgExprContext ctx);
	/**
	 * Enter a parse tree produced by the {@code funcArgStr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 */
	void enterFuncArgStr(SysyParser.FuncArgStrContext ctx);
	/**
	 * Exit a parse tree produced by the {@code funcArgStr}
	 * labeled alternative in {@link SysyParser#funcArg}.
	 * @param ctx the parse tree
	 */
	void exitFuncArgStr(SysyParser.FuncArgStrContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#mulExpr}.
	 * @param ctx the parse tree
	 */
	void enterMulExpr(SysyParser.MulExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#mulExpr}.
	 * @param ctx the parse tree
	 */
	void exitMulExpr(SysyParser.MulExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#addExpr}.
	 * @param ctx the parse tree
	 */
	void enterAddExpr(SysyParser.AddExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#addExpr}.
	 * @param ctx the parse tree
	 */
	void exitAddExpr(SysyParser.AddExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#relExpr}.
	 * @param ctx the parse tree
	 */
	void enterRelExpr(SysyParser.RelExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#relExpr}.
	 * @param ctx the parse tree
	 */
	void exitRelExpr(SysyParser.RelExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#eqExpr}.
	 * @param ctx the parse tree
	 */
	void enterEqExpr(SysyParser.EqExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#eqExpr}.
	 * @param ctx the parse tree
	 */
	void exitEqExpr(SysyParser.EqExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#logicAndExpr}.
	 * @param ctx the parse tree
	 */
	void enterLogicAndExpr(SysyParser.LogicAndExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#logicAndExpr}.
	 * @param ctx the parse tree
	 */
	void exitLogicAndExpr(SysyParser.LogicAndExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#logicOrExp}.
	 * @param ctx the parse tree
	 */
	void enterLogicOrExp(SysyParser.LogicOrExpContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#logicOrExp}.
	 * @param ctx the parse tree
	 */
	void exitLogicOrExp(SysyParser.LogicOrExpContext ctx);
	/**
	 * Enter a parse tree produced by {@link SysyParser#constExpr}.
	 * @param ctx the parse tree
	 */
	void enterConstExpr(SysyParser.ConstExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysyParser#constExpr}.
	 * @param ctx the parse tree
	 */
	void exitConstExpr(SysyParser.ConstExprContext ctx);
}