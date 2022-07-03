package ds;

import java.io.IOException;

import ds.Logger.LogLevel;
import ds.Logger.LogTarget;

public class LoggerBuilder {
    private LogLevel level = LogLevel.NONE;
    private LogTarget target = LogTarget.CONSOLE;
    private String file = null;

    public LoggerBuilder withLevel(LogLevel level) {
        this.level = level;
        return this;
    }

    public LoggerBuilder withTarget(LogTarget target) {
        this.target = target;
        return this;
    }

    public LoggerBuilder withFile(String file) {
        this.file = file;
        return this;
    }

    public Logger build() throws IOException {
        return new Logger(this.level, this.target, this.file);
    }
}