package codechicken.mixin;

import codechicken.mixin.api.AsmName;
import codechicken.mixin.api.JavaName;
import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.util.Utils;
import com.google.common.collect.ImmutableSet;
import net.covers1624.quack.collection.FastStream;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by covers1624 on 20/1/24.
 */
public abstract class SidedFactory<B, F, T> extends MixinFactoryImpl<B, F> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SidedFactory.class);

    protected final Map<Class<?>, TraitKey> clientTraits = new HashMap<>();
    protected final Map<Class<?>, TraitKey> serverTraits = new HashMap<>();

    protected final Map<Class<?>, ImmutableSet<TraitKey>> clientObjectTraitCache = new HashMap<>();
    protected final Map<Class<?>, ImmutableSet<TraitKey>> serverObjectTraitCache = new HashMap<>();

    protected SidedFactory(MixinCompiler mc, Class<B> baseType, Class<F> factory, String suffix) {
        super(mc, baseType, factory, suffix);
    }

    /**
     * Overload of {@link #registerTrait(Class, Class, Class)}, using the same
     * trait impl for client and server.
     *
     * @param marker The Marker class, to be found in the part instances class hierarchy.
     */
    @AsmName
    @JavaName
    public void registerTrait(Class<?> marker, Class<?> trait) {
        registerTrait(marker, trait, trait);
    }

    /**
     * Registers a trait to be applied to the host tile in the presence of a specific
     * marker class existing in the class hierarchy of a part instance.
     *
     * @param marker      The Marker class, to be found in the part instances class hierarchy.
     * @param clientTrait The trait class to be applied on the client side.
     * @param serverTrait The trait class to be applied on the server side.
     */
    @AsmName
    @JavaName
    public void registerTrait(Class<?> marker, @Nullable Class<?> clientTrait, @Nullable Class<?> serverTrait) {
        if (clientTrait != null) {
            register(clientTraits, marker, clientTrait);
        }

        if (serverTrait != null) {
            register(serverTraits, marker, serverTrait);
        }
    }

    /**
     * Gets all the {@link TraitKey}'s this generator knows about from the <code>thing</code>'s
     * class hierarchy.
     *
     * @param thing  The thing to get all traits from.
     * @param client If this is the client side or not.
     * @return The {@link TraitKey}s.
     */
    public ImmutableSet<TraitKey> getTraitsForObject(T thing, boolean client) {
        return getObjectTraitCache(client).computeIfAbsent(thing.getClass(), clazz -> {
            Map<Class<?>, TraitKey> traits = getTraitMap(client);
            return hierarchy(clazz)
                    .map(traits::get)
                    .filter(Objects::nonNull)
                    .toImmutableSet();
        });
    }

    protected FastStream<Class<?>> hierarchy(Class<?> clazz) {
        return FastStream.concat(
                FastStream.of(clazz),
                FastStream.of(clazz.getInterfaces()).flatMap(this::hierarchy),
                FastStream.ofNullable(clazz.getSuperclass()).flatMap(this::hierarchy)
        );
    }

    protected Map<Class<?>, TraitKey> getTraitMap(boolean client) {
        return client ? clientTraits : serverTraits;
    }

    protected Map<Class<?>, ImmutableSet<TraitKey>> getObjectTraitCache(boolean client) {
        return client ? clientObjectTraitCache : serverObjectTraitCache;
    }

    protected void register(Map<Class<?>, TraitKey> map, Class<?> marker, Class<?> trait) {
        String tName = Utils.asmName(trait);
        TraitKey existing = map.get(marker);
        if (existing != null) {
            if (existing.tName().equals(tName)) {
                LOGGER.error("Attempted to re-register trait for '{}' with a different impl. Ignoring. Existing: '{}', New: '{}'", marker, existing.tName(), tName);
            } else {
                LOGGER.error("Skipping re-register of trait for '{}' and impl '{}'", marker, tName);
            }
            return;
        }
        map.put(marker, registerTrait(trait));
    }
}
