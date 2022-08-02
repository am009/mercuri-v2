package ssa.ds;

/*
 * 基本语法：
 *      <result> = getelementptr <ty>, <ty>* <ptrval>, {<ty> <index>}*，
 * 
 * 其中第一个 <ty>  表示第一个索引所指向的类型
 * 第二个 <ty>     表示后面的指针基址 <ptrval> 的类型，
 * <ty> <index>   表示一组索引的类型和值。
 * 要注意索引的类型和 索引指向的基本类型 是不一样的，索引的类型一般为 i32 或 i64 
 * **而 索引指向的基本类型 确定的是增加索引值时指针的偏移量。**
 * 
 * 例子：
 * %1 = getelementptr [5 x [4 x i32]], [5 x [4 x i32]]* @a, i32 0, i32 2  
 * %1 类型为 [4 x i32]*，可以理解为 C 语言中指向长度为 4 的一维数组基址的指针
 * 
 * https://llvm.org/docs/LangRef.html#getelementptr-instruction
 * https://llvm.org/docs/GetElementPtr.html
 * https://buaa-se-compiling.github.io/miniSysY-tutorial/lab7/help.html
 */
/**
 * GEP 指令：用于计算地址（无副作用，不会读写数据）。计算结果的接收者是 %name
 * 第一个参数：用于计算基准类型
 * 第二个参数是一个或一向量的指针，作为基地址
 * 其余的参数是索引
 */
public class GetElementPtr extends Instruction {
    public Type base;

    public GetElementPtr(BasicBlock parent_bb, Value ptr) {
        assert ptr.type.isPointer;
        parent = parent_bb;
        oprands.add(new Use(this, ptr));
        // 非参数时第一维是0 (是参数时必然省略第一维)
        if (!(ptr instanceof ParamValue)) {
            oprands.add(new Use(this, ConstantValue.ofInt(0)));
        }
        type = ptr.type.clone();
        this.base = ptr.type.clone();
        this.base.isPointer = false;
    }

    public GetElementPtr addIndex(Value v) {
        assert v.type.baseType == PrimitiveTypeTag.INT && (!v.type.isArray());
        if (oprands.size() > 1) { // 只取一维类型不变
            type = type.subArrType();
            type.isPointer = true;
        }
        oprands.add(new Use(this, v));
        return this;
    }

    static String format = "%s = getelementptr %s, %s* %s";

    // getelementptr <ty>, <ty>* <ptrval>{, <ty> <idx>}*
    @Override
    public String toString() {
        var s = String.format(format, toValueString(), base.toString(), base.toString(),
                oprands.get(0).value.toValueString());
        var b = new StringBuilder(s);
        oprands.subList(1, oprands.size()).forEach(
                use -> b.append(", ").append(use.value.type.toString()).append(" ").append(use.value.toValueString()));
        if (comments != null) {
            b.append("     ; ").append(comments);
        }
        return b.toString();
    }
}
