package codechicken.mixin;

import codechicken.asm.ASMHelper;
import codechicken.mixin.api.MixinBackend;
import codechicken.mixin.api.MixinCompiler;
import codechicken.mixin.api.MixinDebugger;
import codechicken.mixin.api.MixinLanguageSupport;
import codechicken.mixin.util.*;
import com.google.common.collect.Lists;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Supplier;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.*;

/**
 * Generates composite classes similar to the scala compiler with traits.
 * <p>
 * Created by covers1624 on 2/11/20.
 */
public class MixinCompilerImpl implements MixinCompiler {

    public static final Level LOG_LEVEL = Level.valueOf(System.getProperty("codechicken.mixin.log_level", "DEBUG"));
    private static final Logger LOGGER = LoggerFactory.getLogger(MixinCompilerImpl.class);

    private final MixinBackend mixinBackend;
    private final MixinDebugger debugger;
    private final List<MixinLanguageSupport> languageSupportList;
    private final Map<String, MixinLanguageSupport> languageSupportMap;

    private final Map<String, byte[]> classBytesCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ClassInfo> infoCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, MixinInfo> mixinMap = Collections.synchronizedMap(new HashMap<>());
    private final MixinClassLoader classLoader;

    public MixinCompilerImpl() {
        this(new MixinBackend.SimpleMixinBackend());
    }

    public MixinCompilerImpl(MixinBackend mixinBackend) {
        this(mixinBackend, new MixinDebugger.NullDebugger());
    }

    public MixinCompilerImpl(MixinBackend mixinBackend, MixinDebugger debugger) {
        this(mixinBackend, debugger, () -> new SimpleServiceLoader<>(MixinLanguageSupport.class).poll().getNewServices());
    }

    public MixinCompilerImpl(MixinBackend mixinBackend, MixinDebugger debugger, Supplier<Collection<Class<? extends MixinLanguageSupport>>> supportSupplier) {
        this.mixinBackend = mixinBackend;
        this.debugger = debugger;
        LOGGER.atLevel(LOG_LEVEL).log("Starting CodeChicken MixinCompiler.");
        LOGGER.atLevel(LOG_LEVEL).log("Loading MixinLanguageSupport services..");
        long start = System.nanoTime();
        List<LanguageSupportInstance> languageSupportInstances = FastStream.of(supportSupplier.get())
                .map(LanguageSupportInstance::new)
                .sorted(Comparator.comparingInt(e -> e.sortIndex))
                .toList();
        languageSupportList = FastStream.of(languageSupportInstances)
                .map(e -> e.instance)
                .toList();
        Map<String, LanguageSupportInstance> languageSupportInstanceMap = new HashMap<>();
        for (LanguageSupportInstance instance : languageSupportInstances) {
            LanguageSupportInstance other = languageSupportInstanceMap.get(instance.name);
            if (other != null) {
                throw new RuntimeException(String.format("Duplicate MixinLanguageSupport. '%s' name conflicts with '%s'", instance, other));
            }
            languageSupportInstanceMap.put(instance.name, instance);
        }
        languageSupportMap = FastStream.of(languageSupportInstanceMap.entrySet())
                .toMap(Map.Entry::getKey, e -> e.getValue().instance);
        long end = System.nanoTime();
        LOGGER.atLevel(LOG_LEVEL).log("Loaded {} MixinLanguageSupport instances in {}.", languageSupportList.size(), Utils.timeString(start, end));

        classLoader = new MixinClassLoader(mixinBackend);
    }

    @Override
    public MixinBackend getMixinBackend() {
        return mixinBackend;
    }

    @Override
    public <T extends MixinLanguageSupport> @Nullable T getLanguageSupport(String name) {
        return SneakyUtils.unsafeCast(languageSupportMap.get(name));
    }

    @Override
    public @Nullable ClassInfo getClassInfo(String name) {
        // Do not compress this down to a computeIfAbsent, obtainInfo can modify the infoCache map.
        ClassInfo info = infoCache.get(name);
        if (info == null) {
            info = obtainInfo(name);
            infoCache.put(name, info);
        }
        return info;
    }

    @Override
    public @Nullable ClassInfo getClassInfo(ClassNode cNode) {
        ClassInfo info = infoCache.get(cNode.name);
        if (info == null) {
            info = obtainInfo(cNode);
            infoCache.put(cNode.name, info);
        }
        return info;
    }

    @Override
    public MixinInfo getMixinInfo(String name) {
        return mixinMap.get(name);
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> Class<T> compileMixinClass(String name, String superClass, Set<String> traits) {
        ClassInfo baseInfo = getClassInfo(superClass);
        if (baseInfo == null) throw new IllegalArgumentException("Provided super class does not exist.");
        if (traits.isEmpty()) {
            try {
                return (Class<T>) Class.forName(baseInfo.getName().replace('/', '.'), true, mixinBackend.getContextClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException("Base class can't be loaded??", ex);
            }
        }

        long start = System.nanoTime();
        List<MixinInfo> baseTraits = FastStream.of(traits)
                .map(mixinMap::get)
                .toList();
        List<MixinInfo> mixinInfos = FastStream.of(baseTraits)
                .flatMap(MixinInfo::linearize)
                .distinct()
                .toList();
        List<ClassInfo> traitInfos = FastStream.of(mixinInfos)
                .map(MixinInfo::name)
                .map(this::getClassInfo)
                .toList();
        ClassNode cNode = new ClassNode();

        cNode.visit(V1_8, ACC_PUBLIC, name, null, superClass, FastStream.of(baseTraits).map(MixinInfo::name).toArray(new String[0]));

        MethodInfo cInit = FastStream.of(baseInfo.getMethods())
                .filter(e -> e.getName().equals("<init>"))
                .first();
        MethodNode mInit = (MethodNode) cNode.visitMethod(ACC_PUBLIC, "<init>", cInit.getDesc(), null, null);
        Utils.writeBridge(mInit, cInit.getDesc(), INVOKESPECIAL, superClass, "<init>", cInit.getDesc(), false);
        mInit.instructions.remove(mInit.instructions.getLast());//remove the RETURN from writeBridge

        List<MixinInfo> prevInfos = new ArrayList<>();

        for (MixinInfo t : mixinInfos) {
            mInit.visitVarInsn(ALOAD, 0);
            mInit.visitMethodInsn(INVOKESTATIC, t.name(), "$init$", "(L" + t.name() + ";)V", true);

            for (FieldMixin f : t.fields()) {
                FieldNode fv = (FieldNode) cNode.visitField(ACC_PRIVATE, f.getAccessName(t.name()), f.desc(), null, null);

                Type fType = Type.getType(fv.desc);
                MethodVisitor mv;
                mv = cNode.visitMethod(ACC_PUBLIC, fv.name, "()" + f.desc(), null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, name, fv.name, fv.desc);
                mv.visitInsn(fType.getOpcode(IRETURN));
                mv.visitMaxs(-1, -1);

                mv = cNode.visitMethod(ACC_PUBLIC, fv.name + "_$eq", "(" + f.desc() + ")V", null, null);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(fType.getOpcode(ILOAD), 1);
                mv.visitFieldInsn(PUTFIELD, name, fv.name, fv.desc);
                mv.visitInsn(RETURN);
                mv.visitMaxs(-1, -1);
            }

            for (String s : t.supers()) {
                int nIdx = s.indexOf('(');
                String sName = s.substring(0, nIdx);
                String sDesc = s.substring(nIdx);
                MethodNode mv = (MethodNode) cNode.visitMethod(ACC_PUBLIC, t.name().replace("/", "$") + "$$super$" + sName, sDesc, null, null);

                MixinInfo prev = FastStream.of(prevInfos)
                        .reversed()
                        .filter(e -> ColUtils.anyMatch(e.methods(), m -> m.name.equals(sName) && m.desc.equals(sDesc)))
                        .firstOrDefault();
                // each super goes to the one before
                if (prev != null) {
                    Utils.writeStaticBridge(mv, sName, prev);
                } else {
                    MethodInfo mInfo = Objects.requireNonNull(baseInfo.findPublicImpl(sName, sDesc));
                    Utils.writeBridge(mv, sDesc, INVOKESPECIAL, mInfo.getOwner().getName(), sName, sDesc, mInfo.getOwner().isInterface());
                }

            }
            prevInfos.add(t);
        }
        mInit.visitInsn(RETURN);

        Set<String> methodSigs = new HashSet<>();
        for (MixinInfo t : Lists.reverse(mixinInfos)) {//last trait gets first pick on methods
            for (MethodNode m : t.methods()) {
                if (methodSigs.add(m.name + m.desc)) {
                    MethodNode mv = (MethodNode) cNode.visitMethod(ACC_PUBLIC, m.name, m.desc, null, m.exceptions.toArray(new String[0]));
                    Utils.writeStaticBridge(mv, m.name, t);
                }
            }
        }

        // generate synthetic bridge methods for covariant return types
        Set<ClassInfo> allParentInfos = FastStream.of(baseInfo)
                .concat(traitInfos)
                .flatMap(Utils::allParents)
                .toSet();
        List<MethodInfo> allParentMethods = FastStream.of(allParentInfos)
                .flatMap(ClassInfo::getMethods)
                .toList();

        for (String nameDesc : new HashSet<>(methodSigs)) {
            int nIdx = nameDesc.indexOf('(');
            String sName = nameDesc.substring(0, nIdx);
            String sDesc = nameDesc.substring(nIdx);
            String pDesc = sDesc.substring(0, sDesc.lastIndexOf(")") + 1);
            for (MethodInfo m : allParentMethods) {
                if (!m.getName().equals(sName) || !m.getDesc().startsWith(pDesc)) continue;
                if (!methodSigs.add(m.getName() + m.getDesc())) continue;

                MethodNode mv = (MethodNode) cNode.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC | ACC_BRIDGE, m.getName(), m.getDesc(), null, m.getExceptions());
                Utils.writeBridge(mv, mv.desc, INVOKEVIRTUAL, cNode.name, sName, sDesc, (cNode.access & ACC_INTERFACE) != 0);
            }
        }

        byte[] bytes = ASMHelper.createBytes(cNode, COMPUTE_FRAMES | COMPUTE_MAXS);
        long end = System.nanoTime();
        LOGGER.atLevel(LOG_LEVEL).log("Generation of {} with [{}] took {}", superClass, String.join(", ", traits), Utils.timeString(start, end));
        return defineClass(name, bytes);
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> Class<T> defineClass(String name, byte[] bytes) {
        debugger.defineClass(name, bytes);
        return (Class<T>) classLoader.defineClass(name, bytes);
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> Class<T> getDefinedClass(String name) {
        return (Class<T>) Objects.requireNonNull(classLoader.getDefinedClass(name), "Class was not defined by MixinCompiler. " + name);
    }

    @Override
    public MixinInfo registerTrait(ClassNode cNode) {
        // Cache hit.
        MixinInfo info = mixinMap.get(cNode.name);
        if (info != null) {
            return info;
        }

        for (MixinLanguageSupport languageSupport : languageSupportList) {
            info = languageSupport.buildMixinTrait(cNode);
            if (info == null) continue;

            if (!cNode.name.equals(info.name())) {
                throw new IllegalStateException("Traits must have the same name as their ClassNode. Got: " + info.name() + ", Expected: " + cNode.name);
            }
            mixinMap.put(info.name(), info);
            return info;
        }
        throw new IllegalStateException("No MixinLanguageSupport wished to handle class '" + cNode.name + "'");
    }

    private ClassInfo obtainInfo(ClassNode cNode) {
        for (MixinLanguageSupport languageSupport : languageSupportList) {
            ClassInfo info = languageSupport.obtainInfo(cNode);
            if (info != null) {
                return info;
            }
        }
        // In theory not possible, the Java plugin will always create nodes for
        throw new IllegalStateException("Java plugin did not create ClassInfo for existing node: " + cNode.name);
    }

    private @Nullable ClassInfo obtainInfo(String cName) {
        ClassNode cNode = getClassNode(cName);
        if (cNode != null) {
            return obtainInfo(cNode);
        }

        try {
            return new ReflectionClassInfo(
                    this,
                    Class.forName(cName.replace('/', '.'), true, mixinBackend.getContextClassLoader())
            );
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    @Override
    public ClassNode getClassNode(String name) {
        if (name.equals("java/lang/Object")) return null;

        byte[] bytes = classBytesCache.computeIfAbsent(name, mixinBackend::getBytes);
        if (bytes == null) return null;

        return ASMHelper.createClassNode(bytes, EXPAND_FRAMES);
    }

    private static class MixinClassLoader extends ClassLoader {

        public MixinClassLoader(MixinBackend mixinBackend) {
            super(mixinBackend.getContextClassLoader());
        }

        public Class<?> defineClass(String cName, byte[] bytes) {
            return defineClass(cName.replace('/', '.'), bytes, 0, bytes.length);
        }

        public Class<?> getDefinedClass(String cName) {
            return findLoadedClass(cName.replace('/', '.'));
        }
    }

    private class LanguageSupportInstance {

        private final Class<? extends MixinLanguageSupport> clazz;
        private final MixinLanguageSupport instance;
        private final String name;
        private final int sortIndex;

        public LanguageSupportInstance(Class<? extends MixinLanguageSupport> clazz) {
            this.clazz = clazz;
            MixinLanguageSupport.LanguageName lName = clazz.getAnnotation(MixinLanguageSupport.LanguageName.class);
            MixinLanguageSupport.SortingIndex sIndex = clazz.getAnnotation(MixinLanguageSupport.SortingIndex.class);
            if (lName == null) {
                throw new RuntimeException("MixinLanguageSupport '" + clazz.getName() + "' is not annotated with MixinLanguageSupport.LanguageName!");
            }
            name = lName.value();
            sortIndex = sIndex != null ? sIndex.value() : 1000;

            LOGGER.atLevel(LOG_LEVEL).log("Loading MixinLanguageSupport '{}', Name: '{}', Sorting Index: '{}'", clazz.getName(), name, sortIndex);
            Constructor<? extends MixinLanguageSupport> ctor = Utils.findConstructor(clazz, MixinCompiler.class);
            Object[] args;
            if (ctor != null) {
                args = new Object[] { MixinCompilerImpl.this };
            } else {
                ctor = Utils.findConstructor(clazz);
                args = new Object[0];
            }
            if (ctor == null) {
                throw new RuntimeException("Expected MixinLanguageSupport to have either no-args ctor or take a MixinCompiler instance.");
            }
            instance = Utils.newInstance(ctor, args);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", LanguageSupportInstance.class.getSimpleName() + "[", "]")
                    .add("class=" + clazz.getName())
                    .add("name='" + name + "'")
                    .add("sortIndex=" + sortIndex)
                    .toString();
        }
    }
}
