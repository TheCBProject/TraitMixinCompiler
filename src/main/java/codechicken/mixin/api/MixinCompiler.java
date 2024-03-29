package codechicken.mixin.api;

import codechicken.mixin.MixinCompilerImpl;
import codechicken.mixin.util.ClassInfo;
import codechicken.mixin.util.JavaTraitGenerator;
import codechicken.mixin.util.MixinInfo;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.Set;

/**
 * Defines a compiler capable of generating a composite Class, comprised of
 * a Set of Scala-like Trait classes on top of a base Class implementation.
 * <p>
 * Created by covers1624 on 2/17/20.
 */
public interface MixinCompiler {

    /**
     * Create a {@link MixinCompiler} instance.
     *
     * @return The instance.
     */
    static MixinCompiler create() {
        return new MixinCompilerImpl();
    }

    /**
     * Create a {@link MixinCompiler} instance, with the given MixinBackend.
     *
     * @param backend The MixinBackend.
     * @return The instance.
     */
    static MixinCompiler create(MixinBackend backend) {
        return new MixinCompilerImpl(backend);
    }

    /**
     * Create a {@link MixinCompiler} instance, with the
     * given {@link MixinBackend} and {@link MixinDebugger}.
     *
     * @param backend  The MixinBackend.
     * @param debugger The MixinDebugger.
     * @return The instance.
     */
    static MixinCompiler create(MixinBackend backend, MixinDebugger debugger) {
        return new MixinCompilerImpl(backend, debugger);
    }

    /**
     * Create a {@link MixinCompiler} instance, with the
     * given {@link MixinBackend}, {@link MixinDebugger}
     * and the specified Collection of {@link MixinLanguageSupport}s.
     *
     * @param backend  The MixinBackend.
     * @param debugger The MixinDebugger.
     * @param supports The MixinLanguageSupport classes to load.
     * @return The instance.
     */
    static MixinCompiler create(MixinBackend backend, MixinDebugger debugger, Collection<Class<? extends MixinLanguageSupport>> supports) {
        return new MixinCompilerImpl(backend, debugger, () -> supports);
    }

    /**
     * Gets the {@link MixinBackend} for this MixinCompiler
     *
     * @return The MixinBackend instance.
     */
    MixinBackend getMixinBackend();

    /**
     * Get a {@link MixinLanguageSupport} instance with the given name.
     *
     * @param name The name.
     * @return The {@link MixinLanguageSupport} instance or {@code null}
     */
    <T extends MixinLanguageSupport> @Nullable T getLanguageSupport(String name);

    /**
     * Gets a {@link ClassInfo} instance for the given class name.
     *
     * @param name The class name.
     * @return The ClassInfo
     */
    @Nullable
    ClassInfo getClassInfo(@AsmName String name);

    /**
     * Overload for {@link #getClassInfo(String)}, taking a {@link ClassNode} instead.
     *
     * @param node The ClassNode.
     * @return The ClassInfo.
     */
    @Nullable
    ClassInfo getClassInfo(ClassNode node);

    /**
     * Overload for {@link #getClassInfo(String)}, taking a {@link Class} instead.
     *
     * @param clazz The Class.
     * @return The ClassInfo.
     */
    @Nullable
    default ClassInfo getClassInfo(@Nullable Class<?> clazz) {
        return clazz == null ? null : getClassInfo(clazz.getName().replace(".", "/"));
    }

    /**
     * Loads a {@link ClassNode} for the given class name.
     *
     * @param name The Class name.
     * @return The ClassNode.
     */
    @Nullable
    ClassNode getClassNode(@AsmName String name);

    /**
     * Registers a Trait to the {@link MixinCompiler}.
     *
     * @param cNode The ClassNode for the trait.
     * @return The MixinInfo for the trait.
     */
    MixinInfo registerTrait(ClassNode cNode);

    /**
     * Gets a {@link MixinInfo} for the given Class name.
     *
     * @param name The Class name
     * @return The MixinInfo, Null if it does not exist.
     */
    @Nullable
    MixinInfo getMixinInfo(@AsmName String name);

    /**
     * Defines a class.
     *
     * @param name  The name for the class.
     * @param bytes The bytes for the class.
     * @return The defined class.
     */
    <T> Class<T> defineClass(@AsmName String name, byte[] bytes);

    /**
     * Get a previously defined class.
     *
     * @param name The name of the previously defined class.
     * @return The defined class.
     * @throws NullPointerException If the class was not found.
     */
    <T> Class<T> getDefinedClass(@AsmName @JavaName String name);

    /**
     * Compiles a new class with the given name, super Class, and traits.
     *
     * @param name       The name for the class.
     * @param superClass The name for the super class.s
     * @param traits     The Traits to mixin.
     * @return The compiled class.
     */
    <T> Class<T> compileMixinClass(String name, String superClass, Set<String> traits);

}
