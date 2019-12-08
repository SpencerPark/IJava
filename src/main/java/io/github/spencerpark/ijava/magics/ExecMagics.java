/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Spencer Park
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

import io.github.spencerpark.jupyter.kernel.magic.registry.LineMagic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class ExecMagics {

    @LineMagic(aliases = "system")
    public void exec(List<String> args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(args);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (Scanner scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
            while (scanner.hasNext()) {
                System.out.println(scanner.nextLine());
            }
        }
    }
}
