package codechicken.mixin.util;

import codechicken.mixin.api.MixinDebugger;
import net.covers1624.quack.io.IOUtils;
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

    private final Path folder;
    private final DumpType type;

    public SimpleDebugger(Path folder, DumpType type) {
        this.folder = folder;
        this.type = type;
        try {
            if (Files.exists(folder)) {
                if (!Files.isDirectory(folder)) {
                    LOGGER.warn("Expected '{}' to be a directory. Overwriting..", folder);
                    Files.delete(folder);
                } else {
                    LOGGER.atLevel(LOG_LEVEL).log("Clearing debugger output. '{}'", folder.toAbsolutePath());
                    Utils.deleteFolder(folder);
                }
            }
            Files.createDirectories(folder);
        } catch (IOException e) {
            LOGGER.error("Encountered an error setting up SimpleDebugger.", e);
        }
    }

    @Override
    public void defineClass(String name, byte[] bytes) {
        name = name.replace("/", ".");
        try {
            switch (type) {
                case TEXT: {
                    try {
                        LOGGER.atLevel(LOG_LEVEL).log("Dumping '{}' as text", name);
                        Path path = folder.resolve(name + ".txt");
                        try (BufferedWriter writer = Files.newBufferedWriter(IOUtils.makeParents(path))) {
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
                    LOGGER.atLevel(LOG_LEVEL).log("Dumping '{}' as binary.", name);
                    Path path = folder.resolve(name + ".class");
                    try (OutputStream os = Files.newOutputStream(IOUtils.makeParents(path))) {
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
