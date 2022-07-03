package dst.ds;

public class DstGeneratorContext {
    private AstVisitorContainer visitorContainer;

    public DstGeneratorContext(AstVisitorContainer visitorContainer) {
        this.visitorContainer = visitorContainer;
    }

    public AstVisitorContainer getVisitors() {
        return visitorContainer;
    }

    public void panic(String msg) {
        throw new RuntimeException(msg);
    }
}
