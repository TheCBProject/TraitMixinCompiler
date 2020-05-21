package codechicken.mixin.forge;

import codechicken.mixin.api.MixinBackend;
import cpw.mods.modlauncher.TransformingClassLoader;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Created by covers1624 on 2/11/20.
 */
public class ForgeMixinBackend extends MixinBackend.SimpleMixinBackend {

    private static final MethodHandle m_buildTransformedClassNodeFor;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method m = TransformingClassLoader.class.getDeclaredMethod("buildTransformedClassNodeFor", String.class, String.class);
            m.setAccessible(true);
            m_buildTransformedClassNodeFor = lookup.unreflect(m);
        } catch (Throwable e) {
            throw new RuntimeException("Unable to retrieve methods via reflection.", e);
        }
    }

    private final TransformingClassLoader classLoader;

    public ForgeMixinBackend() {
        classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
    }

    @Override
    public byte[] getBytes(String name) {
        String jName = name.replace("/", ".");
        if (jName.equals("java.lang.Object")) {
            return null;
        }
        try {
            return (byte[]) m_buildTransformedClassNodeFor.invoke(classLoader, jName, "codechicken.mixin.forge.ForgeMixinBackend");
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get bytes for class '" + name + "'.", e);
        }
    }

    @Override
    public boolean filterMethodAnnotations(String annType, String value) {
        if (FMLEnvironment.dist == null) {
            return false;
        }
        String side = "net.minecraftforge.api.distmarker.Dist." + FMLEnvironment.dist.name();
        return annType.equals("net.minecraftforge.api.distmarker.OnlyIn") && !value.equals(side);
    }
}
