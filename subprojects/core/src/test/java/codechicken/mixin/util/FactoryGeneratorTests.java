package codechicken.mixin.util;

import codechicken.mixin.api.MixinCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Created by covers1624 on 3/2/21.
 */
public class FactoryGeneratorTests {

    @Test
    public void testVoidFactory() {
        MixinCompiler compiler = MixinCompiler.create();
        FactoryGenerator generator = new FactoryGenerator(compiler);
        VoidFactory factory = generator.generateFactory(ThingToMake.class, VoidFactory.class);
        ThingToMake thing = factory.construct();
    }

    public interface VoidFactory {

        ThingToMake construct();
    }

    @Test
    public void testObjectParam() {
        MixinCompiler compiler = MixinCompiler.create();
        FactoryGenerator generator = new FactoryGenerator(compiler);
        ObjectParamFactory factory = generator.generateFactory(ThingToMake.class, ObjectParamFactory.class);
        ThingToMake thing = factory.construct("Hello World");

        Assertions.assertEquals("Hello World", thing.str);
    }

    public interface ObjectParamFactory {

        ThingToMake construct(String str);
    }

    @Test
    public void testPrimitiveParam() {
        MixinCompiler compiler = MixinCompiler.create();
        FactoryGenerator generator = new FactoryGenerator(compiler);
        PrimitiveParamFactory factory = generator.generateFactory(ThingToMake.class, PrimitiveParamFactory.class);
        ThingToMake thing = factory.construct(69420);

        Assertions.assertEquals(69420, thing.i);
    }

    public interface PrimitiveParamFactory {

        ThingToMake construct(int i);
    }

    @Test
    public void testWidePrimitiveParam() {
        MixinCompiler compiler = MixinCompiler.create();
        FactoryGenerator generator = new FactoryGenerator(compiler);
        WidePrimitiveParamFactory factory = generator.generateFactory(ThingToMake.class, WidePrimitiveParamFactory.class);
        ThingToMake thing = factory.construct(69.420D);

        Assertions.assertEquals(69.420D, thing.d);
    }

    public interface WidePrimitiveParamFactory {

        ThingToMake construct(double d);
    }

    @Test
    public void testMixedParams() {
        MixinCompiler compiler = MixinCompiler.create();
        FactoryGenerator generator = new FactoryGenerator(compiler);
        MixedParamFactory factory = generator.generateFactory(ThingToMake.class, MixedParamFactory.class);
        ThingToMake thing = factory.construct("Hello", 69420, 69.420D, "World");

        Assertions.assertEquals("Hello", thing.str);
        Assertions.assertEquals(69420, thing.i);
        Assertions.assertEquals(69.420D, thing.d);
        Assertions.assertEquals("World", thing.str2);
    }

    public interface MixedParamFactory {

        ThingToMake construct(String str, int i, double d, String str2);
    }

    public static class ThingToMake {

        private String str;
        private int i;
        private double d;
        private String str2;

        public ThingToMake() {
        }

        public ThingToMake(String str) {
            this.str = str;
        }

        public ThingToMake(int i) {
            this.i = i;
        }

        public ThingToMake(double d) {
            this.d = d;
        }

        public ThingToMake(String str, int i, double d, String str2) {
            this.str = str;
            this.i = i;
            this.d = d;
            this.str2 = str2;
        }
    }
}
