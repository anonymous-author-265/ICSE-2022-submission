package edu.utdallas.seers.parameter;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentAction;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.FeatureControl;

import java.util.Map;
import java.util.function.Consumer;

/**
 * @param <B> Base type of the argument which is passed to {@link Argument}.
 * @param <S> Type returned by the library. Will be the same as B unless nargs > 1 is used, in which
 *            case it will be {@code List<B>}
 * @param <T> Type of the setter that will store the final value.
 */
public abstract class StoreSilent<B, S, T> implements ArgumentAction {
    protected final Class<B> baseType;
    protected final Consumer<T> setter;

    public StoreSilent(Consumer<T> setter, Class<B> baseType) {
        this.baseType = baseType;
        this.setter = setter;
    }

    protected abstract T transform(S value);

    @SuppressWarnings("unchecked")
    @Override
    public void run(ArgumentParser parser, Argument arg, Map<String, Object> attrs, String flag, Object value) {
        setter.accept(transform((S) value));
    }

    @Override
    public void onAttach(Argument arg) {
        arg
                .setDefault(FeatureControl.SUPPRESS)
                .type(baseType);
    }
}
