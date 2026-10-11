package com.pijamaparty.store.platform.web.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Lets a {@link RateLimitKeyResolver} read a small body before the controller does, for example the email of a
 * sign-in (RNF-29.5). The bytes read are replayed to the rest of the chain, followed by whatever was not read, so the
 * controller sees the request unchanged. Nothing is buffered unless a resolver asks. Only for bodies the controller
 * reads as a stream (JSON): Tomcat parses form parameters from the original stream.
 */
public final class PeekableRequest extends HttpServletRequestWrapper {

    /** Larger bodies are never buffered; a sign-in body is far below this. */
    public static final int MAX_PEEK_BYTES = 8 * 1024;

    private byte[] peeked;
    private boolean complete;
    private boolean streamTaken;

    PeekableRequest(HttpServletRequest request) {
        super(request);
    }

    /**
     * The whole body, when the request went through the rate-limit filter and its body has at most
     * {@link #MAX_PEEK_BYTES}; empty otherwise.
     */
    public static Optional<byte[]> body(HttpServletRequest request) throws IOException {
        return request instanceof PeekableRequest peekable ? peekable.peek() : Optional.empty();
    }

    private Optional<byte[]> peek() throws IOException {
        if (peeked == null) {
            if (streamTaken) {
                return Optional.empty();
            }
            byte[] read = super.getInputStream().readNBytes(MAX_PEEK_BYTES + 1);
            complete = read.length <= MAX_PEEK_BYTES;
            peeked = read;
        }
        return complete ? Optional.of(peeked.clone()) : Optional.empty();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        streamTaken = true;
        ServletInputStream rest = super.getInputStream();
        return peeked == null ? rest : new ReplayingInputStream(peeked, rest);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (peeked == null) {
            streamTaken = true;
            return super.getReader();
        }
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /** The peeked bytes first, then the rest of the original stream. Blocking only, as Spring MVC reads it. */
    private static final class ReplayingInputStream extends ServletInputStream {

        private final byte[] head;
        private final ServletInputStream rest;
        private int position;

        ReplayingInputStream(byte[] head, ServletInputStream rest) {
            this.head = head;
            this.rest = rest;
        }

        @Override
        public int read() throws IOException {
            return position < head.length ? head[position++] & 0xFF : rest.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position < head.length) {
                int count = Math.min(length, head.length - position);
                System.arraycopy(head, position, buffer, offset, count);
                position += count;
                return count;
            }
            return rest.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
            return position >= head.length && rest.isFinished();
        }

        @Override
        public boolean isReady() {
            return position < head.length || rest.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("A peeked body is read in blocking mode only");
        }
    }
}
