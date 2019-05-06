package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;

public class Kernel {
    public static JavaKernel getKernelInstance() {
        return IJava.getKernelInstance();
    }

    public static Object eval(String expr) throws Exception {
        JavaKernel kernel = getKernelInstance();

        if (kernel != null) {
            return kernel.evalRaw(expr);
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }
}
