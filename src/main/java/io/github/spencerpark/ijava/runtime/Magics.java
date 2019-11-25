package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.magic.registry.UndefinedMagicException;

import java.util.ArrayList;
import java.util.List;

public class Magics {
    public static <T> T lineMagic(String name, List<String> args) {
        JavaKernel kernel = IJava.getKernelInstance();

        if (kernel != null) {
            try {
                List<String> realArgs = new ArrayList<>(args.size());
                for (String arg : args) {
                    realArgs.add(kernel.evalRaw(interpolate(arg)).toString());
                }
                return kernel.getMagics().applyLineMagic(name, realArgs);
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
                List<String> realArgs = new ArrayList<>(args.size());
                for (String arg : args) {
                    realArgs.add(kernel.evalRaw(interpolate(arg)).toString());
                }
                String realBody = kernel.evalRaw(interpolate(body)).toString();
                return kernel.getMagics().applyCellMagic(name, realArgs, realBody);
            } catch (UndefinedMagicException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(String.format("Exception occurred while running cell magic '%s': %s", name, e.getMessage()), e);
            }
        } else {
            throw new RuntimeException("No IJava kernel running");
        }
    }

    private static String interpolate(String input) {
        List<Interpolation.Expression> expressions = Interpolation.parse(input);
        StringBuilder sb = new StringBuilder();
        sb.append("String.format(\"");
        StringBuilder parameters = new StringBuilder();
        for (Interpolation.Expression expression : expressions) {
            if (expression.isConstant()) {
                sb.append(expression.getExpression().replace("%", "%%").replace("\n", "\\n").replace("\r", "\\r"));
            } else {
                if (parameters.length() == 0) {
                    parameters.append(", ");
                }
                parameters.append("String.valueOf(").append(expression.getExpression()).append(')');
                sb.append("%s");
            }
        }
        sb.append('"');
        sb.append(parameters).append(");");
        return sb.toString();
    }
}
