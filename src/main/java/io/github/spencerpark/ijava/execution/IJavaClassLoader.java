package io.github.spencerpark.ijava.execution;

import io.github.spencerpark.jupyter.kernel.extension.ContainerClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.*;

final class IJavaClassLoader extends ContainerClassLoader {
    private static String classNameToResourcePath(String className) {
        return className.replace('.', '/') + ".class";
    }

    private final BytesURLContext classes;
    private final Set<ServiceLoader<?>> registeredDynamicLoaders = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    IJavaClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        this.classes = new BytesURLContext();
    }

    /**
     * Load services found from this class loader which is automatically {@link ServiceLoader#reload() reloaded} when
     * this class loader's class path changes.
     */
    public <S> ServiceLoader<? extends S> loadDynamicServices(Class<? extends S> service) {
        ServiceLoader<? extends S> loader = ServiceLoader.load(service, this);
        this.registeredDynamicLoaders.add(loader);
        return loader;
    }

    public void addToClasspath(String path) {
        for (String entry : path.split(File.pathSeparator)) {
            try {
                this.addURL(Paths.get(entry).toUri().toURL());
            } catch (MalformedURLException ignored) { }
        }
    }

    void declare(String className, byte[] code) {
        this.classes.define(classNameToResourcePath(className), code);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        BytesURLContext.Resource resource = this.classes.findResource(classNameToResourcePath(name));
        if (resource == null) {
            return super.findClass(name);
        }

        byte[] bytecode = resource.getData();
        return super.defineClass(name, bytecode, 0, bytecode.length, (CodeSource) null);
    }

    @Override
    public URL findResource(String path) {
        URL blobUrl = this.classes.lookupResourceLocation(path);
        return blobUrl != null ? blobUrl : super.findResource(path);
    }

    @Override
    public Enumeration<URL> findResources(String path) throws IOException {
        URL blobUrl = this.classes.lookupResourceLocation(path);
        Enumeration<URL> resources = super.findResources(path);
        if (blobUrl == null) {
            return resources;
        }

        return new Enumeration<>() {
            boolean consumedBlobUrl = false;

            @Override
            public boolean hasMoreElements() {
                return !this.consumedBlobUrl || resources.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                if (this.consumedBlobUrl) {
                    return resources.nextElement();
                } else {
                    this.consumedBlobUrl = true;
                    return blobUrl;
                }
            }
        };
    }
}
