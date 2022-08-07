### 2022年8月8日 65_color.sy

症状：IR过不了，返回值显示segment fault，gdb调试显示在main函数的最内层循环处。编译成ASM却正常。而且编译IR时给clang加上-O2也变正常了。最诡异的是，我给源码中main函数每个循环中增加了putch用来debug后，居然fault的位置在putch里面，而不是我自己的代码。

怀疑是没有理解清楚llvm ir中alloca的语义，可能是每个循环内每个alloca都在拓展了栈大小，然后栈爆了。看了下LLVM生成的IR，确实所有局部变量的alloca都放到函数开头了。。。所幸生成的汇编没什么问题。这个以后再看看怎么处理吧。TODO。

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