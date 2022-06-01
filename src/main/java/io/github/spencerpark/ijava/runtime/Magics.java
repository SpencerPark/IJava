/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 ${author}
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.runtime;

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.magic.registry.UndefinedMagicException;

import java.util.List;

public class Magics {
    public static <T> T lineMagic(String name, List<String> args) {
        JavaKernel kernel = IJava.getKernelInstance();

        if (kernel != null) {
            try {
                return kernel.getMagics().applyLineMagic(name, args);
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
                return kernel.getMagics().applyCellMagic(name, args, body);
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
