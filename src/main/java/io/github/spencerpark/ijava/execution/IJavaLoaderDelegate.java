package io.github.spencerpark.ijava.execution;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IJavaLoaderDelegate implements LoaderDelegate {
    private final IJavaClassLoader loader;
    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

    public IJavaLoaderDelegate(IJavaClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public void load(ExecutionControl.ClassBytecodes[] cbcs) throws ExecutionControl.ClassInstallException, ExecutionControl.NotImplementedException, ExecutionControl.EngineTerminationException {
        boolean[] loaded = new boolean[cbcs.length];
        try {
            this.classesRedefined(cbcs);

            for (int i = 0; i < cbcs.length; i++) {
                ExecutionControl.ClassBytecodes cbc = cbcs[i];

                Class<?> clazz = this.loader.loadClass(cbc.name());
                this.loadedClasses.put(cbc.name(), clazz);
                loaded[i] = true;

                // TODO why is eagerly evaluating this a good thing?
                clazz.getDeclaredMethods();
            }
        } catch (Throwable t) {
            throw new ExecutionControl.ClassInstallException(t.getMessage(), loaded);
        }
    }

    @Override
    public void classesRedefined(ExecutionControl.ClassBytecodes[] cbcs) {
        for (ExecutionControl.ClassBytecodes cbc : cbcs) {
            this.loader.declare(cbc.name(), cbc.bytecodes());
        }
    }

    @Override
    public void addToClasspath(String path) throws ExecutionControl.InternalException {
        try {
            this.loader.addToClasspath(path);
        } catch (Exception ex) {
            throw new ExecutionControl.InternalException(ex.toString());
        }
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = this.loadedClasses.get(name);
        if (clazz != null) {
            return clazz;
        }

        throw new ClassNotFoundException(name);
    }
}
