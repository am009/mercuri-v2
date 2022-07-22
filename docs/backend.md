# Backend

并不是直接转换为汇编指令，还是有一些抽象层次，比如Prologue指令和Ret指令。

TODO: 
1. 写一个线性优化pass，分析代码之间距离，对跳转超出32MB范围的改为绝对地址跳转。
1. 未来加入新的后端架构的时候考虑架构相关的和架构无关的逻辑的解耦，和设计相关的接口。

### 基本块结构

在源码里，没有直接的跳转，而是由AST带来的隐式跳转，比如if的then结尾跳转到else结尾。所以IR生成的时候，跳转也知道是condExpr，递归调用的时候直接传入了创建好的true和false基本块。但是在后端的时候，指令已经平坦化了，所以如果还是按顺序遍历基本块和指令，会发现处理结尾的跳转指令的时候，跳转目标还没有遍历到，而且要求基本块能够通过名字唯一标识似乎不太好。所以还是先构建一个map，从SSA-IR基本块映射到ASM基本块。然后再去遍历。

由于Asm基本块的跳转指令可能有多个，因此显式维护一下前驱和后继关系。

### 指令选择架构

没有采用更高级的Tree pattern matching，还是基于更简单的直接生成，通过后续的窥孔优化来保证代码质量。

相关资料：
1. 《Engineering a Compiler》的指令选择一章
2. [Survey on Instruction Selection: An Extensive and Modern Literature Review](https://arxiv.org/abs/1306.4898) 的`2.3.2 Combining naïve macro expansion with peephole optimization`(`The Davidson-Fraser approach` )

TODO：
1. 窥孔优化

### 寄存器分配

由于经验分享里面说，正确实现线扫也不容易，但是图着色也容易出bug，为了debug还是需要实现一个trivial的简单寄存器分配算法。将寄存器全分配在栈上还是太粗暴了，稍微优化一点就可以得到《Engineering a Compiler》说的local寄存器分配策略。因此先实现这种。在基本块结束的时候将所有live的global变量放回栈中，因此也还是需要liveness分析。

相关资料：
1. 《Engineering a Compiler》的寄存器分配一章

#### valueMap

生成当前指令的时候怎么拿到之前的指令的结果作为操作数？需要用一个valueMap，从ssa的tmp值映射到虚拟寄存器。如果用到了参数，需要在函数开头将参数放入虚拟寄存器。用到了全局变量的值时，也要加载到虚拟寄存器里。

确实，寄存器分配之前，应该是假设自己有无限的虚拟寄存器，上面遇到的情况添加的指令最后都是需要的。虽然没有SSA的性质，但是由于IR那边的value是SSA且合法的，所以直接转为虚拟寄存器不会出现各种未定义的情况，use before assignment。

那是否应当在函数开头将所有的参数放入虚拟寄存器中？对放在寄存器的参数（前4个）好像可以，但是对于那些在内存中的参数，最好还是lazy地生成内存加载指令。（不太确定，还是翻一下《Engineering a Compiler》复习一下local分配的策略。）


### SSA-IR到ARM指令

https://developer.arm.com/documentation/qrc0001/m 最新版的参考卡，但是包含armv8。

https://gitlab.eduxiji.net/nscscc/compiler2021/-/blob/master/ARM%E5%92%8CThumb-2%E6%8C%87%E4%BB%A4%E9%9B%86%E5%BF%AB%E9%80%9F%E5%8F%82%E8%80%83%E5%8D%A1.pdf 

https://d1.amobbs.com/bbs_upload782111/files_31/ourdev_570540.pdf 中文版的另一个

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
