package ir.ds;


import dst.ds.Decl;

public class DeclSymbol implements Symbol{
    public Decl decl;

    public DeclSymbol(Decl decl) {
        this.decl = decl;
    }

    @Override
    public String getName() {
        return decl.id;
    }
    
}
