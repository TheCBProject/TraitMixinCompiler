package codechicken.mixin.api;

import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.reflect.PrivateLookups;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Provides an abstracted backend for the {@link MixinCompiler} system.
 * This allows different environments, to change the low level
 * integration {@link MixinCompiler} requires with the running environment.
 * I.e, Running under MinecraftForge requires _some_ tweaks.
 * <p>
 * Created by covers1624 on 2/11/20.
 */
public interface MixinBackend {

    /**
     * Gets the bytes for a class.
     *
     * @param name The class name.
     * @return The bytes for the class.
     */
    byte @Nullable [] getBytes(@AsmName String name);

    /**
     * Defines a class.
     *
     * @param name  The name for the class.
     * @param bytes The bytes for the class.
     * @return The defined class.
     */
    <T> Class<T> defineClass(String name, byte[] bytes);

    /**
     * Loads a class.
     *
     * @param name The class name.
     * @return The loaded class.
     */
    @Nullable Class<?> loadClass(String name);

    /**
     * Allows a MixinBackend to filter a method based on the annotation value for 'value'.
     * Used exclusively for {@link codechicken.mixin.scala.MixinScalaLanguageSupport}
     * with {@link codechicken.mixin.forge.ForgeMixinBackend} to strip methods from
     * {@link codechicken.mixin.scala.ScalaSignature} in a Forge environment.
     *
     * @param annType The annotation type.
     * @param value   The annotation value for 'value'
     * @return To remove or not.
     */
    default boolean filterMethodAnnotations(String annType, String value) {
        return true;
    }

    /**
     * A simple {@link MixinBackend} implementation for standalone use.
     */
    class SimpleMixinBackend implements MixinBackend {

        protected static final MethodHandle m_defineClass;

        static {
            try {
                m_defineClass = PrivateLookups.getTrustedLookup()
                        .findVirtual(ClassLoader.class, "defineClass", MethodType.methodType(Class.class, byte[].class, int.class, int.class));
            } catch (Throwable e) {
                throw new RuntimeException("Unable to retrieve methods via reflection.", e);
            }
        }

        private final ClassLoader classLoader;

        public SimpleMixinBackend() {
            this(SimpleMixinBackend.class.getClassLoader());
        }

        public SimpleMixinBackend(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public byte @Nullable [] getBytes(String name) {
            try (InputStream is = classLoader.getResourceAsStream(name + ".class")) {
                if (is == null) {
                    return null;
                }
                return IOUtils.toBytes(is);
            } catch (IOException e) {
                SneakyUtils.throwUnchecked(new ClassNotFoundException("Could not load bytes for '" + name + "'.", e));
                return null;
            }
        }

        @Override
        @SuppressWarnings ("unchecked")
        public <T> Class<T> defineClass(String name, byte[] bytes) {
            try {
                return (Class<T>) m_defineClass.invokeExact(classLoader, bytes, 0, bytes.length);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to define class '" + name + "'.", e);
            }
        }

        @Override
        public Class<?> loadClass(String name) {
            try {
                return classLoader.loadClass(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
    }
}
