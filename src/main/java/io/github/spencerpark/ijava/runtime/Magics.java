package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.api.magic.registry.UndefinedMagicException;

import java.util.List;

public class Magics {
    public static <T> T lineMagic(String name, List<String> args) {
        JavaKernel kernel = IJava.getKernelInstance();

        if (kernel != null) {
            try {
                return kernel.magics().applyLineMagic(name, args);
            } catch (UndefinedMagicException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(String.format("Exception occurred while running line magic '%s': %s", name, e.getMessage()), e);
            }
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    public static <T> T cellMagic(String name, List<String> args, String body) {
        JavaKernel kernel = IJava.getKernelInstance();

        if (kernel != null) {
            try {
                return kernel.magics().applyCellMagic(name, args, body);
            } catch (UndefinedMagicException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(String.format("Exception occurred while running cell magic '%s': %s", name, e.getMessage()), e);
            }
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }
}
