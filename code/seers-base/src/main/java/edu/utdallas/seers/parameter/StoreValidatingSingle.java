package edu.utdallas.seers.parameter;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class StoreValidatingSingle<T> extends StoreValidating<T, T> {

    private final Predicate<T> validator;

    public StoreValidatingSingle(Predicate<T> validator, String errorMessage) {
        super(errorMessage);
        this.validator = validator;
    }

    @Override
    protected List<T> findInvalidElements(T value) {
        return validator.test(value) ? Collections.emptyList() : Collections.singletonList(value);
    }
}
