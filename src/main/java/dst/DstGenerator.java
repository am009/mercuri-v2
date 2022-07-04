package dst;


import ast.SysyParser.CompUnitContext;
import dst.ds.AstVisitorContainer;
import dst.ds.CompUnit;
import dst.ds.DstGeneratorContext;

public class DstGenerator {

    private DstGeneratorContext genContext;

    public DstGenerator(CompUnitContext compUnitContext, String filename) {
        genContext = new DstGeneratorContext( new AstVisitorContainer(), compUnitContext, filename);
    }

    public CompUnit generate() {

        return genContext.getVisitors().of(AstCompUnitVisitor.class).visitCompUnit(genContext);
    }

}
