package codechicken.mixin;

import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.MixinFactory;
import codechicken.mixin.classes.Mixin1;
import codechicken.mixin.classes.Mixin2;
import codechicken.mixin.classes.MixinBase;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vague tests for the mixin compiler and JavaTraitGenerator.
 * Not for automated testing, must be run manually.
 * <p>
 * Created by covers1624 on 2/12/20.
 */
public class MixinCompilerTest {

    private static MixinCompiler compiler;
    private static MixinFactory<MixinBase> factory;

    @BeforeAll
    public static void setup() {
        System.setProperty("codechicken.mixin.log_level", "INFO");
        compiler = MixinCompiler.create();
        factory = new MixinFactoryImpl<>(compiler, MixinBase.class, "tests");
    }

    @Test
    public void testJavaTrait() {
        MixinFactory.TraitKey key = factory.registerTrait("codechicken/mixin/classes/Mixin1");
        MixinBase constructed = factory.construct(ImmutableSet.of(key));

        assertTrue(constructed instanceof Mixin1, "MixinBase does not implement Mixin1");
        constructed.setStuff("Hello");
        assertEquals("Hello, World!", constructed.append(", World!"));
    }

    @Test
    public void testScalaTrait() {
        MixinFactory.TraitKey key = factory.registerTrait("codechicken/mixin/classes/Mixin2");
        MixinBase constructed = factory.construct(ImmutableSet.of(key));

        assertTrue(constructed instanceof Mixin2, "MixinBase does not implement Mixin1");
        constructed.setStuff("Hello");
        assertEquals("Hello, World!", constructed.append(", World!"));
    }
}
