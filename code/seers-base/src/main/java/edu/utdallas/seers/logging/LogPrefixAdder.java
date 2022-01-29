package edu.utdallas.seers.logging;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.pattern.ConverterKeys;
import org.apache.logging.log4j.core.pattern.LogEventPatternConverter;

/**
 * With the default configuration, adds the prefix stored in the static field to every logged message.
 */
@Plugin(name = "prefix", category = "Converter")
@ConverterKeys({"P", "prefix"})
public class LogPrefixAdder extends LogEventPatternConverter {

    private static String prefix = "";

    /**
     * Constructs an instance of LoggingEventPatternConverter.
     */
    protected LogPrefixAdder() {
        super("Prefix", "prefix");
    }

    @SuppressWarnings({"unused", "RedundantSuppression"})
    public static LogPrefixAdder newInstance(final String[] args) {
        return new LogPrefixAdder();
    }

    @Override
    public void format(LogEvent event, StringBuilder toAppendTo) {
        toAppendTo.append(prefix);
    }

    public static void setPrefix(String prefix) {
        LogPrefixAdder.prefix = prefix;
    }

    public static void clearPrefix() {
        prefix = "";
    }
}
