package com.mohan.netsaver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TeeInputStream: reads from source, simultaneously writes every byte to sink.
 * This is the core of zero-double-bandwidth architecture.
 * Single network stream → WebView AND file simultaneously.
 */
public class TeeInputStream extends InputStream {

    private final InputStream source;
    private final OutputStream sink;
    private boolean closed = false;

    public TeeInputStream(InputStream source, OutputStream sink) {
        this.source = source;
        this.sink = sink;
    }

    @Override
    public int read() throws IOException {
        int b = source.read();
        if (b != -1) {
            sink.write(b);
        } else {
            closeSink();
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = source.read(buf, off, len);
        if (n > 0) {
            sink.write(buf, off, n);
        } else if (n == -1) {
            closeSink();
        }
        return n;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    private void closeSink() {
        if (!closed) {
            closed = true;
            try {
                sink.flush();
                sink.close();
            } catch (IOException ignored) {}
        }
    }

    @Override
    public void close() throws IOException {
        closeSink();
        source.close();
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }
}
