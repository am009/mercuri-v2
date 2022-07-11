
# IR 设计

通过toString方法直接转为LLVM IR的文本形式。

顶层模块暂时叫Module吧，不要和ir/ds里的Module弄混了。生成的类暂时叫FakeSSAGenerator吧，防止有人误解是直出ssa。

设计时如果不知道类该有什么成员时，参照LLVM IR (https://llvm.org/docs/LangRef.html )同时看
https://mapping-high-level-constructs-to-llvm-ir.readthedocs.io/en/latest/README.html

生成时，局部变量先全部使用alloca指令，之后再通过mem2reg转SSA，mem2reg模仿的是mimic（TODO 加链接）。

考虑使用Basic Block Argument 替代Phi指令 TODO 加资料

TODO
1. 把一些指令的LLVM IR打印改成用格式化字符串😂
1. 控制流相关生成，更多语句的生成
1. array的初始化生成（等写了函数调用生成之后）
1. 加入Phi节点，转SSA


### 基础

指令Instruction类为各种指令的基类，指令分为可以在基本块内的普通指令和分割基本块（只能放在基本块末尾）的TerminatorInstruction（包含Branch，Jump和Ret）。

### User & Use & Value

最初解读自hblzbd的代码：hblzbd_compiler\include\ssa.h，但是TrivialCompiler和Mimic都是这么做的。

Use代表关系，User使用Value的关系。Use里有个User指针，有个Value指针。

Value里有个Use list，它只由Use类的构造函数维护。当构建Use对象的时候，自动给Value类里的list添加Use。当Use对象解构的时候把自己从Value类里的list移除。这里的list是`std::list<Use*>`。

而User是Instruction的父类，而且基本上只有Instruction是User。然而Value有很多，Call指令需要使用FunctionValue，跳转指令可能使用BasicBlockValue。

那指令的返回值被使用是什么情况呢？User也是继承自Value的，对应的是，Instruction在使用Operand（Value）的同时自身的结果也是一个Value。User除了作为Value要维护使用自己的对象，自身也要维护另外一个list代表自己使用了的Use关系。即`std::vector<Use>`。而且这里不是指针，意味着Use对象是否解构是属于User管理的。

由于Java没有严格意义上的destructor，所以需要在Use关系删除前，显式调用相关函数将Use类从Value里的列表中移除。同时可以在`finalize`函数里检查是否正确delete。 https://stackoverflow.com/questions/171952/is-there-a-destructor-for-java 总之删除Use关系要小心，注意双向关系的移除。

Mimic甚至将Function也作为User，使用的是BasicBlock，BasicBlock也是User，使用Instruction。而且Mimic里面的iterator和下标运算都是User里面定义的，这意味着mimic纯靠User的Use list保存指令的Operand。同时也没有显式的Use类的构造。Use构造是在AddValue添加到use list的时候（即也是添加到Operand）的时候构造的。然后Use的构造函数内部会给Value那边添加Use关系，完成双向链接。

仔细想想的话，其实就在于CallInst --use--> Function --contain--> BasicBlock --contain--> Inst --use--> BasicBlock。这里两个contain关系其实可以单独对待，由于两个use关系，所以Function和BasicBlock都至少需要作为Value。如果不把contain关系特殊对待，则它们也可以看作是User。把contain关系和use关系混在一起也不一定好？还是先不混在一起吧。

### 从语法检查后的DST找到生成的变量的Value

当一个变量生成Alloca指令后，后面继续遍历DST发现引用了前面的变量。虽然语义分析给对之前变量的引用填上了对应的Decl对象，但是我们怎么从Decl对象找到之前生成的Alloca指令的值呢？

FakeSSAGeneratorContext中增加了相关的Map，从对应的DST对象映射到Func和Alloca指令所代表的Value。

### builtin函数的声明

为每个模块都增加builtin函数的声明，一方面转LLVM IR的时候引用函数可以不报错，另外也可以顺带加入Map，方便之后找到FuncValue。

### 观察LLVM IR

使用下面的简单Makefile，然后在相同目录下编写简单的C语言文件，执行make命令即可。

```
.PHONY: all clean
all: $(addsuffix .ll, $(basename $(wildcard *.c)))

%.ll: %.c
	clang-12 -S -emit-llvm --no-standard-libraries -o ./$@ ./$<

clean:
	rm *.ll
```

### DST的IR生成

#### 类型

sysy只有基本的类型int float和较为复杂的数组类型，没有复杂的指针和结构体类型。函数参数可以是复杂的数组类型，但是函数返回值只会是int float void之一。

由于部分库函数可能超出这个类型范围，比如格式化字符串的情况。所以需要处理函数实参为字符串常量的情况。

参考LLVM IR，全局变量必须有初始值，原来没有就要加一个0。然而局部变量就完全不同，常量的局部变量可以直接表示为Constant Value在后面的指令上直接用。全局变量引用名字的时候其实只是全局变量的地址，访问值需要一个Load指令。

由于短路求值还是有点复杂，同时为了更灵活，所以还是需要i1类型，然后使用branch指令直接根据i1跳转。因此单独构建一个Type类，支持的类型包括i1, i8数组，i8*, i32, float数组和指针（函数参数省略一维直接当作指针）。为Constant、GlobalVariable等提供支持。局部变量是隐式使用的，在遇到定义的时候alloca，然后生成初始化值的语句。对于Const变量可以直接解析出来。为了不影响前端数据结构，直接定义新的类似的类型。

局部变量中，Const变量仅可以对非array的不生成alloca，是array的Const局部变量还是考虑同时当作普通数组生成到栈空间，没有用到可以之后写个pass消除。（主要是Const数组不知道有没有当参数传，全局Const数组可以直接对应LLVM里的constant，如果GlobalVariable当参数传了那还是得对应到对应的局部变量。）

由于LLVM的数组常量有迭代的每一层的类型声明，因此对应构建了一个Constant类。其中有数组的递归的结构，但是每一层都保存有自己的Type。
```
@a = dso_local global [3 x [2 x [2 x i32]]] [[2 x [2 x i32]] [[2 x i32] [i32 1, i32 0], [2 x i32] zeroinitializer], [2 x [2 x i32]] [[2 x i32] [i32 6, i32 2], [2 x i32] [i32 4, i32 5]], [2 x [2 x i32]] zeroinitializer], align 16
```

#### 生成时的隐性约定

Context里有一个current指针，指向当前基本块。语句生成结束后需要确保这个指针指向的基本块是和整个语句同级的。

比如生成if语句这种需要基本块结构的情况，先生成计算条件的语句和跳转指令，指向if和else两个基本块。然后首先将current指向if的基本块，然后递归visit if块生成语句，然后再将current指向else块，递归调用visit生成else块内的语句。最后由于没有和if语句同级的块，需要再生成一个exit块，将if和else无条件跳转到这个块，然后将current恢复到这个块。

由于控制流语句可能会提前生成后续结构，所以生成的时候允许current结尾是无条件跳转，插入时插入到中间。首先split basic block，即将无条件跳转放到新的单独的BasicBlock，原来的BasicBlock作为Entry，新的BasicBlock作为Exit。（或者使用BasicBlock.addBeforeTerminator直接插入到中间）

### GetElementPtr 第一维是否一直是0

普通的计算地址时，比如直接访问数组变量，确实一直是0。构建指令的时候传入数组类型的指针值（全局变量或者局部变量（alloca）都是），然后先0，再index到数组内。

当数组作为函数参数第一维省略的时候，直接变成少一维的指针类型了。此时就没有0，直接index就到数组内了。（参数为指针也仅可能是数组传参这一种情况。）

### 递归设置数组变量初始值

首先调用memset将数组区域归零。然后遍历赋值每个非零值。
通过InitValue新增的isAllZero的flag判断是否需要进入。
递归的过程参照之前的flatten的过程。但是更简单
