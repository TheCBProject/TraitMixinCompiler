package codechicken.mixin;

import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.MixinFactory;
import codechicken.mixin.util.ClassInfo;
import codechicken.mixin.util.FactoryGenerator;
import codechicken.mixin.util.Utils;
import com.google.common.collect.ImmutableSet;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.util.SneakyUtils;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Created by covers1624 on 2/17/20.
 */
public class MixinFactoryImpl<B, F> implements MixinFactory<B, F> {

    protected final AtomicInteger counter = new AtomicInteger();
    protected final List<BiConsumer<Class<? extends B>, ImmutableSet<TraitKey>>> compileCallbacks = new ArrayList<>();
    protected final Map<ImmutableSet<TraitKey>, Class<? extends B>> classCache = new HashMap<>();
    protected final Map<ImmutableSet<TraitKey>, F> factoryCache = new HashMap<>();
    protected final Map<Class<?>, ImmutableSet<TraitKey>> traitLookup = new HashMap<>();
    protected final Map<String, TraitKey> registeredTraits = new HashMap<>();

    protected final MixinCompiler mixinCompiler;
    protected final Class<B> baseType;
    protected final Class<F> factoryClass;
    protected final String classSuffix;

    protected final FactoryGenerator factoryGenerator;

    public MixinFactoryImpl(MixinCompiler mixinCompiler, Class<B> baseType, Class<F> factoryClass, String classSuffix) {
        this.mixinCompiler = mixinCompiler;
        this.baseType = baseType;
        this.factoryClass = factoryClass;
        this.classSuffix = classSuffix;

        factoryGenerator = new FactoryGenerator(mixinCompiler);
        //Validate factory.
        factoryGenerator.findMethod(factoryClass);
    }

    @Override
    public MixinCompiler getMixinCompiler() {
        return mixinCompiler;
    }

    @Override
    public synchronized TraitKey registerTrait(String tName) {
        TraitKey trait = registeredTraits.get(tName);
        if (trait != null) return trait;

        ClassNode cNode = mixinCompiler.getClassNode(tName);
        if (cNode == null) {
            SneakyUtils.throwUnchecked(new ClassNotFoundException(tName));
            return null;
        }
        return registerTrait(cNode);
    }

    @Override
    public synchronized TraitKey registerTrait(ClassNode cNode) {
        String tName = cNode.name;
        TraitKey key = registeredTraits.get(tName);
        if (key != null) {
            return key;
        }
        ClassInfo info = mixinCompiler.getClassInfo(cNode);

        String parentName = info.concreteParent().getName();
        ClassInfo baseInfo = mixinCompiler.getClassInfo(baseType);
        if (!checkParent(parentName, baseInfo)) {
            throw new IllegalArgumentException("Trait '" + tName + "' with resolved parent '" + parentName + "' does not extend base type '" + Utils.asmName(baseType) + "'");
        }
        mixinCompiler.registerTrait(cNode);
        key = new TraitKeyImpl(tName);
        registeredTraits.put(tName, key);
        return key;
    }

    @Override
    public F construct(ImmutableSet<TraitKey> traits) {
        return factoryCache.computeIfAbsent(traits, this::compile);
    }

    @Override
    public ImmutableSet<TraitKey> getTraitsForClass(Class<?> clazz) {
        return traitLookup.get(clazz);
    }

    private boolean checkParent(String parentName, ClassInfo info) {
        if (info.getName().equals(parentName)) return true;

        ClassInfo sClass = info.getSuperClass();
        if (sClass == null) return false;

        return checkParent(parentName, sClass);
    }

    private synchronized F compile(ImmutableSet<TraitKey> traits) {
        Class<? extends B> clazz = classCache.computeIfAbsent(traits, e -> {
            Set<String> traitNames = FastStream.of(traits).map(TraitKey::getTName).toImmutableSet();
            Class<? extends B> compiled = mixinCompiler.compileMixinClass(nextName(), Utils.asmName(baseType), traitNames);
            traitLookup.put(compiled, traits);
            return compiled;
        });
        return factoryGenerator.generateFactory(clazz, factoryClass);
    }

    private String nextName() {
        return baseType.getSimpleName() + "_" + classSuffix + "$$" + counter.getAndIncrement();
    }

    private static class TraitKeyImpl implements TraitKey {

        private final String tName;

        private TraitKeyImpl(String tName) {
            this.tName = tName;
        }

        @Override
        public String getTName() {
            return tName;
        }

        @Override
        public boolean equals(Object obj) {
            if (super.equals(obj)) {
                return true;
            }
            if (!(obj instanceof TraitKeyImpl)) {
                return false;
            }
            TraitKeyImpl other = (TraitKeyImpl) obj;
            return Objects.equals(getTName(), other.getTName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(tName);
        }
    }
}
