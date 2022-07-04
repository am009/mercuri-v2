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
        var dstGen = new DstGenerator(parser.compUnit(), args.getInFile());
        CompUnit dst = dstGen.generate();
        if (true) {
            // print dst as json
            var gb = new com.google.gson.GsonBuilder();
            gb.setPrettyPrinting();
            var gson = gb.create();
            var json = gson.toJson(dst);
            Global.logger.trace(json);
        }
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
