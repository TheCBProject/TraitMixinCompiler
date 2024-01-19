package codechicken.mixin.scala;

import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.util.ClassInfo;
import codechicken.mixin.util.ClassNodeInfo;
import net.covers1624.quack.collection.FastStream;
import org.objectweb.asm.tree.ClassNode;

/**
 * Created by covers1624 on 19/1/24.
 */
public class ScalaClassInfo extends ClassNodeInfo {

    public final ScalaSignature sig;
    public final ScalaSignature.ClassSymbolRef cSym;

    public ScalaClassInfo(MixinCompiler mixinCompiler, ClassNode cNode, ScalaSignature sig, ScalaSignature.ClassSymbolRef cSym) {
        super(mixinCompiler, cNode);
        this.sig = sig;
        this.cSym = cSym;
        interfaces = FastStream.of(cSym.jInterfaces()).map(mixinCompiler::getClassInfo).toList();
    }

    @Override
    public ClassInfo concreteParent() {
        ClassInfo info = getSuperClass();
        if (info instanceof ScalaClassInfo sInfo && sInfo.isTrait()) return sInfo.concreteParent();

        return info;
    }

    @Override
    public boolean isInterface() {
        return cSym.isTrait() || cSym.isInterface();
    }

    @Override
    public ClassInfo getSuperClass() {
        return mixinCompiler.getClassInfo(cSym.jParent());
    }

    public boolean isTrait() {
        return cSym.isTrait();
    }

    public boolean isObject() {
        return cSym.isObject();
    }
}
