package io.github.spencerpark;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Supplier;

public class LazyOutputStreamDelegate extends OutputStream {
    private final Supplier<OutputStream> writeTo;

    public LazyOutputStreamDelegate(Supplier<OutputStream> writeTo) {
        this.writeTo = writeTo;
    }

    @Override
    public void write(int b) throws IOException {
        this.writeTo.get().write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.writeTo.get().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.writeTo.get().write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        this.writeTo.get().flush();
    }
}
