package com.mohan.netsaver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * TeeInputStream — zero-double-bandwidth core.
 *
 * Every byte read by the WebView is simultaneously written to the
 * file output stream. One network request, two destinations.
 *
 * Thread-safety: read() and close() may be called from WebView's
 * network thread; we guard the sink-close path with a volatile flag.
 */
public class TeeInputStream extends InputStream {

    private final InputStream  source;
    private final OutputStream sink;
    private volatile boolean   sinkClosed = false;

    public TeeInputStream(InputStream source, OutputStream sink) {
        this.source = source;
        this.sink   = sink;
    }

    @Override
    public int read() throws IOException {
        int b = source.read();
        if (b != -1) {
            safeSinkWrite(b);
        } else {
            closeSink();
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = source.read(buf, off, len);
        if (n > 0) {
            safeSinkWrite(buf, off, n);
        } else if (n == -1) {
            closeSink();
        }
        return n;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    @Override
    public int available() throws IOException {
        return source.available();
    }

    @Override
    public void close() throws IOException {
        try {
            source.close();
        } finally {
            closeSink();
        }
    }

    // ── helpers ──────────────────────────────────────────────

    private void safeSinkWrite(int b) {
        if (sinkClosed) return;
        try { sink.write(b); } catch (IOException ignored) {}
    }

    private void safeSinkWrite(byte[] buf, int off, int n) {
        if (sinkClosed) return;
        try { sink.write(buf, off, n); } catch (IOException ignored) {}
    }

    private void closeSink() {
        if (sinkClosed) return;
        sinkClosed = true;
        try { sink.flush(); } catch (IOException ignored) {}
        try { sink.close(); } catch (IOException ignored) {}
    }
}
