package codechicken.mixin.api;

import com.google.common.collect.ImmutableSet;
import org.objectweb.asm.tree.ClassNode;

/**
 * Represents a 'user' facing interface for interacting and caching the {@link MixinCompiler}.
 * <p>
 * Created by covers1624 on 2/17/20.
 */
public interface MixinFactory<B, F> {

    /**
     * Gets the associated MixinCompiler for this MixinFactory.
     *
     * @return The MixinCompiler.
     */
    MixinCompiler getMixinCompiler();

    /**
     * Registers a trait.
     *
     * @param tClass The trait class.
     * @return a TraitKey for using this registered trait.
     */
    TraitKey registerTrait(@AsmName Class<?> tClass);

    /**
     * Registers a binary trait. Used for traits which
     * are generated at runtime.
     *
     * @param cNode The {@link ClassNode} for the trait.
     * @return a TraitKey for using this registered trait.
     */
    TraitKey registerTrait(@AsmName ClassNode cNode);

    /**
     * Returns a factory ({@link F}) capable of constructing a new {@link B} with the given set of traits applied.
     * <p>
     * The MixinFactory will cache constructed classes with the given set of traits,
     * subsequent calls with the same traits will not cause a new class to be generated.
     * <p>
     * It should be noted that, {@link ImmutableSet} is explicitly used here,
     * as their hashCode is statically computed, making it favourable for use as a
     * key in a Map.
     *
     * @param traits The traits to apply.
     * @return The Factory.
     */
    F construct(ImmutableSet<TraitKey> traits);

    /**
     * Gets the traits that were used in compiling the given class.
     * If the given class was not compiled by this factory, simply returns null.
     *
     * @param clazz The Class to get the traits for.
     * @return The classes traits, or null if it was not compiled by this factory.
     */
    ImmutableSet<TraitKey> getTraitsForClass(Class<?> clazz);

    interface TraitKey {

        /**
         * Gets the class name for this trait.
         *
         * @return The class name.
         */
        @AsmName
        String getTName();
    }

}
