package edu.utdallas.seers.lasso.retrieval;

import java.util.Objects;

public class BaselineConfig implements RetrievalConfiguration {
    public final BaselineIndexBuilder.Type type;
    public final BaselineIndexBuilder.Input input;
    public final BaselineIndexBuilder.Output output;
    public final int dimension;

    public BaselineConfig(BaselineIndexBuilder.Type type, BaselineIndexBuilder.Input input,
                          BaselineIndexBuilder.Output output, int dimension) {
        this.type = type;
        this.input = input;
        this.output = output;
        this.dimension = dimension;
    }

    @Override
    public String toString() {
        return type.prettyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaselineConfig that = (BaselineConfig) o;
        return dimension == that.dimension && type == that.type && input == that.input && output == that.output;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, input, output, dimension);
    }
}
