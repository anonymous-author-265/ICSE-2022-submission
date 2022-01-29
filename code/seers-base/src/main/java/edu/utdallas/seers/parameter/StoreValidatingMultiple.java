package edu.utdallas.seers.parameter;

import net.sourceforge.argparse4j.impl.action.StoreArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentAction;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StoreValidatingMultiple<T> extends StoreValidating<List<T>, T> {

    private final Predicate<T> validator;

    public StoreValidatingMultiple(ArgumentAction delegate, Predicate<T> validator, String errorMessage) {
        super(delegate, errorMessage);
        this.validator = validator;
    }

    public StoreValidatingMultiple(Predicate<T> validator, String errorMessage) {
        this(new StoreArgumentAction(), validator, errorMessage);
    }

    @Override
    protected List<T> findInvalidElements(List<T> value) {
        return value.stream()
                .filter(validator.negate())
                .collect(Collectors.toList());
    }
}
