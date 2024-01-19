package codechicken.mixin.api;

import codechicken.asm.ASMHelper;
import codechicken.mixin.util.ClassInfo;
import codechicken.mixin.util.ClassNodeInfo;
import codechicken.mixin.util.JavaTraitGenerator;
import codechicken.mixin.util.MixinInfo;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ServiceLoader;
import java.util.function.BiFunction;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

/**
 * Defines abstract logic for loading Mixins for different languages.
 * These are loaded using a {@link ServiceLoader} from the classpath.
 * <p>
 * Created by covers1624 on 2/16/20.
 */
public interface MixinLanguageSupport {

    /**
     * Tries to load a {@link ClassInfo} for the given {@link ClassNode}.
     * Custom implementations shouldn't be greedy, only load a
     * {@link ClassInfo} if you know for certain that you need to.
     * I.e: {@link codechicken.mixin.scala.MixinScalaLanguageSupport}, only loads
     * a {@link codechicken.mixin.scala.ScalaClassInfo} if the class has a
     * ScalaSignature Annotation, and is a Scala trait class.
     *
     * @param cNode The ClassNode.
     * @return The ClassInfo.
     */
    @Nullable
    ClassInfo obtainInfo(ClassNode cNode);

    /**
     * Tries to build a {@link MixinInfo} for the given {@link ClassNode}.
     * as with {@link #obtainInfo}, only load MixinInfos if you
     * know for certain that you need to. I.e: {@link codechicken.mixin.scala.MixinScalaLanguageSupport},
     * will only load a MixinInfo for the class, if {@link MixinCompiler#getClassInfo(ClassNode)}
     * resolves to a {@link codechicken.mixin.scala.ScalaClassInfo}.
     *
     * @param cNode The ClassNode.
     * @return The MixinInfo.
     */
    @Nullable
    MixinInfo buildMixinTrait(ClassNode cNode);

    /**
     * The name for the MixinLanguageSupport, Required.
     * Must be unique.
     */
    @Target (ElementType.TYPE)
    @Retention (RetentionPolicy.RUNTIME)
    @interface LanguageName {

        String value();
    }

    /**
     * A simple way to sort this {@link MixinLanguageSupport} before others in the list.
     * A smaller number will be earlier in the list. E.g: [-3000, -100, 0, 100, 3000]
     * If this annotation is not provided, it will default to an index of 1000.
     */
    @Target (ElementType.TYPE)
    @Retention (RetentionPolicy.RUNTIME)
    @interface SortingIndex {

        int value();
    }

    /**
     * The default java handling for MixinCompiler.
     */
    @LanguageName ("java")
    @SortingIndex (Integer.MAX_VALUE) // Always last
    class JavaMixinLanguageSupport implements MixinLanguageSupport {

        protected final MixinCompiler mixinCompiler;
        private BiFunction<MixinCompiler, ClassNode, JavaTraitGenerator> traitGeneratorFactory = JavaTraitGenerator::new;

        public JavaMixinLanguageSupport(MixinCompiler mixinCompiler) {
            this.mixinCompiler = mixinCompiler;
        }

        public void setTraitGeneratorFactory(BiFunction<MixinCompiler, ClassNode, JavaTraitGenerator> factory) {
            traitGeneratorFactory = factory;
        }

        @Override
        public ClassInfo obtainInfo(ClassNode cNode) {
            return new ClassNodeInfo(mixinCompiler, cNode);
        }

        @Override
        public MixinInfo buildMixinTrait(ClassNode cNode) {
            JavaTraitGenerator generator = traitGeneratorFactory.apply(mixinCompiler, cNode);
            ClassNode sNode = generator.getStaticNode();
            if (sNode != null) {
                mixinCompiler.defineClass(sNode.name, ASMHelper.createBytes(sNode, COMPUTE_FRAMES | COMPUTE_MAXS));
            }
            ClassNode tNode = generator.getTraitNode();
            MixinInfo info = generator.getMixinInfo();
            mixinCompiler.defineClass(tNode.name, ASMHelper.createBytes(tNode, COMPUTE_FRAMES | COMPUTE_MAXS));
            return info;
        }
    }

}
