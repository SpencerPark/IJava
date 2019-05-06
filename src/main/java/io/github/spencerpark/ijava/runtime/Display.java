package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;

import java.util.UUID;

public class Display {
    public static DisplayData render(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.getRenderer().render(o);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static DisplayData render(Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.getRenderer().renderAs(o, as);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static String display(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().render(o);

            String id = data.getDisplayId();
            if (id == null) {
                id = UUID.randomUUID().toString();
                data.setDisplayId(id);
            }

            kernel.display(data);

            return id;
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static String display(Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().renderAs(o, as);

            String id = data.getDisplayId();
            if (id == null) {
                id = UUID.randomUUID().toString();
                data.setDisplayId(id);
            }

            kernel.display(data);

            return id;
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static void updateDisplay(String id, Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().render(o);
            kernel.getIO().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static void updateDisplay(String id, Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.getRenderer().renderAs(o, as);
            kernel.getIO().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }
}
