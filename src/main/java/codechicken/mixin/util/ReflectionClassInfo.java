package codechicken.mixin.util;

import codechicken.mixin.api.MixinCompiler;
import net.covers1624.quack.collection.FastStream;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Created by covers1624 on 2/11/20.
 */
public class ReflectionClassInfo extends ClassInfo {

    private final Class<?> clazz;
    private final String name;
    private final List<ClassInfo> interfaces;
    private final List<MethodInfo> methods;

    public ReflectionClassInfo(MixinCompiler mixinCompiler, Class<?> clazz) {
        super(mixinCompiler);
        this.clazz = clazz;
        name = Utils.asmName(clazz.getName());
        interfaces = FastStream.of(clazz.getInterfaces())
                .map(mixinCompiler::getClassInfo)
                .toList();
        methods = FastStream.of(clazz.getMethods())
                .map(ReflectionMethodInfo::new)
                .toList(FastStream.infer());
    }

    //@formatter:off
    @Override public String getName() { return name; }
    @Override public boolean isInterface() { return clazz.isInterface(); }
    @Override public ClassInfo getSuperClass() { return mixinCompiler.getClassInfo(clazz.getSuperclass()); }
    @Override public Iterable<ClassInfo> getInterfaces() { return interfaces; }
    @Override public Iterable<MethodInfo> getMethods() { return methods; }
    //@formatter:on

    public class ReflectionMethodInfo implements MethodInfo {

        private final String name;
        private final String desc;
        private final String[] exceptions;
        private final boolean isPrivate;
        private final boolean isAbstract;

        private ReflectionMethodInfo(Method method) {
            name = method.getName();
            desc = Type.getType(method).getDescriptor();
            exceptions = FastStream.of(method.getExceptionTypes())
                    .map(Class::getName)
                    .map(Utils::asmName)
                    .toArray(new String[0]);
            isPrivate = Modifier.isPrivate(method.getModifiers());
            isAbstract = Modifier.isAbstract(method.getModifiers());
        }

        //@formatter:off
        @Override public ClassInfo getOwner() { return ReflectionClassInfo.this; }
        @Override public String getName() { return name; }
        @Override public String getDesc() { return desc; }
        @Override public String[] getExceptions() { return exceptions; }
        @Override public boolean isPrivate() { return isPrivate; }
        @Override public boolean isAbstract() { return isAbstract; }
        //@formatter:on
    }

}
