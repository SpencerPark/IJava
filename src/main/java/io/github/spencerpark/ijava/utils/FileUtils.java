package io.github.spencerpark.ijava.utils;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileUtils {
    private FileUtils() {
        // hide
    }

    public static Map<String, String> readXmlElementText(String filePath, Collection<String> elementNames) throws FileNotFoundException, XMLStreamException {
        Map<String, String> result = new HashMap<>();
        XMLInputFactory xmlInputFactory = XMLInputFactory.newDefaultFactory();
        XMLEventReader reader = xmlInputFactory.createXMLEventReader(new FileInputStream(filePath));

        while (reader.hasNext()) {
            XMLEvent nextEvent = reader.nextEvent();

            if (nextEvent.isStartElement()) {
                StartElement startElement = nextEvent.asStartElement();
                String elementName = startElement.getName().getLocalPart();
                if (elementNames.contains(elementName)) {
                    // if startElement, then next to textData
                    nextEvent = reader.nextEvent();

                    result.put(elementName, nextEvent.asCharacters().getData());
                }
            }
        }

        return result;
    }

    public static Collection<Path> listMatchedFilePath(String glob, String location) throws IOException {
        // String startFolder = new File(FileUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile().getPath();
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
}
