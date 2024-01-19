package codechicken.mixin.util;

import codechicken.mixin.api.MixinDebugger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static codechicken.mixin.MixinCompilerImpl.LOG_LEVEL;

/**
 * Created by covers1624 on 2/9/20.
 */
public class SimpleDebugger implements MixinDebugger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleDebugger.class);

    private final Path root;
    private final DumpType type;

    public SimpleDebugger(Path root, DumpType type) {
        this.root = root;
        this.type = type;
        try {
            if (Files.exists(root)) {
                if (!Files.isDirectory(root)) {
                    LOGGER.warn("Expected '{}' to be a directory. Overwriting..", root);
                    Files.delete(root);
                } else {
                    LOGGER.atLevel(LOG_LEVEL).log("Clearing debugger output. '{}'", root.toAbsolutePath());
                    Utils.deleteFolder(root);
                }
            }
            Files.createDirectories(root);
        } catch (IOException e) {
            LOGGER.error("Encountered an error setting up SimpleDebugger.", e);
        }
    }

    @Override
    public void defineInternal(String name, byte[] bytes) {
        dump("internal_define", name, bytes);
    }

    @Override
    public void defineClass(String name, byte[] bytes) {
        dump("define", name, bytes);
    }

    public void dump(String from, String name, byte[] bytes) {
        name = name.replace("/", ".");
        try {
            Path folder = root.resolve(from);
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
            switch (type) {
                case TEXT: {
                    try {
                        LOGGER.atLevel(LOG_LEVEL).log("Dumping '{}' from {} as text", name, from);
                        Path path = folder.resolve(name + ".txt");
                        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                            ClassVisitor cv = new TraceClassVisitor(null, new Textifier(), new PrintWriter(writer));
                            ClassReader reader = new ClassReader(bytes);
                            reader.accept(cv, ClassReader.EXPAND_FRAMES);
                        }
                        break;
                    } catch (IOException e) {
                        throw e;// Rethrow
                    } catch (Exception e) {
                        LOGGER.warn("Fatal exception dumping as text. Dumping as binary.", e);
                        //Fall through to Binary.
                    }
                }
                case BINARY: {
                    LOGGER.atLevel(LOG_LEVEL).log("Dumping '{}' from {} as binary.", name, from);
                    Path path = folder.resolve(name + ".class");
                    try (OutputStream os = Files.newOutputStream(path)) {
                        os.write(bytes);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Unable to dump '{}' to disk.", name, e);
        }
    }

    public enum DumpType {
        TEXT,
        BINARY,
        ;
    }
}
