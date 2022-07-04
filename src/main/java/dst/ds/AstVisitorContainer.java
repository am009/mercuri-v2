package dst.ds;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

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