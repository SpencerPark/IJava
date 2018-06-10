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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

public class LazyInputStreamDelegate extends InputStream {
    private final Supplier<InputStream> readFrom;

    public LazyInputStreamDelegate(Supplier<InputStream> readFrom) {
        this.readFrom = readFrom;
    }

    @Override
    public int read() throws IOException {
        return this.readFrom.get().read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return this.readFrom.get().read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return this.readFrom.get().read(b, off, len);
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return this.readFrom.get().readAllBytes();
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return this.readFrom.get().readNBytes(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return this.readFrom.get().skip(n);
    }

    @Override
    public int available() throws IOException {
        return this.readFrom.get().available();
    }

    @Override
    public void close() throws IOException {
        this.readFrom.get().close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        this.readFrom.get().mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        this.readFrom.get().reset();
    }

    @Override
    public boolean markSupported() {
        return this.readFrom.get().markSupported();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return this.readFrom.get().transferTo(out);
    }
}
