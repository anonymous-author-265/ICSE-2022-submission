package edu.utdallas.seers.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * This needs to be a class (as opposed to e.g. a function pointer in the serializable class)
 * because otherwise we would need an instance of the class to get the adapters before deserializing.
 */
public abstract class AdapterSupplier {

    private final Map<Class<?>, TypeAdapter<?>> typeAdapters;
    private final Map<Class<?>, TypeAdapter<?>> hierarchyAdapters;

    protected AdapterSupplier() {
        var builder = build(new Builder());
        typeAdapters = builder.getTypeAdapters();
        hierarchyAdapters = builder.getHierarchyAdapters();
    }

    /**
     * This method should register all needed adapters in a builder.
     *
     * @param builder Builder to use.
     * @return Builder.
     */
    protected abstract Builder build(Builder builder);

    public Map<Class<?>, TypeAdapter<?>> getTypeAdapters() {
        return typeAdapters;
    }

    public Map<Class<?>, TypeAdapter<?>> getTypeHierarchyAdapters() {
        return hierarchyAdapters;
    }

    public static class Builder {
        private final Map<Class<?>, TypeAdapter<?>> typeAdapters = new HashMap<>();
        private final Map<Class<?>, TypeAdapter<?>> hierarchyAdapters = new HashMap<>();

        public <T> Builder addTypeAdapter(Class<T> type, Function<String, T> reader) {
            typeAdapters.put(type, new StringAdapter<>(Object::toString, reader));
            return this;
        }

        public Builder addPathAdapter() {
            addTypeAdapter(Path.class, Paths::get);
            return this;
        }

        public <T> Builder addHierarchyAdapter(Class<T> type, Function<String, T> reader) {
            hierarchyAdapters.put(type, new StringAdapter<>(Object::toString, reader));
            return this;
        }

        private Map<Class<?>, TypeAdapter<?>> getHierarchyAdapters() {
            return hierarchyAdapters;
        }

        private Map<Class<?>, TypeAdapter<?>> getTypeAdapters() {
            return typeAdapters;
        }
    }

    static class StringAdapter<T> extends TypeAdapter<T> {
        private final Function<T, String> serializer;
        private final Function<String, T> deserializer;

        public StringAdapter(Function<T, String> serializer, Function<String, T> deserializer) {
            this.serializer = serializer;
            this.deserializer = deserializer;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            out.value(serializer.apply(value));
        }

        @Override
        public T read(JsonReader in) throws IOException {
            return deserializer.apply(in.nextString());
        }
    }

}
