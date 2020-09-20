package codechicken.mixin.util;

import codechicken.asm.*;
import codechicken.mixin.api.MixinCompiler;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    protected List<String> supers = new ArrayList<>();
    protected MixinInfo mixinInfo;

    protected Map<String, String> fieldNameLookup;
    protected Map<String, MethodNode> methodSigLookup;

    public JavaTraitGenerator(MixinCompiler mixinCompiler, ClassNode cNode) {
        this.mixinCompiler = mixinCompiler;
        this.cNode = cNode;
        this.tNode = new ClassNode();
        this.sNode = new ClassNode();
        checkNode();
        operate();
    }

    protected void checkNode() {
        preCheckNode();
        if ((cNode.access & ACC_PUBLIC) != 0) {
            throw new IllegalArgumentException("Java trait '" + cNode.name + "' must not be declared public.");
        }
        if ((cNode.access & ACC_INTERFACE) != 0) {
            if (Utils.isScalaClass(cNode)) {
                throw new IllegalArgumentException("Cannot register scala trait interface '" + cNode.name + "' as a mixin trait. Please include the scala module on the classpath.");
            }
            throw new IllegalArgumentException("Cannot register java interface '" + cNode.name + "' as a mixin trait.");
        }
        if (!cNode.innerClasses.isEmpty() && cNode.innerClasses.stream().noneMatch(this::checkInner)) {
            throw new IllegalArgumentException("Found illegal inner class for '" + cNode.name + "', use scala.");
        }

        if ((cNode.access & ACC_ABSTRACT) != 0) {
            throw new IllegalArgumentException("Cannot register abstract class " + cNode.name + " as a java mixin trait. Use scala");
        }
    }

    protected void operate() {
        preProcessTrait();

        staticFields = cNode.fields.stream()//
                .filter(e -> (e.access & ACC_STATIC) != 0)//
                .collect(Collectors.toList());
        staticFields.forEach(e -> {
            if ((e.access & ACC_PRIVATE) == 0) {
                throw new IllegalStateException("Static field '" + e.name + "' in java trait '" + cNode.name + "' must be private.");
            }
        });

        instanceFields = cNode.fields.stream()//
                .filter(e -> (e.access & ACC_STATIC) == 0)//
                .collect(Collectors.toList());

        traitFields = instanceFields.stream()//
                .map(f -> new FieldMixin(f.name, f.desc, f.access))//
                .collect(Collectors.toList());

        fieldNameLookup = traitFields.stream()//
                .collect(Collectors.toMap(FieldMixin::getName, e -> e.getAccessName(cNode.name)));

        methodSigLookup = cNode.methods.stream()//
                .collect(Collectors.toMap(e -> e.name + e.desc, Function.identity()));

        beforeTransform();

        sNode.visit(V1_8, ACC_PUBLIC, cNode.name + "$", null, "java/lang/Object", new String[0]);
        sNode.sourceFile = cNode.sourceFile;

        tNode.visit(V1_8, ACC_INTERFACE | ACC_ABSTRACT | ACC_PUBLIC, cNode.name, null, "java/lang/Object", cNode.interfaces.toArray(new String[0]));
        tNode.sourceFile = cNode.sourceFile;

        //Clone static fields to StaticNode
        staticFields.forEach(f -> sNode.visitField(ACC_STATIC, f.name, f.desc, f.signature, f.value));

        traitFields.forEach(f -> {
            tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, fieldNameLookup.get(f.getName()), "()" + f.getDesc(), null, null);
            tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, fieldNameLookup.get(f.getName()) + "_$eq", "(" + f.getDesc() + ")V", null, null);
        });
        cNode.methods.forEach(this::convertMethod);
        postProcessTrait();
        mixinInfo = new MixinInfo(tNode.name, cNode.superName, Collections.emptyList(), traitFields, traitMethods, supers);
    }

    protected void preCheckNode() {
    }

    protected void preProcessTrait() {
    }

    protected void beforeTransform() {
    }

    protected void postProcessTrait() {
    }

    public Optional<ClassNode> getStaticNode() {
        if (sNode.methods.isEmpty() && sNode.fields.isEmpty()) {
            return Optional.empty();//Pointless.
        }
        return Optional.of(sNode);
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
            if (insn instanceof FieldInsnNode) {
                FieldInsnNode fInsn = (FieldInsnNode) insn;
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
                    Optional<MethodInfo> optMethod = getSuper(mInsn, stack);
                    if (optMethod.isPresent()) {
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
                                ///call the static impl method
                                pointer.replace(new MethodInsnNode(INVOKEVIRTUAL, mInsn.owner, mInsn.name, Utils.staticDesc(mInsn.owner, mInsn.desc), true));
                            } else {
                                //call the interface method
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
        boolean isPrivate = (mNode.access & ACC_PRIVATE) != 0;
        int access = !isPrivate ? ACC_PUBLIC : ACC_PRIVATE;
        if (!isPrivate) {
            MethodVisitor mv = tNode.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, mNode.name, mNode.desc, null, mNode.exceptions.toArray(new String[0]));
            traitMethods.add((MethodNode) mv);
        }
        MethodNode mv = staticClone(mNode, !isPrivate ? mNode.name + "$" : mNode.name, access);
        staticTransform(mv, mNode);
    }

    private Optional<MethodInfo> getSuper(MethodInsnNode mInsn, StackAnalyser stack) {
        if (mInsn.owner.equals(stack.owner.getInternalName())) {
            return Optional.empty();//not a super call
        }

        //super calls are either to methods with the same name or contain a pattern 'target$$super$name' from the scala compiler
        String methodName = stack.mNode.name.replaceAll(".+\\Q$$super$\\E", "");
        if (!mInsn.name.equals(methodName)) {
            return Optional.empty();
        }

        StackAnalyser.StackEntry entry = stack.peek(Type.getType(mInsn.desc).getArgumentTypes().length);
        if (!(entry instanceof StackAnalyser.Load)) {
            return Optional.empty();
        }
        StackAnalyser.Load load = (StackAnalyser.Load) entry;
        if (!(load.e instanceof StackAnalyser.This)) {
            return Optional.empty();
        }

        return mixinCompiler.getClassInfo(stack.owner.getInternalName()).findPublicParentImpl(methodName, mInsn.desc);
    }

    private MethodNode staticClone(MethodNode mNode, String name, int access) {
        ClassNode target = mNode.name.equals("<clinit>") ? sNode : tNode;
        String desc = (mNode.access & ACC_STATIC) == 0 ? Utils.staticDesc(cNode.name, mNode.desc) : mNode.desc;
        MethodNode mv = (MethodNode) target.visitMethod(access | ACC_STATIC, name, desc, null, mNode.exceptions.toArray(new String[0]));
        ASMHelper.copy(mNode, mv);
        return mv;
    }

    //true if pass
    private boolean checkInner(InnerClassNode innerNode) {
        if (innerNode.outerName == null) {
            return false;
        }
        if (cNode.name.equals(innerNode.outerName)) {
            return false;
        }
        if (innerNode.name.startsWith(cNode.name)) {
            return false;
        }
        return true;
    }

    private static List<StackAnalyser.StackEntry> peekArgs(StackAnalyser analyser, String desc) {
        int len = Type.getArgumentTypes(desc).length;
        StackAnalyser.StackEntry[] args = new StackAnalyser.StackEntry[len];

        for (int i = 0; i < len; ++i) {
            args[len - i - 1] = analyser.peek();
        }

        return Arrays.asList(args);
    }

    public static class InsnPointer {

        public final InsnList insnList;
        public AbstractInsnNode pointer;

        public InsnPointer(InsnList insnList) {
            this.insnList = insnList;
            pointer = insnList.getFirst();
        }

        private void replace(AbstractInsnNode newInsn) {
            insnList.insert(pointer, newInsn);
            insnList.remove(pointer);
            pointer = newInsn;
        }

        public AbstractInsnNode get() {
            return pointer;
        }

        public AbstractInsnNode advance() {
            return pointer = pointer.getNext();
        }
    }

}
