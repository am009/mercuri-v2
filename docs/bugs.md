
### 2022年7月8日 AST遍历时

src\main\java\dst\AstDeclVisitor.java 在访问visitConstDef时使用的依然是DeclType.VAR，改为了DeclType.CONST。然后出现了下面的bug。

exp 展开成变量时，走的是`PrimaryExp → '(' Exp ')' | LVal | Numbe`，因此即使不是赋值语句也会展开为LVal。如果在语义检查中认为LVal不能引用const变量则会出现问题。

### 坑点

TODO 解决`-2147483648`当作unaryop和number时，数字超过int大小的相关的解析问题
