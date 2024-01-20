package codechicken.mixin.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Basically a re-implementation of ServiceLoader that does things a little differently.
 * <p>
 * Services are only class loaded and not instantiated, useful for some applications.
 * {@link #poll()} must be called to actually load stuff, new services found directly
 * after that poll operation can be obtained via {@link #getNewServices()}, this set
 * is cleared each time {@link #poll()} is called. All found services can be retrieved
 * via {@link #getAllServices()}.
 * <p>
 * <p>
 * Created by covers1624 on 15/11/18.
 */
public class SimpleServiceLoader<S> {

    private static final String PREFIX = "META-INF/services/";

    private final Class<?> serviceClazz;
    private final ClassLoader classLoader;

    private final Set<String> foundClasses = new HashSet<>();
    private final Set<Class<? extends S>> foundServices = new HashSet<>();
    private final Set<Class<? extends S>> newServices = new HashSet<>();

    public SimpleServiceLoader(Class<S> serviceClazz) {
        this.serviceClazz = serviceClazz;
        this.classLoader = getContextClassLoader();
    }

    public SimpleServiceLoader(Class<S> serviceClazz, ClassLoader classLoader) {
        this.serviceClazz = serviceClazz;
        this.classLoader = classLoader;
    }

    @SuppressWarnings ("unchecked")
    public SimpleServiceLoader<S> poll() {
        newServices.clear();
        load(classLoader, serviceClazz, line -> {
            if (!foundClasses.contains(line)) {
                foundClasses.add(line);
                Class<S> clazz = null;
                try {
                    clazz = (Class<S>) Class.forName(line, false, classLoader);
                } catch (ClassNotFoundException e) {
                    fail(serviceClazz, "Provider " + line + " not found.");
                }
                if (!serviceClazz.isAssignableFrom(clazz)) {
                    fail(serviceClazz, "Provider " + line + " not a subtype");
                }
                foundServices.add(clazz);
                newServices.add(clazz);
            }
        });
        return this;
    }

    public Set<Class<? extends S>> getAllServices() {
        return Collections.unmodifiableSet(foundServices);
    }

    public Set<Class<? extends S>> getNewServices() {
        return Collections.unmodifiableSet(newServices);
    }

    public static void load(Class<?> clazz, Consumer<String> cons) {
        load(getContextClassLoader(), clazz, cons);
    }

    public static void load(ClassLoader cl, Class<?> clazz, Consumer<String> cons) {
        try {
            for (URL url : Utils.toIterable(cl.getResources(PREFIX + clazz.getName()))) {
                try (LineNumberReader reader = new LineNumberReader(new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int lc = reader.getLineNumber();
                        int cIndex = line.indexOf('#');
                        if (cIndex >= 0) {
                            line = line.substring(0, cIndex);
                        }
                        line = line.trim();
                        int n = line.length();
                        if (n != 0) {
                            if (line.indexOf(' ') >= 0 || line.indexOf('\t') >= 0) {
                                fail(clazz, url, lc, "Illegal configuration-file syntax");
                            }
                            int cp = line.codePointAt(0);
                            if (!Character.isJavaIdentifierStart(cp)) {
                                fail(clazz, url, lc, "Illegal provider-class name: " + line);
                            }
                            for (int i = Character.charCount(cp); i < n; i += Character.charCount(cp)) {
                                cp = line.codePointAt(i);
                                if (!Character.isJavaIdentifierPart(cp) && cp != '.') {
                                    fail(clazz, url, lc, "Illegal provider-class name: " + line);
                                }
                            }
                            cons.accept(line);
                        }
                    }
                }
            }

        } catch (IOException e) {
            fail(clazz, "Error reading configuration file", e);
        }
    }

    private static ClassLoader getContextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = SimpleServiceLoader.class.getClassLoader();
        }
        return cl;
    }

    private static void fail(Class<?> service, String msg, Throwable cause) throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg, cause);
    }

    private static void fail(Class<?> service, String msg) throws ServiceConfigurationError {
        throw new ServiceConfigurationError(service.getName() + ": " + msg);
    }

    private static void fail(Class<?> service, URL u, int line, String msg) throws ServiceConfigurationError {
        fail(service, u + ":" + line + ": " + msg);
    }

}
