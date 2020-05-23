package codechicken.mixin

import codechicken.mixin.api.MixinCompiler
import org.junit.jupiter.api.Test

/**
 * Test case for Scala object classes.
 *
 * Created by covers1624 on 4/13/20.
 */
class ScalaObjectTests {

    @Test
    def doStuff() {
        val compiler = MixinCompiler.create()
        val factory = new MixinFactoryImpl[Microblock](compiler, classOf[Microblock], "cmb", classOf[Int])
        factory.registerTrait("codechicken/mixin/CornerMicroblock")
    }
}


abstract class Microblock {
    def microFactory: MicroblockFactory
}

class MicroblockFactory {
}

object CornerMicroFactory extends MicroblockFactory {
}

trait CornerMicroblock extends Microblock {
    //Letting scalac pick the type for this method was problematic for the mixin compiler.
    // The pickle format ends up with the descriptor '()Lcodechicken/mixin/CornerMicroFactory;' whilst
    // the method bytecode ends up with the descriptor '()Lcodechicken/mixin/CornerMicroFactory$;' because
    // both are correct, as the former refers to a scala object and the latter refers to the implementation
    // of said object class, the fix here was to make the mixin compiler aware of this when matching signature
    // methods to bytecode methods, it will now check if the return type is a scala object and handle accordingly
    override def microFactory = CornerMicroFactory
}
