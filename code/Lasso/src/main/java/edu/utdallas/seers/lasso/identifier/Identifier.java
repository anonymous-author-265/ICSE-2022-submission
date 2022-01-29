package edu.utdallas.seers.lasso.identifier;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Identifier {
    private final List<Component> components;

    public Identifier(@Nullable String packageName) {
        String realName = Optional.ofNullable(packageName).orElse("");
        components = Arrays.stream(realName.split("\\."))
                .map(s -> new Component(s, ComponentType.PACKAGE))
                .collect(Collectors.toUnmodifiableList());
    }

    public Identifier(List<Component> components, Component... newComponents) {
        this.components = Stream.concat(
                components.stream(),
                Arrays.stream(newComponents)
        )
                .collect(Collectors.toUnmodifiableList());
    }

    public Identifier addType(String name) {
        return addComponent(name, ComponentType.TYPE);
    }

    public Identifier addField(String name) {
        return addComponent(name, ComponentType.FIELD);
    }

    public Identifier addMethod(String name) {
        return addComponent(name, ComponentType.METHOD);
    }

    protected Identifier addComponent(String name, ComponentType type) {
        return new Identifier(components, new Component(name, type));
    }

    public Identifier addMethodParameter(String methodName, String variableName) {
        return new Identifier(components,
                new Component(methodName, ComponentType.METHOD),
                new Component(variableName, ComponentType.PARAMETER)
        );
    }

    public Identifier addVariable(String methodName, String variableName) {
        return new Identifier(components,
                new Component(methodName, ComponentType.METHOD),
                new Component(variableName, ComponentType.VARIABLE)
        );
    }

    public boolean packageStartsWith(List<String> strings) {
        if (strings.size() > components.size()) {
            return false;
        }

        for (int i = 0; i < strings.size(); i++) {
            var component = components.get(i);
            String currentName = strings.get(i);

            if (component.type != ComponentType.PACKAGE || !currentName.equals(component.name)) {
                return false;
            }
        }

        return true;
    }

    public Stream<String> stream() {
        return components.stream()
                .map(c -> c.name);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(components.get(0).name);

        components.stream()
                .skip(1)
                .forEachOrdered(c -> {
                    String prefix;
                    if (c.type == ComponentType.TYPE || c.type == ComponentType.PACKAGE) {
                        prefix = ".";
                    } else {
                        prefix = "#";
                    }

                    builder.append(prefix).append(c.name);
                });

        return builder.toString();
    }

    public List<String> getPackageComponents() {
        return components.stream()
                .filter(c -> c.type == ComponentType.PACKAGE)
                .map(c -> c.name)
                .collect(Collectors.toList());
    }

    enum ComponentType {
        TYPE, PACKAGE, METHOD, VARIABLE, PARAMETER, FIELD
    }

    private static class Component {
        private final String name;
        private final ComponentType type;

        public Component(String name, ComponentType type) {
            this.name = name;
            this.type = type;
        }
    }
}
