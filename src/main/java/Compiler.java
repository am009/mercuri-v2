import java.io.IOException;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import ast.SysyLexer;
import ast.SysyParser;
import ds.CliArgs;
import ds.Global;
import ds.Logger.LogLevel;
import ds.Logger.LogTarget;
import dst.DstGenerator;
import dst.ds.CompUnit;
import ir.SemanticAnalyzer;
import ds.LoggerBuilder;

/**
 * Driver for the compiler.
 */
public class Compiler {
    public static void main(String[] rawArgs) throws IOException {
        initLogger();
        var args = CliArgs.parse(rawArgs);
        Global.logger.log(LogLevel.TRACE, "Args: " + args);
        var charStream = CharStreams.fromFileName(args.getInFile());
        var tokenSource = new SysyLexer(charStream);
        var tokenStream = new CommonTokenStream(tokenSource);
        var parser = new SysyParser(tokenStream);
        var dstGen = new DstGenerator();
        CompUnit dst = dstGen.process(parser.compUnit(), args.getInFile());
        // !! IF_DEBUG
        var gb = new com.google.gson.GsonBuilder();
        gb.setPrettyPrinting();
        var gson = gb.create();
        // !! END_IF

        // !! IF_DEBUG
        var jsonDst = gson.toJson(dst);
        Global.logger.trace("--- dst ---");
        Global.logger.trace(jsonDst);
        // !! END_IF
        var semAnalyzer = new SemanticAnalyzer();
        semAnalyzer.process(dst);
        // !! IF_DEBUG
        var dstTypechecked = gson.toJson(dst);
        Global.logger.trace("--- dst (type checked) ---");
        Global.logger.trace(dstTypechecked);
        // !! END_IF
    }

    private static void initLogger() throws IOException {
        var props = System.getProperties();
        var logLevel = props.getProperty("logLevel");
        var logTarget = props.getProperty("logTarget");
        var logFile = props.getProperty("logFile");
        var logger = new LoggerBuilder();
        if (logFile != null) {
            logger.withFile(logFile);
        }
        if (logLevel == null) {
            logger.withLevel(LogLevel.NONE);
        } else {
            logger.withLevel(LogLevel.fromString(logLevel));
        }
        if (logTarget == null) {
            logger.withTarget(LogTarget.CONSOLE);
        } else {
            logger.withTarget(LogTarget.fromString(logTarget));
        }
        Global.logger = logger.build();
    }
}
