package edu.utdallas.seers.json;

public class BasicAdapterSupplier extends AdapterSupplier {
    @Override
    protected Builder build(Builder builder) {
        return builder.addPathAdapter();
    }
}
