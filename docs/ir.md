
# IR 设计

通过toString方法直接转为LLVM IR的文本形式。

顶层模块暂时叫Module吧，不要和ir/ds里的Module弄混了。生成的类暂时叫FakeSSAGenerator吧，防止有人误解是直出ssa。

设计时模仿了hblzbd、TrivialCompiler。不知道类该有什么成员时，参照LLVM IR (https://llvm.org/docs/LangRef.html )

生成时，局部变量先全部使用alloca指令，之后再通过mem2reg转SSA，mem2reg模仿的是mimic（TODO 加链接）。

考虑使用Basic Block Argument 替代Phi指令 TODO 加资料

### User & Use & Value

最初解读自hblzbd的代码：hblzbd_compiler\include\ssa.h，但是TrivialCompiler和Mimic都是这么做的。

Use代表关系，User使用Value的关系。Use里有个User指针，有个Value指针。

Value里有个Use list，它只由Use类的构造函数维护。当构建Use对象的时候，自动给Value类里的list添加Use。当Use对象解构的时候把自己从Value类里的list移除。这里的list是`std::list<Use*>`。

而User是Instruction的父类，而且基本上只有Instruction是User。然而Value有很多，Call指令需要使用FunctionValue，跳转指令可能使用BasicBlockValue。

那指令的返回值被使用是什么情况呢？User也是继承自Value的，对应的是，Instruction在使用Operand（Value）的同时自身的结果也是一个Value。User除了作为Value要维护使用自己的对象，自身也要维护另外一个list代表自己使用了的Use关系。即`std::vector<Use>`。而且这里不是指针，意味着Use对象是否解构是属于User管理的。

由于Java没有严格意义上的destructor，所以需要在Use关系删除前，显式调用相关函数将Use类从Value里的列表中移除。同时可以在`finalize`函数里检查是否正确delete。 https://stackoverflow.com/questions/171952/is-there-a-destructor-for-java 

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

### 类型设计

sysy只有基本的类型int float和较为复杂的数组类型，没有复杂的指针和结构体类型。

由于部分库函数可能超出这个类型范围，比如格式化字符串的情况。所以需要处理函数实参为字符串常量的情况。

函数参数可以是复杂的数组类型，但是函数返回值只会是int float void之一。