/*
 * MIT License
 *
 * Copyright (c) 2020 cqfn.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights * to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.cqfn.rio.stream;

import io.reactivex.Flowable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.cqfn.rio.WriteGreed;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link ReactiveOutputStream}.
 * @since 0.4
 * @checkstyle MagicNumberCheck (500 lines)
 */
class ReactiveOutputStreamTest {

    @Test
    void transferDataToStream() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] data = "123".getBytes();
        new ReactiveOutputStream(out)
            .write(Flowable.fromArray(ByteBuffer.wrap(data)), WriteGreed.SYSTEM)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            out.toByteArray(),
            new IsEqual<>(data)
        );
    }

    @Test
    @SuppressWarnings("PMD.AssignmentInOperand")
    void transferDataToPipedStream() throws IOException {
        try (
            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in)
        ) {
            final byte[] data = this.getBinFile();
            final byte[] res = new byte[data.length];
            final CompletableFuture<Void> future = new ReactiveOutputStream(out)
                .write(Flowable.fromArray(ByteBuffer.wrap(data)), WriteGreed.SYSTEM)
                .toCompletableFuture();
            int offset = 0;
            int read;
            while ((read = in.read(res, offset, res.length - offset)) > 0) {
                offset += read;
            }
            future.join();
            MatcherAssert.assertThat(
                res,
                new IsEqual<>(data)
            );
        }
    }

    @Test
    void transferResourceToStream() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] data = this.getBinFile();
        new ReactiveOutputStream(out)
            .write(Flowable.fromArray(ByteBuffer.wrap(data)), WriteGreed.SYSTEM)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            out.toByteArray(),
            new IsEqual<>(data)
        );
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private byte[] getBinFile() {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (
            InputStream stream = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream("file.bin")
            )
        ) {
            int count;
            final byte[] data = new byte[8 * 1024];
            while ((count = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, count);
            }
        } catch (final IOException err) {
            throw new UncheckedIOException(err);
        }
        return buffer.toByteArray();
    }
}