package ir.ds;

import java.util.ArrayList;
import java.util.List;
import dst.ds.BasicType;
import dst.ds.Block;
import dst.ds.Decl;
import dst.ds.Func;
import dst.ds.FuncType;

public class Module {
    public Scope globalScope;
    public String id;
    public List<Func> builtinFuncs;

    public Module(Scope globalScope, String id) {
        this.globalScope = globalScope;
        this.id = id;
        this.builtinFuncs = this.initBuiltinFuncs();
    }

    private List<Func> initBuiltinFuncs() {
        var list = new ArrayList<Func>();
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
        return list;
    }
}
