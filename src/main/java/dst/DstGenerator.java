package dst;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import ast.SysyParser.CompUnitContext;
import dst.ds.AstVisitorContainer;
import dst.ds.CompUnit;
import dst.ds.DstGeneratorContext;

public class DstGenerator {
    private CompUnitContext compUnitContext;
    private AstVisitorContainer visitorContainer;
    private String filename;


    public DstGenerator(CompUnitContext compUnitContext, String filename) {
        this.compUnitContext = compUnitContext;
        this.visitorContainer = new AstVisitorContainer();
        this.filename = filename;
    }

    public CompUnit generate() {
        var genContext = new DstGeneratorContext(visitorContainer);
        return visitorContainer.of(AstCompUnitVisitor.class).visitCompUnit(compUnitContext, genContext, filename);
    }

}
