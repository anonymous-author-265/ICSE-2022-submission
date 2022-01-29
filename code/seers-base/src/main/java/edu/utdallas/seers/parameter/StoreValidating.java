package edu.utdallas.seers.parameter;

import net.sourceforge.argparse4j.impl.action.StoreArgumentAction;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class StoreValidating<S, T> implements ArgumentAction {
    private final ArgumentAction delegate;
    private final String errorMessage;

    public StoreValidating(String errorMessage) {
        this(new StoreArgumentAction(), errorMessage);
    }

    public StoreValidating(ArgumentAction delegate, String errorMessage) {
        this.delegate = delegate;
        this.errorMessage = errorMessage;
    }

    protected abstract List<T> findInvalidElements(S value);

    protected String reportError(List<T> invalidElements) {
        String sep;
        if (errorMessage.trim().endsWith(".")) {
            sep = " ";
        } else {
            sep = ". ";
        }
        return this.errorMessage + sep + "Value(s): " +
                invalidElements.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(", "));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value)
            throws ArgumentParserException {
        var invalidElements = findInvalidElements((S) value);
        if (!invalidElements.isEmpty()) {
            throw new ArgumentParserException(reportError(invalidElements), parser);
        }
        delegate.run(parser, arg, attrs, flag, value);
    }

    @Override
    public void onAttach(Argument arg) {
        delegate.onAttach(arg);
    }

    @Override
    public boolean consumeArgument() {
        return delegate.consumeArgument();
    }
}
