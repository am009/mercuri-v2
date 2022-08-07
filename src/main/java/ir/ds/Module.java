package ir.ds;

import java.util.ArrayList;
import java.util.List;
import dst.ds.BasicType;
import dst.ds.Block;
import dst.ds.Decl;
import dst.ds.DeclType;
import dst.ds.Func;
import dst.ds.FuncType;

public class Module {
    public Scope globalScope;
    public String id;
    public static List<Func> builtinFuncs;
    public static final int MEMSET = 0;

    public Module(Scope globalScope, String id) {
        this.globalScope = globalScope;
        this.id = id;
        initBuiltinFuncs();
    }

    private static void initBuiltinFuncs() {
        if (builtinFuncs != null) {
            return;
        }

        var list = new ArrayList<Func>();
        // memset for array initialization to zero
        list.add(new Func(FuncType.VOID, "memset", new ArrayList<Decl>() {
            {
                // var ret = new Decl(DeclType.VAR, true, false, BasicType.INT, "ptr", null, null);
                // ret.isDimensionOmitted = true;
                // add(ret); 
                add(Decl.fromSimpleParam("ptr", BasicType.STRING_LITERAL)); // TODO i8* pointer
                add(Decl.fromSimpleParam("c", BasicType.INT));
                add(Decl.fromSimpleParam("n", BasicType.INT));
            }
        }, Block.Empty));
        // int getint()
        list.add(new Func(FuncType.INT, "getint", new ArrayList<Decl>(), Block.Empty));
        // int getch()
        list.add(new Func(FuncType.INT, "getch", new ArrayList<Decl>(), Block.Empty));
        // int getfloat()
        list.add(new Func(FuncType.FLOAT, "getfloat", new ArrayList<Decl>(), Block.Empty));
        // int getarray(int[] x)
        list.add(new Func(FuncType.INT, "getarray", new ArrayList<Decl>() {
            {
                add(Decl.fromArrayParam("x", BasicType.INT, new ArrayList<>()));
            }
        }, Block.Empty));
        // int getfarray(float[])
        list.add(new Func(FuncType.INT, "getfarray", new ArrayList<Decl>() {
            {
                add(Decl.fromArrayParam("x", BasicType.FLOAT, new ArrayList<>()));
            }
        }, Block.Empty));
        // void putint(int x)
        list.add(new Func(FuncType.VOID, "putint", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("x", BasicType.INT));
            }
        }, Block.Empty));
        // void putch(int x)
        list.add(new Func(FuncType.VOID, "putch", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("x", BasicType.INT));
            }
        }, Block.Empty));
        // void putfloat(float x)
        list.add(new Func(FuncType.VOID, "putfloat", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("x", BasicType.FLOAT));
            }
        }, Block.Empty));
        // void putarray(int x, int[] y)
        list.add(new Func(FuncType.VOID, "putarray", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("x", BasicType.INT));
                add(Decl.fromArrayParam("y", BasicType.INT, new ArrayList<>()));
            }
        }, Block.Empty));
        // void putfarray(int x, float[] y)
        list.add(new Func(FuncType.VOID, "putfarray", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("x", BasicType.INT));
                add(Decl.fromArrayParam("y", BasicType.FLOAT, new ArrayList<>()));
            }
        }, Block.Empty));
        // void putf(fmt, ...)
        list.add(new Func(FuncType.VOID, "putf", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("fmt", BasicType.STRING_LITERAL));
            }
        }, Block.Empty).setIsVariadic(true));
        // int _sysy_starttime(int line);
        list.add(new Func(FuncType.INT, "_sysy_starttime", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("line", BasicType.INT));
            }
        }, Block.Empty));
        // int _sysy_stoptime(int line);
        list.add(new Func(FuncType.INT, "_sysy_stoptime", new ArrayList<Decl>() {
            {
                add(Decl.fromSimpleParam("line", BasicType.INT));
            }
        }, Block.Empty));
        builtinFuncs = list;
    }
}
