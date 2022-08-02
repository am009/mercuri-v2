
### 2022年7月8日 AST遍历时

src\main\java\dst\AstDeclVisitor.java 在访问visitConstDef时使用的依然是DeclType.VAR，改为了DeclType.CONST。然后出现了下面的bug。

exp 展开成变量时，走的是`PrimaryExp → '(' Exp ')' | LVal | Number`，因此即使不是赋值语句也会展开为LVal。如果在语义检查中认为LVal不能引用const变量则会出现问题。

### 坑点

DONE 解决`-2147483648`当作unaryop和number时，数字超过int大小的相关的解析问题
TODO 增加测试用例：
- 调用约定部分
    - 浮点传参超过16后会不会用到r0-r4？
    - 变参函数的double和32位值的对齐问题
- 测试代码太大超过32MB的B指令跳转范围？？