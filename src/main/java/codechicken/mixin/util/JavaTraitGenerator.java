package codechicken.mixin.util;

import codechicken.asm.*;
import codechicken.mixin.api.MixinCompiler;
import net.covers1624.quack.collection.ColUtils;
import net.covers1624.quack.collection.FastStream;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Function;

import static org.objectweb.asm.Opcodes.*;

/**
 * Handles taking a java class, and turning it into a Scala-like trait interface.
 * <p>
 * Created by covers1624 on 2/11/20.
 */
public class JavaTraitGenerator {

    protected final MixinCompiler mixinCompiler;
    protected final ClassNode cNode;
    protected final ClassNode sNode;
    protected final ClassNode tNode;

    protected List<FieldNode> staticFields;

    protected List<FieldNode> instanceFields;

    protected List<FieldMixin> traitFields;
    protected List<MethodNode> traitMethods = new ArrayList<>();
    protected Set<String> supers = new LinkedHashSet<>();
    protected MixinInfo mixinInfo;

    protected Map<String, String> fieldNameLookup;
    protected Map<String, MethodNode> methodSigLookup;

    public JavaTraitGenerator(MixinCompiler mixinCompiler, ClassNode cNode) {
        this.mixinCompiler = mixinCompiler;
        this.cNode = cNode;
        tNode = new ClassNode();
        sNode = new ClassNode();
        checkNode();
        staticFields = FastStream.of(cNode.fields)
                .filter(e -> (e.access & ACC_STATIC) != 0)
                .toList();

        instanceFields = FastStream.of(cNode.fields)
                .filter(e -> (e.access & ACC_STATIC) == 0)
                .toList();

        traitFields = FastStream.of(instanceFields)
                .map(f -> new FieldMixin(f.name, f.desc, f.access))
                .toList();

        fieldNameLookup = FastStream.of(traitFields)
                .toMap(FieldMixin::name, e -> e.getAccessName(cNode.name));

        methodSigLookup = FastStream.of(cNode.methods)
                .toMap(e -> e.name + e.desc, Function.identity());
        mixinInfo = operate();
    }

    protected void checkNode() {
        preCheckNode();
        if ((cNode.access & ACC_INTERFACE) != 0) {
            throw new IllegalArgumentException("Cannot register java interface '" + cNode.name + "' as a mixin trait.");
        }
        if (!cNode.innerClasses.isEmpty() && !ColUtils.anyMatch(cNode.innerClasses, innerNode -> innerNode.outerName != null && !cNode.name.equals(innerNode.outerName) && !innerNode.name.startsWith(cNode.name))) {
            throw new IllegalArgumentException("Found illegal inner class for '" + cNode.name + "', use scala.");
        }
        List<FieldNode> invalidFields = FastStream.of(cNode.fields)
                .filter(e -> (e.access & ACC_PRIVATE) == 0)
                .toList();
        if (!invalidFields.isEmpty()) {
            String fields = "[" + FastStream.of(invalidFields)
                    .map(e -> e.name)
                    .join(", ") + "]";
            throw new IllegalArgumentException("Illegal fields " + fields + " found in " + cNode.name + ". These fields must be private.");
        }

        if ((cNode.access & ACC_ABSTRACT) != 0) {
            throw new IllegalArgumentException("Cannot register abstract class " + cNode.name + " as a java mixin trait. Use scala");
        }
    }

    protected MixinInfo operate() {

        beforeTransform();

        sNode.visit(V1_8, ACC_PUBLIC, cNode.name + "$", null, "java/lang/Object", new String[0]);
        sNode.sourceFile = cNode.sourceFile;

        tNode.visit(V1_8, ACC_INTERFACE | ACC_ABSTRACT | ACC_PUBLIC, cNode.name, null, "java/lang/Object", cNode.interfaces.toArray(new String[0]));
        tNode.sourceFile = cNode.sourceFile;

        //Clone static fields to StaticNode
        staticFields.forEach(f -> sNode.visitField(ACC_STATIC, f.name, f.desc, f.signature, f.value));

        traitFields.forEach(f -> {
            tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, fieldNameLookup.get(f.name()), "()" + f.desc(), null, null);
            tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, fieldNameLookup.get(f.name()) + "_$eq", "(" + f.desc() + ")V", null, null);
        });
        cNode.methods.forEach(this::convertMethod);
        return new MixinInfo(tNode.name, cNode.superName, Collections.emptyList(), traitFields, traitMethods, List.copyOf(supers));
    }

    protected void preCheckNode() {
    }

    protected void beforeTransform() {
    }

    public @Nullable ClassNode getStaticNode() {
        if (sNode.methods.isEmpty() && sNode.fields.isEmpty()) {
            return null; // Pointless.
        }
        return sNode;
    }

    public ClassNode getTraitNode() {
        return tNode;
    }

    public MixinInfo getMixinInfo() {
        return mixinInfo;
    }

    private void staticTransform(MethodNode mNode, MethodNode base) {
        StackAnalyser stack = new StackAnalyser(Type.getObjectType(cNode.name), base);
        InsnList insnList = mNode.instructions;
        InsnPointer pointer = new InsnPointer(insnList);

        AbstractInsnNode insn;
        while ((insn = pointer.get()) != null) {
            if (insn instanceof FieldInsnNode fInsn) {
                if (fInsn.owner.equals(cNode.name)) {
                    if (insn.getOpcode() == GETFIELD) {
                        pointer.replace(new MethodInsnNode(INVOKEINTERFACE, cNode.name, fieldNameLookup.get(fInsn.name), "()" + fInsn.desc, true));
                    } else if (insn.getOpcode() == PUTFIELD) {
                        pointer.replace(new MethodInsnNode(INVOKEINTERFACE, cNode.name, fieldNameLookup.get(fInsn.name) + "_$eq", "(" + fInsn.desc + ")V", true));
                    } else if (insn.getOpcode() == GETSTATIC || insn.getOpcode() == PUTSTATIC) {
                        fInsn.owner = fInsn.owner + "$";
                    }
                }
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode mInsn = (MethodInsnNode) insn;
                if (mInsn.getOpcode() == INVOKESPECIAL) {
                    MethodInfo sMethod = getSuper(mInsn, stack);
                    if (sMethod != null) {
                        String bridgeName = cNode.name.replace("/", "$") + "$$super$" + mInsn.name;
                        if (supers.add(mInsn.name + mInsn.desc)) {
                            tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, bridgeName, mInsn.desc, null, null);
                        }
                        pointer.replace(new MethodInsnNode(INVOKEINTERFACE, cNode.name, bridgeName, mInsn.desc, true));
                    } else {
                        MethodNode target = methodSigLookup.get(mInsn.name + mInsn.desc);
                        if (target != null) {
                            if ((target.access & ACC_PRIVATE) != 0) {
                                //Call the static method impl
                                pointer.replace(new MethodInsnNode(INVOKESTATIC, mInsn.owner, mInsn.name, Utils.staticDesc(mInsn.owner, mInsn.desc), true));
                            }
                        }
                    }
                } else if (mInsn.getOpcode() == INVOKEVIRTUAL) {
                    if (mInsn.owner.equals(cNode.name)) {
                        MethodNode target = methodSigLookup.get(mInsn.name + mInsn.desc);
                        if (target != null) {
                            if ((target.access & ACC_PRIVATE) != 0) {
                                // call the static impl method
                                pointer.replace(new MethodInsnNode(INVOKESTATIC, mInsn.owner, mInsn.name, Utils.staticDesc(mInsn.owner, mInsn.desc), true));
                            } else {
                                // call the interface method
                                pointer.replace(new MethodInsnNode(INVOKEINTERFACE, mInsn.owner, mInsn.name, mInsn.desc, true));
                            }
                        } else {
                            //cast to parent class and call
                            Type mType = Type.getMethodType(mInsn.desc);
                            StackAnalyser.StackEntry instanceEntry = stack.peek(StackAnalyser.width(mType.getArgumentTypes()));
                            insnList.insert(instanceEntry.insn, new TypeInsnNode(CHECKCAST, cNode.superName));
                            mInsn.owner = cNode.superName;
                        }
                    }
                    //Ensure we cast when we call methods that take our parent's type.
                    List<StackAnalyser.StackEntry> entries = peekArgs(stack, mInsn.desc);
                    Type[] argumentTypes = Type.getMethodType(mInsn.desc).getArgumentTypes();
                    for (int i = 0; i < argumentTypes.length; i++) {
                        Type arg = argumentTypes[i];
                        StackAnalyser.StackEntry entry = entries.get(i);
                        if (!arg.getInternalName().equals("java/lang/Object") && ClassHierarchyManager.classExtends(cNode.superName.replace("/", "."), arg.getInternalName().replace("/", "."))) {
                            insnList.insert(entry.insn, new TypeInsnNode(CHECKCAST, cNode.superName));
                        }
                    }
                } else if (mInsn.getOpcode() == INVOKESTATIC) {
                    if (mInsn.owner.equals(cNode.name) && methodSigLookup.get(mInsn.name + mInsn.desc) != null) {
                        mInsn.owner = mInsn.owner + "$";
                    }
                }
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode mInsn = (InvokeDynamicInsnNode) insn;
                Object[] bsmArgs = mInsn.bsmArgs;
                for (int i = 0; i < bsmArgs.length; i++) {
                    Object bsmArg = bsmArgs[i];
                    if (!(bsmArg instanceof Handle handle)) continue;
                    if (handle.getOwner().equals(cNode.name) && handle.getTag() == H_INVOKESTATIC) {
                        bsmArgs[i] = new Handle(handle.getTag(), handle.getOwner() + "$", handle.getName(), handle.getDesc(), handle.isInterface());
                    }
                }
            }

            stack.visitInsn(pointer.get());
            pointer.advance();
        }
    }

    private void convertMethod(MethodNode mNode) {
        if (mNode.name.equals("<init>")) {
            if (!mNode.desc.equals("()V")) {
                throw new IllegalArgumentException("Constructor arguments are not permitted " + mNode.name + " as a mixin trait");
            }
            MethodNode mv = staticClone(mNode, "$init$", ACC_PUBLIC);

            //Strip super constructor call.
            InsnListSection insns = new InsnListSection();
            insns.add(new VarInsnNode(ALOAD, 0));
            insns.add(new MethodInsnNode(INVOKESPECIAL, cNode.superName, "<init>", "()V", false));

            InsnListSection mInsns = new InsnListSection(mv.instructions);
            InsnListSection found = InsnComparator.matches(mInsns, insns, Collections.emptySet());
            if (found == null) {
                throw new IllegalArgumentException("Invalid constructor insn sequence " + cNode.name + "\n" + mInsns);
            }
            found.trim(Collections.emptySet()).remove();
            staticTransform(mv, mNode);
            return;
        }
        if ((mNode.access & ACC_STATIC) != 0) {
            int mask = ACC_PRIVATE | ACC_PROTECTED;
            int access = (mNode.access & ~mask) | ACC_PUBLIC;
            MethodNode mv = (MethodNode) sNode.visitMethod(access, mNode.name, mNode.desc, null, null);
            ASMHelper.copy(mNode, mv);
            staticTransform(mv, mNode);
            return;
        }
        boolean isPrivate = (mNode.access & ACC_PRIVATE) != 0;
        int access = !isPrivate ? ACC_PUBLIC : ACC_PRIVATE;
        if (!isPrivate) {
            MethodVisitor mv = tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, mNode.name, mNode.desc, null, mNode.exceptions.toArray(new String[0]));
            traitMethods.add((MethodNode) mv);
        }
        MethodNode mv = staticClone(mNode, !isPrivate ? mNode.name + "$" : mNode.name, access);
        staticTransform(mv, mNode);
    }

    private @Nullable MethodInfo getSuper(MethodInsnNode mInsn, StackAnalyser stack) {
        if (mInsn.owner.equals(stack.owner.getInternalName())) {
            return null;//not a super call
        }

        //super calls are either to methods with the same name or contain a pattern 'target$$super$name' from the scala compiler
        String methodName = stack.mNode.name.replaceAll(".+\\Q$$super$\\E", "");
        if (!mInsn.name.equals(methodName)) {
            return null;
        }

        StackAnalyser.StackEntry entry = stack.peek(Type.getType(mInsn.desc).getArgumentTypes().length);
        if (!(entry instanceof StackAnalyser.Load load)) {
            return null;
        }
        if (!(load.e instanceof StackAnalyser.This)) {
            return null;
        }

        return Objects.requireNonNull(mixinCompiler.getClassInfo(stack.owner.getInternalName()), "Failed to load class: " + stack.owner.getInternalName())
                .findPublicParentImpl(methodName, mInsn.desc);
    }

    private MethodNode staticClone(MethodNode mNode, String name, int access) {
        ClassNode target = mNode.name.equals("<clinit>") ? sNode : tNode;
        String desc = (mNode.access & ACC_STATIC) == 0 ? Utils.staticDesc(cNode.name, mNode.desc) : mNode.desc;
        MethodNode mv = (MethodNode) target.visitMethod(access | ACC_STATIC, name, desc, null, mNode.exceptions.toArray(new String[0]));
        ASMHelper.copy(mNode, mv);
        return mv;
    }

    private static List<StackAnalyser.StackEntry> peekArgs(StackAnalyser analyser, String desc) {
        int len = Type.getArgumentTypes(desc).length;
        StackAnalyser.StackEntry[] args = new StackAnalyser.StackEntry[len];

        for (int i = 0; i < len; ++i) {
            args[len - i - 1] = analyser.peek(i);
        }

        return Arrays.asList(args);
    }

    public static class InsnPointer {

        public final InsnList insnList;
        public @Nullable AbstractInsnNode pointer;

        public InsnPointer(InsnList insnList) {
            this.insnList = insnList;
            pointer = insnList.getFirst();
        }

        private void replace(AbstractInsnNode newInsn) {
            insnList.insert(pointer, newInsn);
            insnList.remove(pointer);
            pointer = newInsn;
        }

        public @Nullable AbstractInsnNode get() {
            return pointer;
        }

        public AbstractInsnNode advance() {
            assert pointer != null;
            return pointer = pointer.getNext();
        }
    }

}
