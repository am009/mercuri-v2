package dst;

import ast.SysyParser.CompUnitContext;
import dst.ds.AstVisitorContainer;
import dst.ds.CompUnit;
import dst.ds.DstGeneratorContext;

public class DstGenerator {

    public CompUnit process(CompUnitContext compUnitContext, String filename) {
        var genContext = new DstGeneratorContext(new AstVisitorContainer(), compUnitContext, filename);
        return genContext.getVisitors().of(AstCompUnitVisitor.class).visitCompUnit(genContext);
    }

}
