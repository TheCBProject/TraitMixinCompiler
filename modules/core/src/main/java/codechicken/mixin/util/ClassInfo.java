package codechicken.mixin.util;

import codechicken.mixin.api.MixinCompiler;
import net.covers1624.quack.collection.FastStream;

import javax.annotation.Nullable;

/**
 * Created by covers1624 on 2/11/20.
 */
public abstract class ClassInfo {

    protected MixinCompiler mixinCompiler;

    protected ClassInfo(MixinCompiler mixinCompiler) {
        this.mixinCompiler = mixinCompiler;
    }

    public abstract String getName();

    public abstract boolean isInterface();

    public abstract @Nullable ClassInfo getSuperClass();

    public abstract Iterable<ClassInfo> getInterfaces();

    public abstract Iterable<MethodInfo> getMethods();

    public FastStream<MethodInfo> getParentMethods() {
        return FastStream.ofNullable(getSuperClass())
                .concat(getInterfaces())
                .flatMap(ClassInfo::getAllMethods);
    }

    public FastStream<MethodInfo> getAllMethods() {
        return FastStream.concat(getMethods(), getParentMethods());
    }

    public @Nullable MethodInfo findPublicImpl(String name, String desc) {
        return getAllMethods()
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getDesc().equals(desc))
                .filter(m -> !m.isAbstract() && !m.isPrivate())
                .firstOrDefault();
    }

    public @Nullable MethodInfo findPublicParentImpl(String name, String desc) {
        return getParentMethods()
                .filter(m -> m.getName().equals(name))
                .filter(m -> m.getDesc().equals(desc))
                .filter(m -> !m.isAbstract() && !m.isPrivate())
                .firstOrDefault();
    }

    public @Nullable ClassInfo concreteParent() {
        return getSuperClass();
    }

    public boolean inheritsFrom(String parentName) {
        return FastStream.ofNullable(concreteParent()).concat(getInterfaces())
                .anyMatch(e -> e.getName().equals(parentName) || e.inheritsFrom(parentName));
    }

    public String getModuleName() {
        return getName();
    }

}
