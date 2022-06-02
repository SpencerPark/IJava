package io.github.spencerpark.ijava;

import io.github.spencerpark.ijava.utils.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TestUtils {
    @Test
    public void testFileGlob() {
        String glob = "glob:**/*.zip";
        String path = "E:/flink-platform-dev";
        //path = ".";
        //glob = "glob:*";
        //System.out.println(File.pathSeparator);
        //List<Path> match = match(glob, path);
        //Assert.assertNotNull(match);
    }

    @Test
    public void testReadXml() {
        try {
            Path filePath = Path.of("D:\\Maven\\apache-maven-3.6.3\\conf\\settings.xml");
            Set<String> elementNames = Collections.singleton("localRepository");
            Map<String, String> elementTextData = FileUtils.readXmlElementText(filePath, elementNames);
            Assert.assertNotNull(elementTextData);
            for (String elementName : elementNames) {
                Assert.assertEquals(elementTextData.get(elementName), "D:\\Maven\\repository");
            }
        } catch (Exception e) {
            // pass
            System.out.println(e.getMessage());
        }
    }
}
