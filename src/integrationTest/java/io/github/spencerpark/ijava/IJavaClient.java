package io.github.spencerpark.ijava;

import io.github.spencerpark.jupyter.api.KernelConnectionProperties;
import io.github.spencerpark.jupyter.client.ZmqJupyterClient;
import io.github.spencerpark.jupyter.client.api.JupyterClient;
import io.github.spencerpark.jupyter.client.system.JupyterPaths;
import io.github.spencerpark.jupyter.client.system.KernelLauncher;
import io.github.spencerpark.jupyter.client.system.KernelSpec;
import io.github.spencerpark.jupyter.client.system.KernelSpecManager;
import junit.framework.AssertionFailedError;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class IJavaClient implements TestRule {
    private final TemporaryFolder kernelInstallDir = new TemporaryFolder();

    private final Path kernelDistPath;
    private final Path javaHome;

    private KernelSpec installedSpec;
    private Process kernelProcess;
    private ZmqJupyterClient client;

    public IJavaClient() {
        this(Paths.get(System.getProperty("ijava.distPath")),
                Paths.get(System.getProperty("ijava.javaHome")));
    }

    public IJavaClient(Path kernelDistPath, Path javaHome) {
        this.kernelDistPath = kernelDistPath;
        this.javaHome = javaHome;
    }

    public JupyterClient get() {
        return this.client;
    }

    private void stageKernel() throws IOException {
        Path kernelInstallPath = this.kernelInstallDir.getRoot().toPath();

        try (ZipFile kernelDist = new ZipFile(this.kernelDistPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = kernelDist.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path targetPath = kernelInstallPath.resolve(e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(kernelDist.getInputStream(e), targetPath);
                }
            }
        }

        KernelSpecManager specManager = new KernelSpecManager(List.of(kernelInstallPath));
        KernelSpec specTemplate = specManager.getKernelSpec("java").orElseThrow(AssertionFailedError::new);

        List<String> argv = specTemplate.getArgv().stream()
                .map(arg -> "java".equalsIgnoreCase(arg)
                        ? this.javaHome.resolve("bin").resolve("java").toAbsolutePath().toString()
                        : arg
                )
                .map(arg -> arg.replace(
                        "@KERNEL_INSTALL_DIRECTORY@",
                        specTemplate.getResourceDirectory().toAbsolutePath().toString()
                ))
                .collect(Collectors.toList());
        Map<String, String> env = new LinkedHashMap<>(specTemplate.getEnv());
        env.put("JAVA_HOME", javaHome.toAbsolutePath().toString());

        this.installedSpec = new KernelSpec(
                argv,
                specTemplate.getDisplayName(),
                specTemplate.getLanguage(),
                specTemplate.getInterruptMode(),
                env,
                specTemplate.getMetadata(),
                specTemplate.getResourceDirectory()
        );
    }

    private void startKernel() throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        JupyterPaths paths = JupyterPaths.empty();
        KernelConnectionProperties connProps = KernelLauncher.createLocalTcpProps();

        Path workingDir = this.kernelInstallDir.newFolder("working-dir").toPath();
        this.kernelProcess = KernelLauncher.prepare(paths, connProps, this.installedSpec, workingDir)
                .redirectError(this.kernelInstallDir.newFile("kernel.stderr.txt"))
                .redirectInput(this.kernelInstallDir.newFile("kernel.stdout.txt"))
                .start();

        this.client = ZmqJupyterClient.createConnectedTo(connProps);
    }

    private void shutdownKernel() {
        this.client.shutdown();
        this.kernelProcess.destroyForcibly();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return kernelInstallDir.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                stageKernel();
                startKernel();
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    System.out.println("kernel stderr:");
                    System.out.write(Files.readAllBytes(kernelInstallDir.getRoot().toPath().resolve("kernel.stderr.txt")));
                    System.out.println("kernel stdout:");
                    System.out.write(Files.readAllBytes(kernelInstallDir.getRoot().toPath().resolve("kernel.stdout.txt")));
                } finally {
                    shutdownKernel();
                }
            }
        }, description);
    }
}
