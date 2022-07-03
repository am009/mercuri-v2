package dst;

import ast.SysyBaseVisitor;
import ast.SysyParser;
import ds.Global;
import dst.ds.CompUnit;

public class AstCompUnitVisitor extends SysyBaseVisitor<CompUnit> {
    public CompUnit visitCompUnit(SysyParser.CompUnitContext ctx, String filename) {
        var ret = new CompUnit(filename,);
        ret.file = filename;
        Global.logger.warning("Compilation unit not implemented yet." + filename);
        return ret;
    }
}
