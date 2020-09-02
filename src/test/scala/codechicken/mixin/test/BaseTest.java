package codechicken.mixin.test;

import codechicken.mixin.MixinFactoryImpl;
import codechicken.mixin.api.MixinBackend;
import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.MixinFactory;
import codechicken.mixin.classes.MixinBase;
import codechicken.mixin.util.SimpleDebugger;

import java.nio.file.Paths;

/**
 * Created by covers1624 on 3/9/20.
 */
public abstract class BaseTest {

    public static <T> MixinFactory<T> setup(Class<T> clazz, String name) {
        System.setProperty("codechicken.mixin.log_level", "INFO");
        MixinCompiler compiler = MixinCompiler.create(new MixinBackend.SimpleMixinBackend(), new SimpleDebugger(Paths.get("dumps", name), SimpleDebugger.DumpType.BINARY));
        return new MixinFactoryImpl<>(compiler, clazz, name);
    }

}
