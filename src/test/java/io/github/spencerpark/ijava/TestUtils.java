package io.github.spencerpark.ijava;

import io.github.spencerpark.ijava.utils.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class TestUtils {
    public static List<Path> match(String glob, String location) throws IOException {
        final List<Path> matchedPath = new ArrayList<>();
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(glob);
        Files.walkFileTree(Path.of(location), new HashSet<>(2), 5, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (pathMatcher.matches(path) && Files.isReadable(path)) {
                    matchedPath.add(path);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return matchedPath;
    }

    @Test
    public void testFileGlob() throws IOException {
        String glob = "glob:**/*.zip";
        String path = "E:/flink-platform-dev";
        //path = ".";
        //glob = "glob:*";
        System.out.println(File.pathSeparator);
        //List<Path> match = match(glob, path);
        //Assert.assertNotNull(match);
    }

    @Test
    public void testReadXml() throws XMLStreamException, FileNotFoundException {
        String filePath = "D:\\Maven\\apache-maven-3.6.3\\conf\\settings.xml";
        Set<String> elementNames = Collections.singleton("localRepository");
        Map<String, String> elementTextData = FileUtils.readXmlElementText(filePath, elementNames);
        Assert.assertNotNull(elementTextData);
        for (String elementName : elementNames) {
            Assert.assertEquals(elementTextData.get(elementName), "D:\\Maven\\repository");
        }
    }
}
