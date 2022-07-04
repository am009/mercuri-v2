package dst.ds;

import ast.SysyParser.CompUnitContext;

public class DstGeneratorContext {
    private AstVisitorContainer visitorContainer;
    private CompUnitContext astRoot;
    private CompUnit root;

    public CompUnit getRoot() {
        return this.root;
    }

    private final String filename;

    public CompUnitContext getRootAst() {
        return this.astRoot;
    }

    public String getFilename() {
        return this.filename;
    }

    public DstGeneratorContext(AstVisitorContainer visitorContainer, CompUnitContext compUnitContext, String filename) {
        this.visitorContainer = visitorContainer;
        this.astRoot = compUnitContext;
        this.filename = filename;
    }

    public AstVisitorContainer getVisitors() {
        return visitorContainer;
    }

    public void panic(String msg) {
        throw new RuntimeException(msg);
    }
}
