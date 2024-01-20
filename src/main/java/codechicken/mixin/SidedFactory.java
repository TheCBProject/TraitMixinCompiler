package codechicken.mixin;

import codechicken.mixin.api.AsmName;
import codechicken.mixin.api.JavaName;
import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.SidedTrait;
import codechicken.mixin.util.SimpleServiceLoader;
import codechicken.mixin.util.Utils;
import com.google.common.collect.ImmutableSet;
import net.covers1624.quack.asm.annotation.AnnotationLoader;
import net.covers1624.quack.collection.FastStream;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Repeatable;
import java.util.*;

import static codechicken.mixin.util.Utils.asmName;

/**
 * Created by covers1624 on 20/1/24.
 */
public abstract class SidedFactory<B, F, T> extends MixinFactoryImpl<B, F> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SidedFactory.class);

    protected final Map<String, TraitKey> clientTraits = new HashMap<>();
    protected final Map<String, TraitKey> serverTraits = new HashMap<>();

    protected final Map<Class<?>, ImmutableSet<TraitKey>> clientObjectTraitCache = new HashMap<>();
    protected final Map<Class<?>, ImmutableSet<TraitKey>> serverObjectTraitCache = new HashMap<>();

    protected SidedFactory(MixinCompiler mc, Class<B> baseType, Class<F> factory, String suffix) {
        super(mc, baseType, factory, suffix);
    }

    /**
     * Overload of {@link #registerTrait(String, String, String)}, using the same
     * trait impl for client and server.
     *
     * @param marker The Marker class, to be found in the part instances class hierarchy.
     */
    @AsmName
    @JavaName
    public void registerTrait(String marker, String trait) {
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
    public void registerTrait(String marker, @Nullable String clientTrait, @Nullable String serverTrait) {
        marker = asmName(marker);

        if (clientTrait != null) {
            register(clientTraits, marker, asmName(clientTrait));
        }

        if (serverTrait != null) {
            register(serverTraits, marker, asmName(serverTrait));
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
            Map<String, TraitKey> traits = getTraitMap(client);
            return hierarchy(clazz)
                    .map(Utils::asmName)
                    .map(traits::get)
                    .filter(Objects::nonNull)
                    .toImmutableSet();
        });
    }

    /**
     * @return The current runtime side. {@link Side#CLIENT} OR {@link Side#SERVER}.
     */
    protected abstract Side getRuntimeSide();

    protected void loadServices(Class<? extends TraitMarker> markerService) {
        SimpleServiceLoader.load(markerService, tName -> {
            tName = tName.replace('.', '/');
            LOGGER.info("Trait: {}", tName);
            ClassNode info = mixinCompiler.getClassNode(tName);
            if (info == null) {
                LOGGER.error("Failed to load ClassNode for trait {}", tName);
                return;
            }
            fudgeAnnotations(info.visibleAnnotations);
            fudgeAnnotations(info.invisibleAnnotations);
            AnnotationLoader anns = new AnnotationLoader();
            info.accept(anns.forClass());
            SidedTraitLoadable[] annotations = anns.getAnnotations(SidedTraitLoadable.class);
            if (annotations == null) {
                LOGGER.error("Expected SidedTrait annotation to exist.");
                return;
            }
            for (SidedTraitLoadable ann : annotations) {
                String marker = ann.value();
                Side side = ann.side();
                LOGGER.info("    Marker: {}, Side: {}", marker, side);
                if (side.isCommon() || side.isClient() && getRuntimeSide().isClient()) {
                    register(clientTraits, marker, tName);
                }
                if (side.isCommon() || side.isServer()) {
                    register(serverTraits, marker, tName);
                }
            }
        });
    }

    protected FastStream<Class<?>> hierarchy(Class<?> clazz) {
        return FastStream.concat(
                FastStream.of(clazz),
                FastStream.of(clazz.getInterfaces()).flatMap(this::hierarchy),
                FastStream.ofNullable(clazz.getSuperclass()).flatMap(this::hierarchy)
        );
    }

    protected Map<String, TraitKey> getTraitMap(boolean client) {
        return client ? clientTraits : serverTraits;
    }

    protected Map<Class<?>, ImmutableSet<TraitKey>> getObjectTraitCache(boolean client) {
        return client ? clientObjectTraitCache : serverObjectTraitCache;
    }

    protected void register(Map<String, TraitKey> map, String marker, String trait) {
        TraitKey existing = map.get(marker);
        if (existing != null) {
            if (existing.getTName().equals(trait)) {
                LOGGER.error("Attempted to re-register trait for '{}' with a different impl. Ignoring. Existing: '{}', New: '{}'", marker, existing.getTName(), trait);
            } else {
                LOGGER.error("Skipping re-register of trait for '{}' and impl '{}'", marker, trait);
            }
            return;
        }
        map.put(marker, registerTrait(trait));
    }

    public enum Side {
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

    public interface TraitMarker {
    }

    private static final Type SIDED_TRAIT_TYPE = Type.getType(SidedTrait.class);
    private static final Type SIDED_TRAIT_LIST_TYPE = Type.getType(SidedTrait.TraitList.class);
    private static final Type SIDED_TRAIT_LOADABLE_TYPE = Type.getType(SidedTraitLoadable.class);
    private static final Type SIDED_TRAIT_LOADABLE_LIST_TYPE = Type.getType(SidedTraitLoadable.TraitList.class);

    private static void fudgeAnnotations(@Nullable List<AnnotationNode> annotations) {
        if (annotations == null) return;

        annotations.forEach(SidedFactory::fudgeAnnotation);
    }

    private static void fudgeAnnotation(AnnotationNode annotation) {
        if (Type.getType(annotation.desc).equals(SIDED_TRAIT_TYPE)) {
            annotation.desc = SIDED_TRAIT_LOADABLE_TYPE.getDescriptor();
        } else if (Type.getType(annotation.desc).equals(SIDED_TRAIT_LIST_TYPE)) {
            annotation.desc = SIDED_TRAIT_LOADABLE_LIST_TYPE.getDescriptor();
        } else {
            return;
        }

        for (ListIterator<Object> iterator = annotation.values.listIterator(); iterator.hasNext(); ) {
            Object value = iterator.next();
            if (value instanceof Type type) {
                iterator.set(type.getInternalName());
            } else if (value instanceof AnnotationNode ann) {
                fudgeAnnotation(ann);
            } else if (value instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof AnnotationNode ann) {
                        fudgeAnnotation(ann);
                    }
                }
            }
        }
    }

    /**
     * Copy of {@link SidedTrait} which does not use Class
     * for {@link #value}.
     */
    @Repeatable (SidedTraitLoadable.TraitList.class)
    public @interface SidedTraitLoadable {

        String value();

        SidedFactory.Side side() default SidedFactory.Side.COMMON;

        @interface TraitList {

            SidedTraitLoadable[] value();
        }
    }
}
