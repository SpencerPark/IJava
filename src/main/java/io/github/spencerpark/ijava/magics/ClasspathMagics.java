package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.util.GlobFinder;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ClasspathMagics {
    private final Consumer<String> addToClasspath;

    public ClasspathMagics(Consumer<String> addToClasspath) {
        this.addToClasspath = addToClasspath;
    }

    @LineMagic
    public List<String> jars(List<String> args) {
        List<String> jars = args.stream()
                .map(GlobFinder::new)
                .flatMap(g -> {
                    try {
                        return StreamSupport.stream(g.computeMatchingFiles().spliterator(), false);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception resolving jar glob", e);
                    }
                })
                .map(p -> p.toAbsolutePath().toString())
                .collect(Collectors.toList());

        jars.forEach(this.addToClasspath);

        return jars;
    }

    @LineMagic
    public List<String> classpath(List<String> args) {
        List<String> paths = args.stream()
                .map(GlobFinder::new)
                .flatMap(g -> {
                    try {
                        return StreamSupport.stream(g.computeMatchingPaths().spliterator(), false);
                    } catch (IOException e) {
                        throw new RuntimeException("Exception resolving jar glob", e);
                    }
                })
                .map(p -> p.toAbsolutePath().toString())
                .collect(Collectors.toList());

        paths.forEach(this.addToClasspath);

        return paths;
    }
}
