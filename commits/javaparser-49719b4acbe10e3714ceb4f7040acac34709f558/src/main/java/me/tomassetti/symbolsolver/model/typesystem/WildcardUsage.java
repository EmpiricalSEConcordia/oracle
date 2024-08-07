package me.tomassetti.symbolsolver.model.typesystem;

public class WildcardUsage implements TypeUsage {

    public static WildcardUsage UNBOUNDED = new WildcardUsage(null, null);
    //private WildcardType type;
    private BoundType type;
    private TypeUsage boundedType;

    private WildcardUsage(BoundType type, TypeUsage boundedType) {
        this.type = type;
        this.boundedType = boundedType;
    }

    public static WildcardUsage superBound(TypeUsage typeUsage) {
        return new WildcardUsage(BoundType.SUPER, typeUsage);
    }

    public static WildcardUsage extendsBound(TypeUsage typeUsage) {
        return new WildcardUsage(BoundType.EXTENDS, typeUsage);
    }

    public boolean isWildcard() {
        return true;
    }

    public WildcardUsage asWildcard() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WildcardUsage)) return false;

        WildcardUsage that = (WildcardUsage) o;

        if (boundedType != null ? !boundedType.equals(that.boundedType) : that.boundedType != null) return false;
        if (type != that.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (boundedType != null ? boundedType.hashCode() : 0);
        return result;
    }

    @Override
    public String describe() {
        if (type == null) {
            return "?";
        } else if (type == BoundType.SUPER) {
            return "? super " + boundedType.describe();
        } else if (type == BoundType.EXTENDS) {
            return "? extends " + boundedType.describe();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public boolean isSuper() {
        return type == BoundType.SUPER;
    }

    public boolean isExtends() {
        return type == BoundType.EXTENDS;
    }

    public TypeUsage getBoundedType() {
        if (boundedType == null) {
            throw new IllegalStateException();
        }
        return boundedType;
    }

    @Override
    public boolean isAssignableBy(TypeUsage other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeUsage replaceParam(String name, TypeUsage replaced) {
        if (boundedType == null) {
            return this;
        }
        TypeUsage boundedTypeReplaced = boundedType.replaceParam(name, replaced);
        if (boundedTypeReplaced != boundedType) {
            return new WildcardUsage(type, boundedTypeReplaced);
        } else {
            return this;
        }
    }

    public enum BoundType {
        SUPER,
        EXTENDS
    }
}
