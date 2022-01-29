package edu.utdallas.seers.lasso.data.entity.variables;

import edu.utdallas.seers.lasso.identifier.Identifier;
import edu.utdallas.seers.retrieval.Retrievable;

import java.util.Objects;

public class Attribute implements Retrievable {

    protected static final String SEPARATOR = ";";
    private static final String CONSTRUCTOR_NAME = "<init>";
    private final Type type;
    private final Identifier identifier;

    /**
     * FQN of the class where the variable appears.
     */
    @Deprecated
    // TODO remove?
    private final String name;

    private Attribute(Type type, Identifier identifier) {
        this.type = type;
        this.identifier = identifier;
        this.name = identifier.toString();
    }

    @Deprecated
    public Attribute(Type type, String name) {
        this.type = type;
        this.name = name;
        this.identifier = null;
    }

    public static Attribute newField(Identifier qualifier, String name) {
        return new Attribute(Type.FIELD, qualifier.addField(name));
    }

    public static Attribute newField(String qualifier, String name) {
        return new Attribute(Type.FIELD, qualifier + "#" + name);
    }

    public static Attribute newCtorLocal(String qualifiedName, String arg) {
        return newLocal(qualifiedName, CONSTRUCTOR_NAME, arg);
    }

    public static Attribute newLocal(Identifier qualifier, String methodName, String variableName) {
        return new Attribute(Type.LOCAL_VARIABLE, qualifier.addVariable(methodName, variableName));
    }

    public static Attribute newLocal(String qualifier, String methodName, String variableName) {
        return new Attribute(Type.LOCAL_VARIABLE, qualifier + "#" + methodName + "#" + variableName);
    }

    public static Attribute newMethod(Identifier qualifier, String name) {
        return new Attribute(Type.METHOD, qualifier.addMethod(name));
    }

    public static Attribute newMethod(String qualifier, String name) {
        return new Attribute(Type.METHOD, qualifier + "#" + name);
    }

    public static Attribute newCtor(String className) {
        return new Attribute(Type.METHOD, className + "#" + CONSTRUCTOR_NAME);
    }

    public static Attribute newMethodParameter(Identifier qualifier, String methodName, String variableName) {
        return new Attribute(Type.METHOD_PARAMETER, qualifier.addMethodParameter(methodName, variableName));
    }

    public static Attribute newType(Identifier identifier) {
        return new Attribute(Type.TYPE, identifier);
    }

    public static Attribute fromString(String string) {
        String[] split = string.split(SEPARATOR, 2);
        Type type = Type.valueOf(split[0]);
        String name = split[1];

        return new Attribute(type, name);
    }

    public static Attribute parse(String string) {
        String[] split = string.split("#");
        if (split.length == 2) {
            return Attribute.newField(split[0], split[1]);
        } else if (split.length == 3) {
            return Attribute.newLocal(split[0], split[1], split[2]);
        }

        throw new IllegalArgumentException("Invalid value for attribute: " + string);
    }

    @Override
    public String toString() {
        return type + SEPARATOR + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attribute)) return false;
        Attribute attribute = (Attribute) o;
        return type == attribute.type &&
                name.equals(attribute.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String getID() {
        return null;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public enum Type {
        TYPE,
        FIELD,
        LOCAL_VARIABLE,
        METHOD_PARAMETER,
        METHOD
    }
}
