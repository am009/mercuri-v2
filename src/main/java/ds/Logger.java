package ds;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class Logger implements AutoCloseable {
    /**
     * Log level
     */
    public enum LogLevel {
        NONE,
        ERROR,
        WARNING,
        INFO,
        DEBUG,
        TRACE;

        public static LogLevel fromString(String level) {
            switch (level.toLowerCase()) {
                case "none":
                    return LogLevel.NONE;
                case "error":
                    return LogLevel.ERROR;
                case "warning":
                    return LogLevel.WARNING;
                case "info":
                    return LogLevel.INFO;
                case "debug":
                    return LogLevel.DEBUG;
                case "trace":
                    return LogLevel.TRACE;
                default:
                    throw new IllegalArgumentException("Unknown log level: " + level);
            }
        }
    }

    public enum LogTarget {
        CONSOLE,
        FILE;

        public static LogTarget fromString(String target) {
            switch (target) {
                case "console":
                    return LogTarget.CONSOLE;
                case "file":
                    return LogTarget.FILE;
                default:
                    throw new IllegalArgumentException("Unknown log target: " + target);
            }
        }
    }

    private final LogLevel level;
    private final LogTarget target;
    private final String file;
    private final FileWriter writer;

    public Logger(LogLevel level, LogTarget target, String file) throws IOException {
        this.level = level;
        this.target = target;
        this.file = file;
        this.writer = file != null ? new FileWriter(file) : null;
    }

    @Override
    public void close() throws RuntimeException {
        if (this.writer != null) {
            try {
                this.writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public LogLevel getLevel() {
        return this.level;
    }

    public LogTarget getTarget() {
        return this.target;
    }

    public String getFile() {
        return this.file;
    }
    // Logging methods

    public void log(LogLevel level, String message) {
        if (this.level.compareTo(level) < 0) {
            return;
        }
        message = defaultFormatterFunc(level, message);
        switch (this.target) {
            case CONSOLE:
                System.out.print(message);
                break;
            case FILE:
                try {
                    this.writer.write(message);
                    this.writer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown log target: " + this.target);
        }
    }

    private final Map<LogLevel, String> colorMap = Map.of(
            LogLevel.ERROR, "31",
            LogLevel.WARNING, "33",
            LogLevel.INFO, "32",
            LogLevel.DEBUG, "34",
            LogLevel.TRACE, "36");

    public String defaultFormatterFunc(LogLevel level, String message) {
        switch (target) {
            case CONSOLE:
                var sb = new StringBuilder();
                switch (level) {
                    case ERROR:
                        sb.append("\u001b[").append(colorMap.get(level)).append("m");
                        sb.append("[ERROR] ");
                        sb.append("\u001b[0m");
                        break;
                    case WARNING:
                        sb.append("\u001b[").append(colorMap.get(level)).append("m");
                        sb.append("[WARNING] ");
                        sb.append("\u001b[0m");
                        break;
                    case INFO:
                        sb.append("\u001b[").append(colorMap.get(level)).append("m");
                        sb.append("[INFO] ");
                        sb.append("\u001b[0m");
                        break;
                    case DEBUG:
                        sb.append("\u001b[").append(colorMap.get(level)).append("m");
                        sb.append("[DEBUG] ");
                        sb.append("\u001b[0m");
                        break;
                    case TRACE:
                        sb.append("\u001b[").append(colorMap.get(level)).append("m");
                        sb.append("[TRACE] ");
                        sb.append("\u001b[0m");
                        break;
                    default:
                        break;
                }
                sb.append(message);
                sb.append("\n");
                return sb.toString();
            case FILE:
                return "[" + level.name() + "] " + message + "\n";
            default:
                throw new IllegalArgumentException("Unknown log target: " + this.target);
        }
    }

    // Logging methods with formatting
    // 如果仅有格式化字符串版本，则当打印的字符串里面包含了意外的格式化符号时String.format会出错。
    public void log(LogLevel level, String format, Object... args) {
        this.log(level, String.format(format, args));
    }

    public void info(String format, Object... args) {
        this.log(LogLevel.INFO, format, args);
    }

    public void info(String format) {
        this.log(LogLevel.INFO, format);
    }

    public void debug(String format, Object... args) {
        this.log(LogLevel.DEBUG, format, args);
    }

    public void debug(String format) {
        this.log(LogLevel.DEBUG, format);
    }

    public void trace(String format, Object... args) {
        this.log(LogLevel.TRACE, format, args);
    }

    public void trace(String format) {
        this.log(LogLevel.TRACE, format);
    }

    public void error(String format, Object... args) {
        this.log(LogLevel.ERROR, format, args);
    }

    public void error(String format) {
        this.log(LogLevel.ERROR, format);
    }

    public void warning(String format, Object... args) {
        this.log(LogLevel.WARNING, format, args);
    }

    public void warning(String format) {
        this.log(LogLevel.WARNING, format);
    }
}