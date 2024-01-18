package codechicken.mixin.forge;

/**
 * Created by covers1624 on 4/13/20.
 */
public enum TraitSide {
    COMMON,
    SERVER,
    CLIENT;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }

    public boolean isCommon() {
        return this == COMMON;
    }
}
