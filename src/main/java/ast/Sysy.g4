/**
 * EBNF of SysY 2022 language
 */

grammar Sysy;

/*
 Token
 */

KW_INT       : 'int'      ;
KW_VOID      : 'void'     ;
KW_CONST     : 'const'    ;
KW_RETURN    : 'return'   ;
KW_IF        : 'if'       ;
KW_ELSE      : 'else'     ;
KW_FOR       : 'for'      ;
KW_WHILE     : 'while'    ;
KW_DO        : 'do'       ;
KW_BREAK     : 'break'    ;
KW_CONTINUE  : 'continue' ;
OP_AND       : '&&'       ;
OP_OR        : '||'       ;
OP_EQ        : '=='       ;
OP_NE        : '!='       ;
OP_LT        : '<'        ;
OP_LE        : '<='       ;
OP_GT        : '>'        ;
OP_GE        : '>='       ;

INT_CONSTANT
    : [1-9] [0-9]*          // decimal constant
    | '0' [0-7]*            // octal constant
    | '0' [xX] [0-9a-fA-F]* // hexadecimal constant
    | '0' [bB] [0-1]+       // binary constant
    ;

FLOAT_CONSTANT
    : DEC_CONSTANT_FLOAT
    | HEX_CONSTANT_FLOAT    
    ;

fragment DEC_CONSTANT_FLOAT
    : FRAC_CONSTANT EXP_PART?
    | [1-9] [0-9]* EXP_PART?
    ;

fragment HEX_CONSTANT_FLOAT
    : '0' [xX] (HEX_FRAC_CONST | [0-9a-fA-F]+) BIN_EXP_PART 
    ;

fragment HEX_FRAC_CONST
    : [0-9a-fA-F]? '.' [0-9a-fA-F]
    | [0-9a-fA-F] '.'
    ;

fragment BIN_EXP_PART
    : [pP] [-+]? [0-9]+
    ;

fragment EXP_PART
    : [eE] [-+]? [0-9]+
    ;


fragment FRAC_CONSTANT
    : [0-9]* '.' [0-9]+
    | [0-9]+ '.'
    ;

ID
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

STRING_LITERAL
    : '"' CHAR_LITERAL* '"'
    ;

fragment CHAR_LITERAL
    :   ~["\\\r\n]
    |   '\\' ['"?abfnrtv\\]
    |   '\\\n'
    |   '\\\r\n'
    ;


WS
    : [ \t\n\f\r] -> skip
    ;

INL_COMMENT
    : '//' .*? '\r'? '\n' -> skip
    ;

BLK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

/*
 * Grammar
 */

compUnit
    : transUnit EOF
    ;

transUnit
    : (funcDef | decl)*
    ;

decl
    : constDecl 
    | varDecl
    ;

constDecl
    : 'const' basicType constDef (',' constDef)* ';'
    ;

basicType
    : 'int' 
    | 'float'
    ;

constDef
    : ID ('[' constExpr ']')* '=' constInitVal
    ;

constInitVal
    : constExpr                                     #constExprInitVal
    | '{' (constInitVal (',' constInitVal)*)? '}'   #constCompInitVal
    ;

varDecl
    : basicType varDef (',' varDef)* ';'
    ;

varDef
    : ID ('[' constExpr ']')*
    | ID ('[' constExpr ']')* '=' initVal
    ;

initVal
    : expr
    | '{' (initVal (',' initVal)*)? '}'
    ;

funcDef
    : funcType ID '(' (funcParams)? ')' block
    ;

funcType
    : 'void' 
    | 'int' 
    | 'float'
    ;

funcParams
    : funcParam (',' funcParam)*
    ;

funcParam
    : basicType ID ('[' ']' ('[' constExpr ']')*)?
    ;

block
    : '{' (blockItem)* '}'
    ;

blockItem
    : decl 
    | stmt
    ;

stmt
    : lVal '=' expr ';'                     #assignStmt
    | (expr)? ';'                           #exprStmt
    | block                                 #blockStmt
    | KW_IF '(' cond ')' stmt               #ifStmt
    | KW_IF '(' cond ')' stmt KW_ELSE stmt  #ifElseStmt
    | KW_WHILE '(' cond ')' stmt            #whileStmt
    | KW_BREAK ';'                          #breakStmt
    | KW_CONTINUE ';'                       #continueStmt
    | KW_RETURN (expr)? ';'                 #returnStmt
    ;

expr
    : addExpr
    ;

cond
    : logicOrExp
    ;

lVal
    : ID ('[' expr ']')*
    ;

primaryExpr
    : '(' expr ')'  #primaryExprQuote
    | lVal          #primaryExprLVal
    | number        #primaryExprNumber
    ;

number
    : INT_CONSTANT | FLOAT_CONSTANT
    ;

unaryExpr
    : primaryExpr                          #unaryPrimaryExpr
    | ID '(' (funcArgs)? ')'               #unaryFunc
    | unaryOp unaryExpr                    #unaryOpExpr
    ;

unaryOp
    : '+' 
    | '-' 
    | '!'
    ;

funcArgs
    : funcArg (',' funcArg)*
    ;

funcArg
    : expr           # funcArgExpr 
    | STRING_LITERAL # funcArgStr
    ;

mulExpr
    : unaryExpr
    | mulExpr ('*' | '/' | '%') unaryExpr
    ;

addExpr
    : mulExpr
    | addExpr ('+' | '-') mulExpr
    ;

relExpr
    : addExpr
    | relExpr ('<' | '>' | '<=' | '>=' ) addExpr
    ;

eqExpr
    : relExpr
    | eqExpr ('==' | '!=') relExpr
    ;

logicAndExpr
    : eqExpr
    | logicAndExpr '&&' eqExpr
    ;

logicOrExp
    : logicAndExpr
    | logicOrExp '||' logicAndExpr
    ;

constExpr
    : addExpr
    ;