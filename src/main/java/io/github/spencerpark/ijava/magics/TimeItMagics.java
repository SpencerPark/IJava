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

import io.github.spencerpark.ijava.IJava;
import io.github.spencerpark.jupyter.kernel.magic.registry.CellMagic;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class TimeItMagics {
    private final int epochs = 3;
    private final int loops = 5;

    @CellMagic(aliases = {"time", "timeit"})
    public void timeIt(List<String> args, String body) throws Exception {
        if (args == null) args = Collections.emptyList();

        if (!args.isEmpty() && ("-h".equals(args.get(0)) || "--help".equals(args.get(0)))) {
            System.out.println("help: \nexample: \n");
            System.out.println("%%time epochs=3 loops=5\n1 + 1");
            return;
        }

        // parse input args
        Map<String, Integer> params = args.stream()
                .map(arg -> arg.split("="))
                .filter(kv -> kv.length > 0 && StringUtils.isNotEmpty(kv[0]) && StringUtils.isNotEmpty(kv[1]) && kv[1].matches("\\d+"))
                .collect(Collectors.toMap(kv -> kv[0], kv -> Integer.parseInt(kv[1])));

        // for each epoch
        Integer epochNum = params.getOrDefault("epochs", epochs);
        Integer loopNum = params.getOrDefault("loops", loops);
        List<List<Long>> epochData = new ArrayList<>(epochNum);
        for (int i = 0; i < epochNum; i++) {
            // for each loop
            List<Long> loopData = new ArrayList<>(loopNum);
            for (int j = 0; j < loopNum; j++) {
                loopData.add(System.currentTimeMillis());
                IJava.getKernelInstance().evalRaw(body);
                loopData.add(System.currentTimeMillis());
            }
            epochData.add(loopData);
        }

        // Summary Statistics
        List<List<Long>> epochDiff = new ArrayList<>(epochData.size());
        for (int i = 0; i < epochData.size(); i++) {
            List<Long> loopData = epochData.get(i);
            List<Long> diff = new ArrayList<>(loopData.size() / 2);
            for (int j = 0; j < loopData.size() / 2; j++) {
                diff.add(loopData.get(i * 2 + 1) - loopData.get(i * 2));
            }
            LongSummaryStatistics statistics = diff.stream().collect(Collectors.summarizingLong(o -> o));
            System.out.printf("epoch %d: %s%n", i, statistics);
            epochDiff.add(diff);
        }
        System.out.printf("total: %s%n", epochDiff.stream().flatMap(Collection::stream).collect(Collectors.summarizingLong(o -> o)));
    }
}
