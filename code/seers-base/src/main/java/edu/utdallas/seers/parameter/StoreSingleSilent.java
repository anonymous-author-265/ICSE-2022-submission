package edu.utdallas.seers.parameter;

import java.util.function.Consumer;

public abstract class StoreSingleSilent<S, T> extends StoreSilent<S, S, T> {
    public StoreSingleSilent(Consumer<T> setter, Class<S> baseType) {
        super(setter, baseType);
    }

    @Override
    public boolean consumeArgument() {
        return true;
    }
}
