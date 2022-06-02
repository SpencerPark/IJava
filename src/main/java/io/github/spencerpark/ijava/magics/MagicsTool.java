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
package io.github.spencerpark.ijava.magics;

import io.github.spencerpark.ijava.JavaKernel;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;
import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagicFunction;
import io.github.spencerpark.jupyter.kernel.magic.registry.Magics;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MagicsTool {

    @LineMagic
    public void listLineMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered line magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "lineMagics")));
        } catch (Exception e) {
            System.out.printf("inspect line magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic
    public void listCellMagic(List<String> args) {
        Magics magics = JavaKernel.getMagics();
        try {
            System.out.printf("registered cell magics: %n\t- %s%n",
                    String.join("\n\t- ", getMagicsName(magics, "cellMagics")));
        } catch (Exception e) {
            System.out.printf("inspect cell magics fail: %s%n", e.getMessage());
        }
    }

    @LineMagic(aliases = {"list"})
    public void listMagic(List<String> args) {
        listLineMagic(Collections.emptyList());
        listCellMagic(Collections.emptyList());
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getMagicsName(Magics magics, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Field field = magics.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Map<String, LineMagicFunction<?>> lineMagics = (Map<String, LineMagicFunction<?>>) field.get(magics);
        return lineMagics.entrySet()
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.joining(", "))))
                .values();
    }
}
