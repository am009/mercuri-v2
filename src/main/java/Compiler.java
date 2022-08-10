import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import ast.SysyLexer;
import ast.SysyParser;
import backend.AsmModule;
import ds.CliArgs;
import ds.Global;
import ds.Logger.LogLevel;
import ds.Logger.LogTarget;
import dst.DstGenerator;
import dst.ds.CompUnit;
import ir.SemanticAnalyzer;
import ssa.FakeSSAGenerator;
import ssa.NumValueNamer;
import ssa.ds.Module;
import ssa.pass.EABIArithmeicLowing;
import ssa.pass.GVN;
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
        // var gb = new com.google.gson.GsonBuilder();
        // gb.setPrettyPrinting();
        // var gson = gb.create();
        // !! END_IF

        // !! IF_DEBUG
        // var jsonDst = gson.toJson(dst);
        // Global.logger.trace("--- dst ---");
        // Global.logger.trace(jsonDst);
        // !! END_IF
        var semAnalyzer = new SemanticAnalyzer();
        semAnalyzer.process(dst);
        // !! IF_DEBUG
        // var dstTypechecked = gson.toJson(dst);
        // Global.logger.trace("--- dst (type checked) ---");
        // Global.logger.trace(dstTypechecked);
        // !! END_IF
        var ssaIrGen = new FakeSSAGenerator();
        Module ssa = ssaIrGen.process(dst);
        // !! IF_DEBUG
        Global.logger.trace("--- ssa ---");
        Global.logger.trace(ssa.toString());

        Global.logger.trace("--- ssa - after GVN ---");
        GVN.process(ssa);
        NumValueNamer.process(ssa, true);
        Global.logger.trace(ssa.toString());

        // !! END_IF
        if (args.getOutFile() != null && args.getOutFile().endsWith(".ll")) { // 生成LLVM IR
            Files.writeString(Path.of(args.getOutFile()), ssa.toString());
            return;
        }
        // TODO pass manager
        ssa = EABIArithmeicLowing.process(ssa);
        Global.logger.trace("--- EABIArithmeicLowing ---");
        Global.logger.trace(ssa.toString());
        AsmModule asm = backend.arm.Generator.process(ssa);
        Global.logger.trace("--- asm inst selection ---");
        Global.logger.trace(asm.toString());
        // create log dir 
        // backend.lsra.LiveIntervalAnalyzer.process(asm);
        // backend.FlowViewer.process(asm);
        asm = backend.arm.LocalRegAllocator.process(asm);
        // backend.lsra.LinearScanRegisterAllocator.process(asm);
        Global.logger.trace("--- asm reg alloc ---");
        Global.logger.trace(asm.toString());
        if (args.getOutFile() != null) {
            Files.writeString(Path.of(args.getOutFile()), asm.toString());
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
