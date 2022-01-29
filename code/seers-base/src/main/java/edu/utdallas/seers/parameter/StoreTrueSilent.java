package edu.utdallas.seers.parameter;

import net.sourceforge.argparse4j.inf.Argument;

import java.util.function.Consumer;

/**
 * Action that silently sets a property through a provided setter to true if it is found,
 * and false otherwise.
 */
class StoreTrueSilent extends StoreSilent<Void, Void, Boolean> {

    private final Consumer<Boolean> setter;

    public StoreTrueSilent(Consumer<Boolean> setter) {
        super(setter, Void.class);
        this.setter = setter;
    }

    @Override
    protected Boolean transform(Void value) {
        return true;
    }

    @Override
    public void onAttach(Argument arg) {
        super.onAttach(arg);
        setter.accept(false);
    }

    @Override
    public boolean consumeArgument() {
        return false;
    }
}
