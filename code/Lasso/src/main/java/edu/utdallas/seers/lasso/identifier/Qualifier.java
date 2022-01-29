package edu.utdallas.seers.lasso.identifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

class Qualifier {
    private String name;

    Qualifier(@Nullable String name) {
        this.name = name == null || name.isEmpty() ? null : name;
    }

    Qualifier addComponent(@Nonnull String component) {
        return new Qualifier(getDottedOrEmpty() + component);
    }

    String qualify(String otherName) {
        return getDottedOrEmpty() + otherName;
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Qualifier qualifier = (Qualifier) o;
        return Objects.equals(name, qualifier.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    private String getDottedOrEmpty() {
        return Optional.ofNullable(name).map(n -> n + ".").orElse("");
    }
}