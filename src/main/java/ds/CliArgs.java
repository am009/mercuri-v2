package ds;

public class CliArgs {
    /**
     * The name of the source file to read.
     * POSITIONAL
     */
    private String inFile;
    /**
     * The name of the output file to write.
     * -o <output file>
     */
    private String outFile;
    /**
     * The name of the file to write the statistics to.
     * -S
     */
    private OutType outType;
    /**
     * Optimize level
     * -On
     */
    private int optLv;

    @Override
    public String toString() {
        return "{" +
                " inFile='" + getInFile() + "'" +
                ", outFile='" + getOutFile() + "'" +
                ", outType='" + getOutType() + "'" +
                "}";
    }

    public String getInFile() {
        return this.inFile;
    }

    public String getOutFile() {
        return this.outFile;
    }

    public OutType getOutType() {
        return this.outType;
    }

    public boolean isOpt() {
        return optLv != 0;
    }

    public enum OutType {
        ASM,
    }

    public static CliArgs parse(String[] args) {
        CliArgs cliArgs = new CliArgs();
        var n = args.length;
        for (int i = 0; i < n; i++) {
            String arg = args[i];
            if (arg.equals("-o")) {
                if (i + 1 < n) {
                    cliArgs.outFile = args[i + 1];
                    i++;
                } else {
                    throw new IllegalArgumentException("-o requires an argument");
                }
            } else if (arg.equals("-S")) {
                cliArgs.outType = OutType.ASM;
            } else if (arg.startsWith("-O")) {
                cliArgs.optLv = Integer.valueOf(arg.substring(2));
            } else {
                cliArgs.inFile = arg;
            }
        }
        return cliArgs;
    }
}
