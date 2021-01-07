package codechicken.mixin.util;

import codechicken.mixin.api.MixinCompiler;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static codechicken.mixin.util.Utils.asmName;
import static org.objectweb.asm.Opcodes.*;

/**
 * Created by covers1624 on 7/1/21.
 */
public class FactoryGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static final int[] LOADS = {
            -1,
            ILOAD,
            ILOAD,
            ILOAD,
            ILOAD,
            ILOAD,
            FLOAD,
            LLOAD,
            DLOAD,
            ALOAD,
            ALOAD,
            -1,
            -1
    };

    private final MixinCompiler compiler;

    public FactoryGenerator(MixinCompiler compiler) {
        this.compiler = compiler;
    }

    public Method findMethod(Class<?> clazz) {
        if (!clazz.isInterface()) {
            throw new RuntimeException("Class is not an interface.");
        }
        Method[] methods = Arrays.stream(clazz.getMethods())
                .filter(e -> !Modifier.isStatic(e.getModifiers()))
                .filter(e -> Modifier.isAbstract(e.getModifiers()))
                .toArray(Method[]::new);
        if (methods.length == 0) {
            throw new IllegalArgumentException("No implementable methods found for class: " + clazz.getName());
        }
        if (methods.length > 1) {
            String names = Arrays.stream(methods)
                    .map(Method::getName)
                    .collect(Collectors.joining(", ", "[", "]"));
            throw new IllegalStateException("Multiple implementable methods found. " + names + " in " + clazz.getName());
        }
        return methods[0];
    }

    public <T, F> F generateFactory(Class<T> actualClass, Class<F> factoryClazz) {
        Method factoryMethod = findMethod(factoryClazz);
        Constructor<T> constructor = Utils.findConstructor(actualClass, factoryMethod.getParameterTypes())
                .orElseThrow(() -> new IllegalArgumentException("Unable to find constructor for " + actualClass.getName() + " that matches Factory method in " + factoryClazz.getName()));

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        MethodVisitor mv;

        String cName = actualClass.getName() + "$$Ctor$$" + COUNTER.getAndIncrement();
        cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL | ACC_SYNTHETIC, cName, null, asmName(Object.class), new String[] { asmName(factoryClazz) });

        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, asmName(Object.class), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        Type factoryType = Type.getType(factoryMethod);
        Type[] params = factoryType.getArgumentTypes();
        mv = cw.visitMethod(ACC_PUBLIC, factoryMethod.getName(), factoryType.getDescriptor(), null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, asmName(actualClass));
        mv.visitInsn(DUP);
        int count = 0;
        for (Type param : params) {
            //TODO, this likely needs to account for Boxed <-> Primitive.
            // and should get parameters from the Constructor, inserting CHECKCAST where necessary.
            mv.visitVarInsn(LOADS[param.getSort()], count);
            count += (param.getSort() == Type.DOUBLE || param.getSort() == Type.LONG) ? 2 : 1;
        }
        mv.visitMethodInsn(INVOKESPECIAL, asmName(actualClass), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, params), false);
        mv.visitTypeInsn(CHECKCAST, asmName(factoryType.getReturnType().getInternalName()));
        mv.visitInsn(ARETURN);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();

        Class<F> factory = compiler.defineClass(cName, bytes);
        try {
            return factory.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Unable to instantiate new factory.", e);
        }
    }

}
