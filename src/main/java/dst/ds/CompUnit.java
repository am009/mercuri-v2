package dst.ds;

import java.util.List;

public class CompUnit {
    public String file;
    // variable & constant declarations
    public List<Decl> decls;
    // function declarations
    public List<Func> funcs;

    public CompUnit(String file, List<Decl> decls, List<Func> funcs) {
        this.file = file;
        this.decls = decls;
        this.funcs = funcs;
    }
}
