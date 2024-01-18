package codechicken.mixin.forge;

import codechicken.mixin.MixinFactoryImpl;
import codechicken.mixin.api.AsmName;
import codechicken.mixin.api.JavaName;
import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.util.Utils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.*;
import java.util.stream.Stream;

import static codechicken.mixin.util.Utils.asmName;

/**
 * Created by covers1624 on 4/14/20.
 */
@SuppressWarnings ("UnstableApiUsage")//We are careful
public class SidedGenerator<B, F, T> extends MixinFactoryImpl<B, F> {

    private static final Logger logger = LogManager.getLogger();

    protected final Map<String, TraitKey> clientTraits = new HashMap<>();
    protected final Map<String, TraitKey> serverTraits = new HashMap<>();

    protected final Map<Class<?>, ImmutableSet<TraitKey>> clientObjectTraitCache = new HashMap<>();
    protected final Map<Class<?>, ImmutableSet<TraitKey>> serverObjectTraitCache = new HashMap<>();

    protected SidedGenerator(MixinCompiler mixinCompiler, Class<B> baseType, Class<F> factoryClass, String classSuffix) {
        super(mixinCompiler, baseType, factoryClass, classSuffix);
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
    public void registerTrait(String marker, String clientTrait, String serverTrait) {
        marker = asmName(marker);

        if (clientTrait != null) {
            registerSide(clientTraits, marker, asmName(clientTrait));
        }

        if (serverTrait != null) {
            registerSide(serverTraits, marker, asmName(serverTrait));
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
                    .collect(ImmutableSet.toImmutableSet());
        });
    }

    protected void loadAnnotations(Class<? extends Annotation> aClass, Class<? extends Annotation> aListClass) {
        Type aType = Type.getType(aClass);
        Type lType = Type.getType(aListClass);
        ModList.get().getAllScanData().stream()
                .map(ModFileScanData::getAnnotations)
                .flatMap(Collection::stream)
                .filter(a -> a.annotationType().equals(aType) || a.annotationType().equals(lType))
                .filter(a -> a.targetType() == ElementType.TYPE)
                .map(a -> {
                    if (a.annotationType().equals(lType)) {
                        @SuppressWarnings ("unchecked")
                        List<Map<String, Object>> entries = ((List<Map<String, Object>>) a.annotationData().get("value"));
                        return Pair.of(a, entries);
                    }
                    return Pair.of(a, Collections.singletonList(a.annotationData()));
                })
                .forEach(p -> {
                    ModFileScanData.AnnotationData a = p.getLeft();
                    List<Map<String, Object>> dataList = p.getRight();
                    String tName = a.clazz().getInternalName();
                    logger.info("Trait: {}", tName);
                    for (Map<String, Object> data : dataList) {
                        Type marker = (Type) data.get("value");
                        ModAnnotation.EnumHolder holder = (ModAnnotation.EnumHolder) data.get("side");
                        TraitSide side = holder != null ? TraitSide.valueOf(holder.getValue()) : TraitSide.COMMON;
                        logger.info("    Marker: {}, Side: {}", marker.getInternalName(), side);
                        if (side.isCommon() || side.isClient() && FMLEnvironment.dist == Dist.CLIENT) {
                            registerSide(clientTraits, marker.getInternalName(), tName);
                        }
                        if (side.isCommon() || side.isServer()) {
                            registerSide(serverTraits, marker.getInternalName(), tName);
                        }
                    }
                });
    }

    protected Stream<Class<?>> hierarchy(Class<?> clazz) {
        return Streams.concat(
                Stream.of(clazz),
                Arrays.stream(clazz.getInterfaces()).flatMap(this::hierarchy),
                Streams.stream(Optional.ofNullable(clazz.getSuperclass())).flatMap(this::hierarchy)
        );
    }

    protected Map<String, TraitKey> getTraitMap(boolean client) {
        return client ? clientTraits : serverTraits;
    }

    protected Map<Class<?>, ImmutableSet<TraitKey>> getObjectTraitCache(boolean client) {
        return client ? clientObjectTraitCache : serverObjectTraitCache;
    }

    protected void registerSide(Map<String, TraitKey> map, String marker, String trait) {
        TraitKey existing = map.get(marker);
        TraitKey newTrait = registerTrait(trait);
        if (existing != null) {
            if (!existing.equals(newTrait)) {
                logger.error("Attempted to re-register trait for '{}', with a different impl, Ignoring. Existing: '{}', New: '{}'", marker, existing.getTName(), newTrait.getTName());
                return;
            } else if (existing.equals(newTrait)) {
                logger.debug("Skipping re-register of trait for '{}', same impl detected.", marker);
                return;
            }
        }
        map.put(marker, newTrait);
    }
}
