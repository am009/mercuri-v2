package dst;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import ast.SysyParser.CompUnitContext;
import dst.ds.CompUnit;

public class DstGenerator {
    private CompUnitContext compUnitContext;
    private AstVisitorContainer visitorContainer;
    private String filename;

    public class AstVisitorContainer {
        private Map<String, Object> visitorMap = new HashMap<String, Object>();

        @SuppressWarnings("unchecked")
        public <U, T extends AbstractParseTreeVisitor<U>> T of(Class<T> visitorClass) {
            String className = visitorClass.getSimpleName();
            if (visitorMap.containsKey(className)) {
                return (T) visitorMap.get(className);
            } else {
                try {
                    var visitor = visitorClass.getDeclaredConstructor().newInstance();
                    visitorMap.put(className, visitor);
                    return visitor;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    public DstGenerator(CompUnitContext compUnitContext, String filename) {
        this.compUnitContext = compUnitContext;
        this.visitorContainer = new AstVisitorContainer();
        this.filename = filename;
    }

    public CompUnit generate() {
        return visitorContainer.of(AstCompUnitVisitor.class).visitCompUnit(compUnitContext, filename);
    }

}
