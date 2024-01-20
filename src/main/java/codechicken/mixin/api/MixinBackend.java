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
     * The class loader to delegate class loading calls to.
     *
     * @return The context class loader.
     */
    ClassLoader getContextClassLoader();

    /**
     * Gets the bytes for a class.
     *
     * @param name The class name.
     * @return The bytes for the class.
     */
    byte @Nullable [] getBytes(@AsmName String name);

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

        private final ClassLoader classLoader;

        public SimpleMixinBackend() {
            this(SimpleMixinBackend.class.getClassLoader());
        }

        public SimpleMixinBackend(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader getContextClassLoader() {
            return classLoader;
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
    }
}
