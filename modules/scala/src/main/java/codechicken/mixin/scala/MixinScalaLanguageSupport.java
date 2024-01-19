package codechicken.mixin.scala;

import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.MixinLanguageSupport;
import codechicken.mixin.scala.ScalaSignature.ClassSymbolRef;
import codechicken.mixin.util.ClassInfo;
import codechicken.mixin.util.FieldMixin;
import codechicken.mixin.util.MixinInfo;
import net.covers1624.quack.collection.FastStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static codechicken.mixin.api.MixinLanguageSupport.LanguageName;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

/**
 * Created by covers1624 on 19/1/24.
 */
@LanguageName ("scala")
public class MixinScalaLanguageSupport implements MixinLanguageSupport {

    private static final Logger LOGGER = LogManager.getLogger();

    private final MixinCompiler mixinCompiler;

    public MixinScalaLanguageSupport(MixinCompiler mixinCompiler) {
        this.mixinCompiler = mixinCompiler;
    }

    @Override
    public ClassInfo obtainInfo(ClassNode cNode) {
        if (cNode.name.endsWith("$")) {
            String baseName = cNode.name.substring(0, cNode.name.length() - 1);
            ClassNode baseNode = mixinCompiler.getClassNode(baseName);
            if (baseNode != null) {
                ScalaClassInfo info = scalaInfo(baseNode, true);
                if (info != null) {
                    return info;
                }
            }
        }

        return scalaInfo(cNode, false);
    }

    private @Nullable ScalaClassInfo scalaInfo(ClassNode cNode, boolean obj) {
        ScalaSignature sig = ScalaSignature.parse(cNode);
        if (sig == null) return null;

        String name = cNode.name.replace('/', '.');
        ClassSymbolRef cSym = obj ? sig.findObject(name) : sig.findClass(name);
        if (cSym == null) return null;

        return new ScalaClassInfo(mixinCompiler, cNode, sig, cSym);
    }

    @Override
    public MixinInfo buildMixinTrait(ClassNode cNode) {
        if (!(mixinCompiler.getClassInfo(cNode) instanceof ScalaClassInfo info) || !info.isTrait()) return null;

        ScalaSignature sig = info.sig;
        Set<String> filtered = listFiltered(sig);

        List<MixinInfo> parentTraits = getAndRegisterParentTraits(cNode);
        List<FieldMixin> fields = new ArrayList<>();
        List<MethodNode> methods = new ArrayList<>();
        List<String> supers = new ArrayList<>();

        ClassSymbolRef cSym = info.cSym;
        for (ScalaSignature.MethodSymbol sym : sig.<ScalaSignature.MethodSymbol>collect(8)) {
            LOGGER.info(sym);
            if (sym.isParam() || !sym.owner().equals(cSym)) continue;
            if (filtered.contains(sym.full())) continue;

            if (sym.isAccessor()) {
                if (!sym.name().trim().endsWith("$eq")) {
                    fields.add(new FieldMixin(sym.name().trim(), Type.getReturnType(sym.jDesc()).getDescriptor(), sym.isPrivate() ? ACC_PRIVATE : ACC_PUBLIC)); // TODO better
                }
            } else if (sym.isMethod()) {
                String desc = sym.jDesc();
                if (sym.name().startsWith("super$")) {
                    supers.add(sym.name().substring(6) + desc);
                } else if (!sym.isPrivate() && !sym.isDeferred() && !sym.name().equals("$init$")) {
                    // also check if the return type is a scala object
                    String objectDesc = Type.getMethodDescriptor(
                            Type.getObjectType(Type.getReturnType(desc).getInternalName() + "$"),
                            Type.getArgumentTypes(desc)
                    );
                    MethodNode mNode = FastStream.of(cNode.methods)
                            .filter(e -> e.name.equals(sym.name()) && (e.desc.equals(desc) || e.desc.equals(objectDesc)))
                            .firstOrDefault();
                    if (mNode == null) {
                        throw new IllegalArgumentException("Unable to add mixin trait " + cNode.name + ": " + sym.name() + desc + " found in scala signature but not in class file. Most likely an obfuscation issue.");
                    }
                    methods.add(mNode);
                }
            }
        }

        return new MixinInfo(cNode.name, cSym.jParent(), parentTraits, fields, methods, supers);
    }

    private List<MixinInfo> getAndRegisterParentTraits(ClassNode cNode) {
        return FastStream.of(cNode.interfaces)
                .map(mixinCompiler::getClassInfo)
                .filter(e -> e instanceof ScalaClassInfo info && info.isTrait() && !info.cSym.isInterface())
                .map(e -> mixinCompiler.registerTrait(((ScalaClassInfo) e).getCNode()))
                .toList();
    }

    private Set<String> listFiltered(ScalaSignature sig) {
        return FastStream.of(sig.<ScalaSignature.AnnotationInfo>collect(40))
                .filter(e -> {
                    ScalaSignature.Literal value = e.getValue("value");
                    return value instanceof ScalaSignature.EnumLiteral lit
                           && mixinCompiler.getMixinBackend().filterMethodAnnotations(e.annType().name(), lit.value().full());
                })
                .map(e -> e.owner().full())
                .toSet();
    }

}
