package io.github.spencerpark.ijava;

import io.github.spencerpark.ijava.utils.FileUtils;
import io.github.spencerpark.ijava.utils.RuntimeCompiler;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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

    //@Test
    public void testCompile() {
        String name = "vo.Cat";
        String clzDef = """
                package vo;
                                
                //import lombok.Data;
                                
                //@Data
                public class Cat {
                    private String name;
                    private Integer age;
                }
                """;
        Class<?> clz = RuntimeCompiler.compile(name, clzDef, true);
        List<String> methods = Arrays.stream(clz.getDeclaredMethods())
                .map(method -> method.getName() + "(" + Arrays.stream(method.getGenericParameterTypes())
                        .map(type -> type.getTypeName().substring(type.getTypeName().lastIndexOf('.') + 1))
                        .collect(Collectors.joining(",")) + ")").toList();

        System.out.printf("compile done, clz: %s, clz's declared methods: %s%n", clz, methods);
    }

    //@Test
    public void testProcess() throws IOException {
        String[] commands = {"ping", "localhost"};
        Process proc = Runtime.getRuntime().exec(commands);

        String s = null;
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getInputStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.out.println(s);
            }
        }
        try (InputStreamReader inputStreamReader = new InputStreamReader(proc.getErrorStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            while ((s = bufferedReader.readLine()) != null) {
                System.out.println(s);
            }
        }
    }
}
