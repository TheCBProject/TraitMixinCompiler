package codechicken.mixin.util;

import codechicken.mixin.api.MixinCompiler;
import net.covers1624.quack.collection.FastStream;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Created by covers1624 on 2/11/20.
 */
public class ClassNodeInfo extends ClassInfo {

    private final ClassNode cNode;
    protected List<ClassInfo> interfaces;
    private final List<MethodInfo> methods;

    public ClassNodeInfo(MixinCompiler mixinCompiler, ClassNode cNode) {
        super(mixinCompiler);
        this.cNode = cNode;
        interfaces = FastStream.of(cNode.interfaces)
                .map(mixinCompiler::getClassInfo)
                .toList();
        methods = FastStream.of(cNode.methods)
                .map(MethodNodeInfo::new)
                .toList(FastStream.infer());
    }

    //@formatter:off
    @Override public String getName() { return cNode.name; }
    @Override public boolean isInterface() { return (cNode.access & ACC_INTERFACE) != 0; }
    @Override public ClassInfo getSuperClass() { return cNode.superName != null ? mixinCompiler.getClassInfo(cNode.superName) : null; }
    @Override public Iterable<ClassInfo> getInterfaces() { return interfaces; }
    @Override public Iterable<MethodInfo> getMethods() { return methods; }
    public ClassNode getCNode() { return cNode; }
    //@formatter:on

    public class MethodNodeInfo implements MethodInfo {

        private final MethodNode mNode;
        private final String[] exceptions;

        public MethodNodeInfo(MethodNode mNode) {
            this.mNode = mNode;
            exceptions = mNode.exceptions.toArray(new String[0]);
        }

        //@formatter:off
        @Override public ClassInfo getOwner() { return ClassNodeInfo.this; }
        @Override public String getName() { return mNode.name; }
        @Override public String getDesc() { return mNode.desc; }
        @Override public String[] getExceptions() { return exceptions; }
        @Override public boolean isPrivate() { return (mNode.access & ACC_PRIVATE) != 0; }
        @Override public boolean isAbstract() { return (mNode.access & ACC_ABSTRACT) != 0; }
        //@formatter:on
    }
}
