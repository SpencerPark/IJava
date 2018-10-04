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
package io.github.spencerpark.ijava.execution;

import io.github.spencerpark.jupyter.kernel.magic.CellMagicParseContext;
import io.github.spencerpark.jupyter.kernel.magic.LineMagicParseContext;
import io.github.spencerpark.jupyter.kernel.magic.MagicParser;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MagicsSourceTransformer {
    private static final Pattern UNESCAPED_QUOTE = Pattern.compile("(?<!\\\\)\"");

    private final MagicParser parser;

    public MagicsSourceTransformer() {
        this.parser = new MagicParser("(?<=(?:^|=))\\s*%", "%%");
    }

    public String transformMagics(String source) {
        CellMagicParseContext ctx = this.parser.parseCellMagic(source);
        if (ctx != null)
            return this.transformCellMagic(ctx);

        return transformLineMagics(source);
    }

    public String transformLineMagics(String source) {
        return this.parser.transformLineMagics(source, ctx -> {
            boolean inString = false;
            Matcher m = UNESCAPED_QUOTE.matcher(ctx.getLinePrefix());
            while (m.find())
                inString = !inString;

            // If in a string literal, don't apply the magic, just use the original
            if (inString)
                return ctx.getRaw();

            return transformLineMagic(ctx);
        });
    }

    // Poor mans string escape
    private String b64Transform(String arg) {
        String encoded = Base64.getEncoder().encodeToString(arg.getBytes());

        return String.format("new String(Base64.getDecoder().decode(\"%s\"))", encoded);
    }

    private String transformLineMagic(LineMagicParseContext ctx) {
        return String.format(
                "lineMagic(%s,List.of(%s));{};",
                this.b64Transform(ctx.getMagicCall().getName()),
                ctx.getMagicCall().getArgs().stream()
                        .map(this::b64Transform)
                        .collect(Collectors.joining(","))
        );
    }

    private String transformCellMagic(CellMagicParseContext ctx) {
        return String.format(
                "cellMagic(%s,List.of(%s),%s);{};",
                this.b64Transform(ctx.getMagicCall().getName()),
                ctx.getMagicCall().getArgs().stream()
                        .map(this::b64Transform)
                        .collect(Collectors.joining(",")),
                this.b64Transform(ctx.getMagicCall().getBody())
        );
    }
}
