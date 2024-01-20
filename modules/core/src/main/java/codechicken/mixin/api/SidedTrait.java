package codechicken.mixin.api;

import codechicken.mixin.SidedFactory;

import java.lang.annotation.*;

/**
 * Specifies that for a given marker ({@link #value}) the currently annotated class
 * should be applied.
 * <p>
 * Created by covers1624 on 20/1/24.
 */
@Target (ElementType.TYPE)
@Retention (RetentionPolicy.RUNTIME)
@Repeatable (SidedTrait.TraitList.class)
public @interface SidedTrait {

    /**
     * The marker for the trait.
     */
    Class<?> value();

    /**
     * The side for the trait.
     */
    SidedFactory.Side side() default SidedFactory.Side.COMMON;

    @Target (ElementType.TYPE)
    @Retention (RetentionPolicy.RUNTIME)
    @interface TraitList {

        SidedTrait[] value();
    }
}
