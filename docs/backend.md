# Backend

为了之后的窥孔优化，指令的类尽量接近底层指令。除了个别指令如Prologue和Ret指令还是抽象指令。

TODO:
1. 完善窥孔优化逻辑，使用更多指令。 
1. 写一个线性优化pass，分析代码之间距离，对跳转超出32MB范围的改为绝对地址跳转。
1. 未来加入新的后端架构的时候考虑架构相关的和架构无关的逻辑的解耦，和设计相关的接口。
1. 将可以带立即数，带offset，和相关range这种更通用地编码到指令里面。不求做到能直接根据什么规则生成相关的类和匹配代码，先追求每个指令类里的信息接近于指令特点的声明，降低generator里的重复代码。

### 基本块结构

在源码里，没有直接的跳转，而是由AST带来的隐式跳转，比如if的then结尾跳转到else结尾。所以IR生成的时候，跳转也知道是condExpr，递归调用的时候直接传入了创建好的true和false基本块。但是在后端的时候，指令已经平坦化了，所以如果还是按顺序遍历基本块和指令，会发现处理结尾的跳转指令的时候，跳转目标还没有遍历到，而且要求基本块能够通过名字唯一标识似乎不太好。所以还是先构建一个map，从SSA-IR基本块映射到ASM基本块。然后再去遍历。

由于Asm基本块的跳转指令可能有多个，因此显式维护一下前驱和后继关系。

### 指令选择架构

没有采用更高级的Tree pattern matching，还是基于更简单的直接生成，通过后续的窥孔优化来保证代码质量。也因此各个指令需要直接生成较为底层的指令（除了prologue和epilogue），而不是在toString的时候直接生成等价的多条指令。

相关资料：
1. 《Engineering a Compiler》的指令选择一章
2. [Survey on Instruction Selection: An Extensive and Modern Literature Review](https://arxiv.org/abs/1306.4898) 的`2.3.2 Combining naïve macro expansion with peephole optimization`(`The Davidson-Fraser approach` )

TODO：
1. 窥孔优化

### 寄存器分配

由于经验分享里面说，正确实现线扫也不容易，但是图着色也容易出bug，为了debug还是需要实现一个trivial的简单寄存器分配算法。将寄存器全分配在栈上还是太粗暴了，稍微优化一点就可以得到《Engineering a Compiler》说的local寄存器分配策略。因此先实现这种。在基本块结束的时候将所有live的global变量放回栈中，因此也还是需要liveness分析。

相关资料：
1. 《Engineering a Compiler》的寄存器分配一章

##### Log

2022年8月3日 发现Call指令use的寄存器，在call之后依然认为相关参数仍在寄存器中，然而实际上已经被破坏。普通的指令的use需要判断之后是否还是在用，在用的就要再计算一下next数组的值，不在用的直接free掉。call参数这种属于之后不管在不在用都会消失，在用的要从已经分配的寄存器spill掉，也不用计算next值了，不再用的直接free掉。

2022年8月4日 发现spill一个寄存器的时候，如果在栈上分配了太多空间，会导致sub的时候不能用imm，需要先mov到IP然后再sub。但是计算的结果不能直接用目标寄存器，而是要再找一个寄存器，否则的话计算出来的地址直接覆盖了要spill的值。发现可以复用IP寄存器，直接再用IP。

##### r12 的用途
Intra-Procedure-call scratch register，为了方便prologue和epilogue计算留下的寄存器，总之作为临时用一用的寄存器。比如MOV32带上另外一个指令这种临时加载一个值然后用的情况。

##### LDM指令

https://keleshev.com/ldm-my-favorite-arm-instruction/ 

一开始还有点怀疑ldm的reg list是有限制的，没想到真的是随便选寄存器加载。

#### 虚拟寄存器到SSA-IR临时值的映射

生成当前指令的时候怎么拿到之前的指令的结果作为操作数？需要用一个valueMap，从ssa的tmp值映射到虚拟寄存器。如果用到了参数，需要在函数开头将参数放入虚拟寄存器。用到了全局变量的值时，也要加载到虚拟寄存器里。

确实，寄存器分配之前，应该是假设自己有无限的虚拟寄存器，上面遇到的情况添加的指令最后都是需要的。虽然没有SSA的性质，但是由于IR那边的value是SSA且合法的，所以直接转为虚拟寄存器不会出现各种未定义的情况，use before assignment。

那是否应当在函数开头将所有的参数放入虚拟寄存器中？对放在寄存器的参数（前4个）好像可以，但是对于那些在内存中的参数，最好还是lazy地生成内存加载指令。（不太确定，还是翻一下《Engineering a Compiler》复习一下local分配的策略。）


### SSA-IR到ARM指令

https://developer.arm.com/documentation/qrc0001/m 最新版的参考卡，但是包含armv8。

https://gitlab.eduxiji.net/nscscc/compiler2021/-/blob/master/ARM%E5%92%8CThumb-2%E6%8C%87%E4%BB%A4%E9%9B%86%E5%BF%AB%E9%80%9F%E5%8F%82%E8%80%83%E5%8D%A1.pdf 

https://d1.amobbs.com/bbs_upload782111/files_31/ourdev_570540.pdf 中文版的另一个

有指令看不懂的时候可以看看《ARM+Cortex-M3与Cortex-M4权威指南》虽然有很多不相关和不一样的地方。但是讲得非常简单易懂

### 目标架构

架构选择参考https://sourceware.org/binutils/docs-2.38/as.html 里的arm相关部分 `-march=architecture[+extension…]`部分
Trivial-Compiler里写的是`armv7ve`

本来可以看[树莓派4的datasheet](https://datasheets.raspberrypi.com/rpi4/raspberry-pi-4-datasheet.pdf)的，但是比赛是装的32位系统，所以甚至操作系统显示的不是armv8。

首先应该不是直接`-march=armv7`，因为我发现会报错说不支持32位指令，只支持thumb指令。所以应该至少是`-march=armv7-a`。搜了下说`Armv7ve is basically a armv7-a architecture profile with Virtualization Extensions`。

关于浮点看https://stackoverflow.com/questions/64685787/what-are-the-correct-cpu-fpu-assembly-entries-for-the-raspberry-pi-4 之后在看。应该`+vfpv4`或者`+fp`就可以

下面是makefile文件实例，可以方便地编译.S文件而不用反复输入命令
```makefile
.PHONY: all clean
all: $(addsuffix .S, $(basename $(wildcard *.c)))

%.ll: %.c
	clang-12 -S -emit-llvm --no-standard-libraries -o ./$@ ./$<  #  -Wl,--export-all

%.S: %.c
	arm-linux-gnueabihf-gcc-10 -S -march=armv7-a -o ./$@ ./$<

%.o: %.S
	arm-linux-gnueabihf-gcc-10 -c -march=armv7-a -o ./$@ ./$<

clean:
	rm *.ll *.S
```

#### 跳转

函数调用用BL，BLX，无条件跳转用B，BX。函数返回用BX lr。而且注意带X的一般搭配寄存器使用，不带X的搭配label使用。`BLX label always changes the instruction set`所以不要用错了。

直接跳转label都存在32MB的限制，因此当范围过大的时候都要换成加载32位立即数+跳转寄存器的模式。这个问题比较复杂，在x86那边是由汇编器解决的，但是arm这边算法好像没用过来。目前只能先全生成直接跳转，因为不太可能超过32MB，同时增加一个选项，之后如果出现问题，可以将所有的跳转都切换为非直接跳转。

http://altmer.arts-union.ru/3DO/docs/DevDocs/tktfldr/acbfldr/1acbe.html 

有一个测例好像是考察的极端情况。假如一个函数极长且没有无条件跳转（无条件跳转后面可以放常量池），没有各种跳转。此时在函数中间需要加载32位常量，该怎么办？

https://developer.arm.com/documentation/dui0204/j/writing-arm-assembly-language/loading-constants-into-registers/loading-with-mov32?lang=en 这里说了，新的指令集有一次load 16bit的两个指令，正好两个指令解决问题。直接使用新的MOV32伪指令即可。但是GNU assembler 好像不支持。https://sourceware.org/binutils/docs-2.38/as.html#ARM_002dRelocations

从而可以想到一个问题，编译器和链接器功能分开的话，编译器怎么知道某个跳转需要跳多少地址，从而判断这个跳转是否在B label的范围？而且短跳转和长跳转占的位置也不一样，一个指令从长跳转到短跳转的变化会影响整个函数的大小，从而导致其他的跳转可能也发生变化。这个问题叫做`branch displacement optimization`
- https://stackoverflow.com/questions/52825730/how-to-select-jump-instructions-in-one-pass
- https://stackoverflow.com/questions/34911142/why-is-the-start-small-algorithm-for-branch-displacement-not-optimal 

但是其实32MB的范围非常大了，不会真的有测试用例的代码大小超过32m吧。最多也就是你的常量池太远了

用汇编测试发现，超了范围直接报错。而不是会有什么缓解措施。`./simple.S:4: Error: branch out of range`
```S
.text
.code 32
a:
	BL b
	BX lr
.space 0x2000000
b:
	BX lr
```

看到这么一段：GCC estimates the size of each instruction and uses that to decide if it can use a short or a long branch. Long chunks of inline assembly can break that and make it think the code is shorter than it is.也就是说编译器里还是需要简单的代码长度估测的。

本来觉得可以写这样一段宏：
```S
.macro bl_extended target
.ifgt \target-.-0x1f00000
	MOVW r0, #:lower16:\target
	MOVT r0, #:upper16:\target
	BLX r0
.else
	.iflt \target-.+0x1f00000
		MOVW r0, #:lower16:\target
		MOVT r0, #:upper16:\target
		BLX r0
	.else
		BL \target
	.endif
.endif
.endm
```
但是在处理向前跳转时计算offset还是有问题。因为你要向前跳转，而后面的label和当前位置的offset还取决于你这个宏产生多少指令。也是报错non-constant expression in ".if" statement。

感觉可能应该生成Position independent code。各种跳转都去GOT表里取偏移。然后生成relocation相关的，在连接的时候重定位。

#### 重定位

- https://blog.llvm.org/posts/2021-10-01-generating-relocatable-code-for-arm-processors/

因为我们生成的还只是object文件，而不是最后的可执行文件，所以如果用到了很多地址相关的要重定位。比如链接的时候会把代码段放在一起，数据段放在一起。所以用到数据段的地址的时候就会有一个重定位项。比如有变量f，我在代码段使用了`MOVW r0, #:lower16:f`，使用`readelf -r simple.o`打印重定位信息，就会显示有一个项。
```
Relocation section '.rel.text' at offset 0x158 contains 1 entry:
 Offset     Info    Type            Sym.Value  Sym. Name
00000004  0000092b R_ARM_MOVW_ABS_NC 00000008   f
```

那会不会存在这种情况，即重定位后因为超出了指令的寻址范围而无法用当前指令，需要拓展为更多指令呢？应该不会，因为有范围的都是相对寻址，不需要重定位。需要重定位的都是绝对地址。

- 函数内部之间跳转，

#### ABI与调用约定

不错的文章：
- https://eli.thegreenplace.net/2011/09/06/stack-frame-layout-on-x86-64/
- https://stackoverflow.com/questions/30190132/what-is-the-shadow-space-in-x64-assembly

简单的ABI（只有32位int类型）大致是这样：

- 前4个参数放到r0-r4
- 后面的参数依次放到栈上

遇到了32位浮点数后，事情变得复杂了一些：基于使用的Procedure Call Standard有两种可能：一种是坚持使用Base Procedure Call Standard（可能是要和thumb交互吗？我在compiler explorer里加上`-march=armv7-a`是用的这种），另外一种是使用VFP variant standard（用IDA打开给的sylib.a看putfloat函数，参数真是通过s0传递的，看来用的是这种）

- 浮点参数优先用s0-s15传递

遇到了可变参数后，事情变得非常复杂。在可变参数函数里，传的float类型都要提升到double后传参。导致突破了类型系统。直接带来了对齐问题

- 可变参数部分不使用任何vfp寄存器传参，仅使用Base Procedure Call Standard。
- 比如`printf("%a", 1.0f);`中，r0保存字符串指针，r2-r3保存提升后的double

因此需要为每个函数计算一遍该过程，设置好参数需要预留的空间（遍历到Call指令的时候要将当前预留的参数空间设置为Call指令需要的最大值。）

### 硬件不直接支持的运算

部分不支持的运算要转换为函数调用，而且这一步要在汇编指令生成前用一个Pass去处理

1. modulo 取模：调用`___modsi3`
2. 浮点数相关：

### 栈布局

栈分为三块，如下图所示：好处是，我先不生成prologue和epilogue，即不确定栈的大小，指令生成的时候可以直接基于fp取到参数，遇到alloca可以直接沿着fp向上分配空间。然后指令生成阶段结束后，寄存器分配需要spill寄存器的时候就基于指令生成分配的空间继续向上分配。遇到函数调用需要栈空间的，直接基于sp向下放置参数，最后留的时候取最大的空间要求即可。最后把这几部分空间都加起来得到整个栈的空间。

```
/** Low address, stack grow upwards
 * ┌─────────────┐
 * │             │
 * │             │
 * │space for arg│- sp
 * │     ...     │
 * │ spilled reg │-12
 * │     ...     │-a
 * │ alloca local│-4
 * ├─   fp/lr   ─┤0 - fp
 * │    fp/lr    │+4
 * │    arg1     │+8
 * │    arg2     │+12
 * │    ...      │
 * ├─────────────┤
 * │             │
 * High address
```

优点非常明显，即很多offset不需要后面返回来fixup。但是问题也很严重，没有考虑到callee saved register需要在prologue和epilogue push指令带着push的情况。即原本只占8字节的fp/lr现在长度从8字节到24字节长度不等。影响了访问参数时基于fp的offset。而offset的变化甚至会潜在地影响指令生成，即如果导致立即数字段超范围了，则需要生成add/sub指令等。

一个解决的办法是，这些寄存器直接不push，由寄存器分配算法在函数开头和结尾的时候放到spilled reg空间里。但是相比前面的解决办法多了很多store和load指令。另外一种办法是，直接。

在 https://godbolt.org/ 用armv7a-clang 9.0.1 看到另外一个办法是，保存fp的时候不是直接mov，而是改为一个add指令，将fp还是调整到现在这种假装自己只push了两个寄存器的状态，然后把多push的寄存器看作是那边分配的。然后也不严格区分alloca区域和spilled reg区域。但是后期确定了这部分的大小之后要反过来修复alloca相关的指令的offset。

搜了下，好像性能上并不会快很多，毕竟还是要访存的，那我还是沿用旧的设计思路，把需要保存的寄存器放到spill的空间里，虽然代码大小大了一点。

https://stackoverflow.com/questions/61375336/why-do-compilers-insist-on-using-a-callee-saved-register-here 

### 寄存器分配

现在看来，寄存器分配的问题的场景是，在指令选择中用到的所有虚拟寄存器都基本上是最后要变成真实寄存器的。而通过插入load和store指令将某些寄存器先临时保存到栈上，让这些虚拟寄存器都能在指令选择相关运算中临时成为真实寄存器。此外比较麻烦的一点是，函数调用约定和返回值约定使得在各种程序点都需要让某些虚拟寄存器放到正确的位置上。

听说正确实现线扫也非常复杂，所以最开始需要开发使用更加简单的寄存器分配算法。选择基于《Engineering a compiler》里的BottemUp分配算法。而Local（仅支持单个基本块）的算法为了拓展到global的，在基本块分界的时候要把所有的global变量放到内存里。

写的时候感觉非常复杂，不得不说寄存器分配真的难写。使复杂度暴增的主要是：
- 需要支持浮点寄存器的分配
- 需要处理调用约定所带来的约束：如函数r0-r4传参，浮点数s0-s15传参
- 处理callee saved reg在函数开始和结束的保存

正好要对每个基本块逆向遍历一遍，顺带可以计算出global的寄存器。

#### 寄存器分配的约束如何处理

书中的算法完全没有考虑调用约定所带来的约束问题。
- 约束可能在后面，前面没考虑到。比如某变量之后要返回，需要放到r0，但是刚计算出来的时候，寄存器分配算法随便分配了一个非r0寄存器。
- 约束本身都可能冲突：比如返回值要求某变量放在r0，参数要求该变量放在r1。

这部分想了好久，最后基本还是接近回到了原点。原点是不考虑约束，先随便分配寄存器，然后在遇到约束的时候临时满足。目前的解决方案是，在预分析的时候同时收集虚拟寄存器的约束，作为一个hint，然后在分配的时候如果hint的寄存器是空闲的就优先分配，不是空闲的则依然随意分配。可以感觉到效果不会好很多。

https://people.cs.rutgers.edu/~farach/pubs/cc99-tk.pdf

https://www.geeksforgeeks.org/register-allocations-in-code-generation/ 关注register assignment部分

- a pseudo-register represents only one live range, and thus a pseudo-register is defined at most once
- each pseudo-register can be stored and retrieved in a designated memory location
- (register assignment) Maps an allocated name set to the physical register set of the target machine. An assignment can be produced in linear time using Interval-Graph Coloring。

论文里确实说要最后分出一个register assignment阶段，这个才是用来解决CallingConvention的各种约束的。寄存器分配的时候还是分配的“虚拟”物理寄存器，至于哪个虚拟物理寄存器分配给哪个真实寄存器由register assignment决定。关键在于前面的寄存器分配的结果要是allocated name set。只要没有同时使用超过K个寄存器即可。每次分配的都是新的寄存器。

Interval-Graph Coloring 可以看 http://www.cse.msu.edu/~huding/331material/notes6.pdf 最后面一部分，挺简单的。现在想来发现这个local的寄存器分配还真的是Scheduling All Intervals。每个interval感觉上是一个虚拟寄存器的live range。寄存器分配就是当你发现同时存在的interval过多的时候，在合适的地方断开一些。实际上在书上写的算法的基础上，当一个值进入寄存器的时候作为起始点，被Free掉或者spill掉的时候作为结束点，然后这一段interval就是一个allocated name set，代表一个物理寄存器，但是至于是哪个可以留给后面的算法。每个物理寄存器都看作一条流水线，用一个Interval的List表示，每次有约束的时候就提前直接放到对应流水线里。

但是带约束的Scheduling Intervals问题怎么处理？关键是如何在有冲突的时候第一时间检测出来。其实只需要维护一下每个物理寄存器被当前哪个虚拟寄存器占用了即可。

对于带来复杂性的几个问题：
- CalleeSavedReg：看作存在几个带约束的虚拟寄存器在函数开头有定义，结尾有使用。不需要作为一类特殊的寄存器，只需要初始化的时候填上已经被使用的数据，同时在函数开头和结尾约束好物理寄存器即可。
- 函数调用导致Caller Saved reg被占用的情况：要求此时在寄存器内值spill到少于可用寄存器的数量。
	- Call指令：拆分为几个阶段：初始阶段处理约束要求参数在对应寄存器内，同时寄存器使用完毕，能释放的可以释放，中间阶段，此时首先处理现有的有物理约束在Caller Saved reg的interval，然后处理寄存器数量约束（总之把寄存器都赶到callee saved里），结束阶段处理返回值定义且带约束。
- 返回值约束是否会冲突？首先返回指令必然是基本块的最后一个指令，此时所有的值应该都基本被Free了。应该不可能有冲突。注意结尾不用store global值到内存中了。
- 约束存在无法满足的情况：比如如下图三个interval，但是如果a被约束在寄存器1，c被约束在寄存器2，则a和c不能在同一个寄存器里，则无法用两个寄存器解决。而register assignment时，寄存器分配已经算是结束了，不太好重新把值spill，就算要也应该在寄存器分配那里增加检测机制，遇到冲突及时解决。
	- 但是约束都是仅在区域头和尾出现的。我们可以先正常分配，能满足的满足，不能满足的则可以插入mov指令，强行交换到目标寄存器里。（借助空闲的IP寄存器）
	```
	a----  c----
	b---------
	```

最后感觉即使拆分出register assignment，也用处不大。最后的主体代码结构基本还是参照着书上的顺序写的。

### 类型转换

aeabi.h文件：https://gist.github.com/harieamjari/61aa4420ae4ded5e86f5143e46d93573

|运算|需要转换的函数||
|-|-|-|
|~~有符号32位int除法~~|__aeabi_idiv(int numerator, int denominator)||
|~~无符号32位int除法~~|unsigned __aeabi_uidiv(unsigned numerator, unsigned denominator);||
|有符号取模|int __aeabi_mymod(int numerator, int denominator);（自定义）||


SDIV和UDIV指令居然仅支持Thumb状态。。所以整数除法需要转换为函数调用 `__aeabi_idiv`。但是搜了下发现https://community.arm.com/support-forums/f/architectures-and-processors-forum/5638/how-to-understand-sdiv-instruction-availability 只要设置cpu为更新的版本即可。

取模运算和除法运算居然是在一起的，返回的是一个结构体typedef struct { int quot; int rem; } idiv_return;。因为现在语法只允许函数返回int或者float，如果要直接支持比较麻烦。所以自己搞一个汇编函数吧，调用__aeabi_idivmod然后把r1(rem)放到r0返回。[比如这样](https://godbolt.org/#g:!((g:!((g:!((h:codeEditor,i:(filename:'1',fontScale:14,fontUsePx:'0',j:1,lang:___c,selection:(endColumn:19,endLineNumber:10,positionColumn:19,positionLineNumber:10,selectionStartColumn:19,selectionStartLineNumber:10,startColumn:19,startLineNumber:10),source:'//+%23include+%3Caeabi.h%3E%0A%0Atypedef+struct+%7B+int+quot%3B+int+rem%3B+%7D+idiv_return%3B%0Atypedef+struct+%7B+unsigned+quot%3B+unsigned+rem%3B+%7D+uidiv_return%3B%0Aextern+idiv_return+__aeabi_idivmod(int+numerator,+int+denominator)%3B%0Aextern+uidiv_return+__aeabi_uidivmod(unsigned+numerator,+unsigned+denominator)%3B%0A%0Aint+__aeabi_mymod(int+a,+int+b)+%7B%0A++++return+a%25b%3B%0A++++//+return+__aeabi_idivmod(a,+b).rem%3B%0A%7D%0A'),l:'5',n:'0',o:'C+source+%231',t:'0')),k:50.000000000470834,l:'4',n:'0',o:'',s:0,t:'0'),(g:!((h:compiler,i:(compiler:armv7-cclang1100,filters:(b:'1',binary:'1',commentOnly:'1',demangle:'1',directives:'0',execute:'0',intel:'0',libraryCode:'1',trim:'1'),flagsViewOpen:'1',fontScale:14,fontUsePx:'0',j:2,lang:___c,libs:!(),options:'-Os',selection:(endColumn:19,endLineNumber:18,positionColumn:19,positionLineNumber:18,selectionStartColumn:9,selectionStartLineNumber:18,startColumn:9,startLineNumber:18),source:1,tree:'1'),l:'5',n:'0',o:'armv7-a+clang+11.0.0+(C,+Editor+%231,+Compiler+%232)',t:'0')),k:49.99999999952918,l:'4',n:'0',o:'',s:0,t:'0')),l:'2',n:'0',o:'',t:'0')),version:4)

### 浮点运算

VLDR加载常量的伪指令，还支持64位浮点数 https://developer.arm.com/documentation/dui0801/g/Floating-point-Instructions--32-bit-/VLDR-pseudo-instruction--floating-point- 

各种浮点运算指令仅支持寄存器，不支持带常量。首先对于直接带常量的运算，目前整数那边是MOVW、MOVT，那对浮点数需要额外加一个VMOV。看来之后真的需要加常量池了，这个指令数量有点多。在指令生成里，也需要把所在的寄存器类型保存起来，然后在转换IR的值的时候根据IR那边Type的isBaseFloat()函数。目前先在AsmOperand里面增加isFloat吧，之后要拓展再考虑改为enum。

### 变参函数浮点传参

ssa-ir那边是有个fext，但是后端这边遇到了fext的cast就先什么都不做，然后由那边处理参数的时候处理。Call指令判断是不是double类型，是就一定是传float然后提升上来的。然后直接利用额外的d16-d31寄存器将原来的浮点数拓展到d16然后再根据是在栈上还是寄存器里生成对应的VMOV或者VSTR。

### 支持Imm的指令

目前有以下支持Imm的指令。
- ~~ADD/SUB Rd, Rn, #<imm12> 范围0-4095~~ 仅在Thumb-2支持
- `ADD/SUB Rd, Rn, <Operand2>`
- `CMP Rn, <Operand2>`
- `LDR/STR Rd, [Rn {, #<offset>}]` -4095 to +4095
- `VSTR/VLDR Fd, [Rn{, #<immed>}]`  Immediate range 0-1020, multiple of 4.

想了想，直接在指令中都增加一个isImmFit函数，因为有Operand2这种灵活性很强的常量，所以还是定义成函数接口的形式。
