package codechicken.mixin.scala;

import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.function.Supplier;

/**
 * Created by covers1624 on 18/1/24.
 */
public class ScalaSignature {

    public final int major;
    public final int minor;
    public final SigEntry[] table;

    public ScalaSignature(String sig) {
        this(parseBytes(sig));
    }

    private ScalaSignature(Bytes bytes) {
        Reader reader = bytes.reader();
        major = reader.readByte();
        minor = reader.readByte();
        table = new SigEntry[reader.readNat()];
        for (int i = 0; i < table.length; i++) {
            int index = i;
            int start = reader.pos;
            int tpe = reader.readByte();
            int len = reader.readNat();
            table[index] = reader.advance(len, () -> new SigEntry(index, start, new Bytes(bytes.arr, reader.pos, len)));
        }
    }

    private static Bytes parseBytes(String str) {
        byte[] bytes = str.getBytes();
        int len = ByteCodecs.decode(bytes);
        return new Bytes(bytes, 0, len);
    }

    public static @Nullable ScalaSignature parse(ClassNode cNode) {
        return FastStream.of(cNode.visibleAnnotations)
                .filter(e -> e.desc.equals("Lscala/reflect/ScalaSignature;"))
                .map(e -> e.values.get(1).toString())
                .map(ScalaSignature::new)
                .firstOrDefault();
    }

    public <T> List<T> collect(int id) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < table.length; i++) {
            if (table[i].id() == id) {
                list.add(evalT(i));
            }
        }
        return list;
    }

    public @Nullable ObjectSymbol findObject(String name) {
        return FastStream.of(this.<ObjectSymbol>collect(7)).filter(e -> e.full().equals(name)).first();
    }

    public @Nullable ClassSymbol findClass(String name) {
        return FastStream.of(this.<ClassSymbol>collect(6)).filter(e -> !e.isModule() && e.full().equals(name)).first();
    }

    public String evalS(int i) {
        SigEntry e = table[i];
        Bytes bc = e.bytes;
        Reader bcr = bc.reader();
        return switch (e.id()) {
            case 1, 2 -> bcr.readString(bc.len);
            case 3 -> "<no symbol>";
            case 9, 10 -> {
                String s = evalS(bcr.readNat());
                if (bc.pos + bc.len > bcr.pos) {
                    s = evalS(bcr.readNat()) + "." + s;
                }
                yield s;
            }
            default -> throw new RuntimeException("Switch falloff");
        };
    }

    public <T> T evalT(int i) {
        return SneakyUtils.unsafeCast(eval(i));
    }

    private <T> List<T> evalList(Reader reader) {
        List<T> list = new ArrayList<>();
        while (reader.more()) {
            list.add(evalT(reader.readNat()));
        }
        return list;
    }

    public Object eval(int i) {
        SigEntry e = table[i];
        Reader bcr = e.bytes.reader();
        return switch (e.id()) {
            case 1, 2 -> evalS(i);
            case 3 -> new NoSymbol();
            case 6 -> new ClassSymbol(this, evalS(bcr.readNat()), evalT(bcr.readNat()), bcr.readNat(), bcr.readNat());
            case 7 -> new ObjectSymbol(this, evalS(bcr.readNat()), evalT(bcr.readNat()), bcr.readNat(), bcr.readNat());
            case 8 -> new MethodSymbol(this, evalS(bcr.readNat()), evalT(bcr.readNat()), bcr.readNat(), bcr.readNat());
            case 9, 10 -> new ExternalSymbol(evalS(i));
            case 11, 12 -> new NoType(); // 12 is actually NoPrefixType (no lower bound)
            case 13 -> new ThisType(evalT(bcr.readNat()));
            case 14 -> new SingleType(evalT(bcr.readNat()), evalT(bcr.readNat()));
            case 16 -> new TypeRefType(evalT(bcr.readNat()), evalT(bcr.readNat()), evalList(bcr));
            case 19 -> new ClassType(evalT(bcr.readNat()), evalList(bcr));
            case 20 -> new MethodType(evalT(bcr.readNat()), evalList(bcr));
            case 21, 48 -> new ParameterlessType(evalT(bcr.readNat()));
            case 25 -> new BooleanLiteral(bcr.readLong() != 0);
            case 26 -> new ByteLiteral((byte) bcr.readLong());
            case 27 -> new ShortLiteral((short) bcr.readLong());
            case 28 -> new CharLiteral((char) bcr.readLong());
            case 29 -> new IntLiteral((int) bcr.readLong());
            case 30 -> new LongLiteral(bcr.readLong());
            case 31 -> new FloatLiteral(Float.intBitsToFloat((int) bcr.readLong()));
            case 32 -> new DoubleLiteral(Double.longBitsToDouble(bcr.readLong()));
            case 33 -> new StringLiteral(evalS(bcr.readNat()));
            case 34 -> new NullLiteral();
            case 35 -> new TypeLiteral(evalT(bcr.readNat()));
            case 36 -> new EnumLiteral(evalT(bcr.readNat()));
            case 40 -> new AnnotationInfo(evalT(bcr.readNat()), evalT(bcr.readNat()), arrayElements(evalList(bcr)));
            case 44 -> new ArrayLiteral(evalList(bcr));
            default -> e;
        };
    }

    private static Map<String, Literal> arrayElements(List<Object> list) {
        Map<String, Literal> elements = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            elements.put((String) list.get(i), (Literal) list.get(i + 1));
        }
        return elements;
    }

    // @formatter:off
    public record SigEntry(int index, int start, Bytes bytes) {
        public byte id() { return bytes.arr[start]; }
        @Override public String toString() { return "SigEntry(" + index + ", " + id() + ", " + bytes.len + " bytes)"; }
    }
    public interface Flags {
        boolean hasFlag(int flag);
        default boolean isPrivate() { return hasFlag(0x00000004); }
        default boolean isProtected() { return hasFlag(0x00000008); }
        default boolean isAbstract() { return hasFlag(0x00000080); }
        default boolean isDeferred() { return hasFlag(0x00000100); }
        default boolean isMethod() { return hasFlag(0x00000200); }
        default boolean isModule() { return hasFlag(0x00000400); }
        default boolean isInterface() { return hasFlag(0x00000800); }
        default boolean isParam() { return hasFlag(0x00002000); }
        default boolean isStatic() { return hasFlag(0x00800000); }
        default boolean isTrait() { return hasFlag(0x02000000); }
        default boolean isAccessor() { return hasFlag(0x08000000); }
    }
    public interface SymbolRef extends Flags {
        String full();
        int flags();
        @Override default boolean hasFlag(int flag) { return (flags() & flag) != 0; }
    }
    public interface ClassSymbolRef extends SymbolRef {
        ScalaSignature sig();
        String name();
        SymbolRef owner();
        int flags();
        int infoId();
        default String full() { return owner().full() + "." + name(); }
        default boolean isObject() { return false; }
        default ClassType info() { return sig().evalT(infoId() ); }
        default String jParent() { return info().parent().jName(); }
        default List<String> jInterfaces() { return FastStream.of(info().interfaces()).map(TypeRef::jName).toList(); }
    }
    public record ClassSymbol(ScalaSignature sig, String name, SymbolRef owner, int flags, int infoId) implements ClassSymbolRef {
        @Override public String toString() { return "ClassSymbol(" + name + ", " + owner + ", 0x" + Integer.toHexString(flags) + ", " + infoId + ")"; }
    }
    public record ObjectSymbol(ScalaSignature sig, String name, SymbolRef owner, int flags, int infoId) implements ClassSymbolRef {
        @Override public boolean isObject() { return true; }
        @Override public ClassType info() { return ((ClassSymbol) sig.<TypeRefType>evalT(infoId).sym).info(); }
        @Override public String toString() { return "ObjectSymbol(" + name + ", " + owner + ", 0x" + Integer.toHexString(flags) + ", " + infoId + ")"; }
    }
    public record MethodSymbol(ScalaSignature sig, String name, SymbolRef owner, int flags, int infoId) implements SymbolRef {
        @Override public String full() { return owner.full() + "." + name; }
        public TMethodType info() { return sig.evalT(infoId ); }
        public String jDesc() { return info().jDesc(); }
        @Override public String toString() { return "MethodSymbol(" + name + ", " + owner + ", 0x" + Integer.toHexString(flags) + ", " + infoId + ")"; }
    }
    public record ExternalSymbol(String name) implements SymbolRef {
        @Override public String full() { return name; }
        @Override public int flags() { return 0; }
    }
    private record NoSymbol() implements SymbolRef {
        @Override public String full() { return "<no symbol>"; }
        @Override public int flags() { return 0; }
    }
    public interface TMethodType {
        default String jDesc() { return "(" + FastStream.of(params()).map(e -> e.info().returnType().jDesc()).join("") + ")" + returnType().jDesc(); }
        TypeRef returnType();
        List<MethodSymbol> params();
    }
    public record ClassType(SymbolRef owner, List<TypeRef> parents) {
        public TypeRef parent() { return parents.get(0); }
        public List<TypeRef> interfaces() { return parents.subList(1, parents.size()); }
    }
    public record MethodType(TypeRef returnType, List<MethodSymbol> params) implements TMethodType { }
    public record ParameterlessType(TypeRef returnType) implements TMethodType {
        @Override public List<MethodSymbol> params() { return List.of(); }
    }
    public interface TypeRef {
        SymbolRef sym();
        default String name() { return sym().full(); }
        default String jName() {
            String e = name().replace('.', '/');
            return switch (e) {
                case "scala/AnyRef", "scala/Any" -> "java/lang/Object";
                case "scala/Predef/String" -> "java/lang/String";
                default -> e;
            };
        }
        default String jDesc() {
            return switch (name()) {
                case "scala.Array" -> null;
                case "scala.Long" -> "J";
                case "scala.Int" -> "I";
                case "scala.Short" -> "S";
                case "scala.Byte" -> "B";
                case "scala.Double" -> "D";
                case "scala.Float" -> "F";
                case "scala.Boolean" -> "Z";
                case "scala.Unit" -> "V";
                default -> "L" + jName() + ";";
            };
        }
    }
    public record TypeRefType(TypeRef owner, SymbolRef sym, List<TypeRef> typArgs) implements TMethodType, TypeRef {
        @Override public List<MethodSymbol> params() { return List.of(); }
        @Override
        public TypeRef returnType() { return this; }
        @Override
        public String jDesc() {
            if (name().equals("scala.Array")) return "[" + typArgs.get(0).jDesc();
            return TypeRef.super.jDesc();
        }
    }
    public record ThisType(SymbolRef sym) implements TypeRef { }
    public record SingleType(TypeRef owner, SymbolRef sym) implements TypeRef {
        @Override public String jName() { return TypeRef.super.jName() + "$"; }
    }
    public record NoType() implements TypeRef {
        @Override public SymbolRef sym() { return null; }
        @Override public String name() { return "<no type>"; }
    }
    public interface Literal {
        Object value();
    }
    public record BooleanLiteral(Boolean value) implements Literal { }
    public record ByteLiteral(Byte value) implements Literal { }
    public record ShortLiteral(Short value) implements Literal { }
    public record CharLiteral(Character value) implements Literal { }
    public record IntLiteral(Integer value) implements Literal { }
    public record LongLiteral(Long value) implements Literal { }
    public record FloatLiteral(Float value) implements Literal { }
    public record DoubleLiteral(Double value) implements Literal { }
    public record NullLiteral() implements Literal {
        @Override public Object value() { return null; }
    }
    public record StringLiteral(String value) implements Literal { }
    public record TypeLiteral(TypeRef value) implements Literal { }
    public record EnumLiteral(ExternalSymbol value) implements Literal { }
    public record ArrayLiteral(List<?> value) implements Literal { }
    public record AnnotationInfo(SymbolRef owner, TypeRef annType, Map<String, Literal> values) {
        public <T> T getValue(String name) {
            return SneakyUtils.unsafeCast(values.get(name));
        }
    }
    // @formatter:on

    private static final class Reader {

        private final Bytes bc;
        public int pos;

        private Reader(Bytes bc) {
            this.bc = bc;
            pos = bc.pos;
        }

        public boolean more() {
            return pos < bc.pos + bc.len;
        }

        public String readString(int len) {
            return advance(len, () -> new String(Arrays.copyOfRange(bc.arr, pos, pos + len)));
        }

        public byte readByte() {
            readCheck(1);
            return bc.arr[pos++];
        }

        public int readNat() {
            var r = 0;
            var b = 0;
            do {
                b = readByte();
                r = r << 7 | b & 0x7F;
            }
            while ((b & 0x80) != 0);
            return r;
        }

        public long readLong() {
            var l = 0L;
            while (more()) {
                l <<= 8;
                l |= readByte() & 0xFF;
            }
            return l;
        }

        public <T> T advance(int len, Supplier<T> f) {
            readCheck(len);
            T t = f.get();
            pos += len;
            return t;
        }

        private void readCheck(int len) {
            if (pos + len > bc.pos + bc.len) {
                throw new IllegalArgumentException("Ran off the end of bytecode");
            }
        }
    }

    private record Bytes(byte[] arr, int pos, int len) {

        public Reader reader() {
            return new Reader(this);
        }
    }
}
