package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.api.display.DisplayData;

import java.util.UUID;

public class Display {
    public static DisplayData render(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.renderer().render(o);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static DisplayData render(Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            return kernel.renderer().renderAs(o, as);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static String display(Object o) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.renderer().render(o);

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
            DisplayData data = kernel.renderer().renderAs(o, as);

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
            DisplayData data = kernel.renderer().render(o);
            kernel.io().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static void updateDisplay(String id, Object o, String... as) {
        JavaKernel kernel = Kernel.getKernelInstance();

        if (kernel != null) {
            DisplayData data = kernel.renderer().renderAs(o, as);
            kernel.io().display.updateDisplay(id, data);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }
}
