package edu.utdallas.seers.parameter;

import java.util.List;
import java.util.function.Consumer;

public class StoreMultipleSilent<T> extends StoreSilent<T, List<T>, List<T>> {

    public StoreMultipleSilent(Consumer<List<T>> setter, Class<T> baseType) {
        super(setter, baseType);
    }

    @Override
    protected List<T> transform(List<T> value) {
        return value;
    }

    @Override
    public boolean consumeArgument() {
        return true;
    }
}
