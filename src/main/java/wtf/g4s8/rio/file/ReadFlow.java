/*
 * MIT License
 *
 * Copyright (c) 2020 g4s8
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
package wtf.g4s8.rio.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * File read flow publisher.
 * @since 0.1
 */
final class ReadFlow implements Publisher<ByteBuffer> {

    /**
     * Dummy subscription which does nothing.
     */
    private static final Subscription DUMMY = new Subscription() {
        @Override
        public void request(final long count) {
            // nothing
        }

        @Override
        public void cancel() {
            // nothing
        }
    };

    /**
     * File path.
     */
    private final Path path;

    /**
     * Buffer allocation strategy.
     */
    private final Buffers buffers;

    /**
     * Executor service for IO operations
     */
    private final ExecutorService exec;

    /**
     * Ctor.
     * @param path Path for file
     * @param buffers Buffers allocation strategy
     * @param exec Executor service for IO operations
     */
    ReadFlow(final Path path, final Buffers buffers, final ExecutorService exec) {
        this.buffers = buffers;
        this.path = path;
        this.exec = exec;
    }

    @Override
    public void subscribe(final Subscriber<? super ByteBuffer> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber can't be null");
        final FileChannel chan;
        try {
            chan = FileChannel.open(this.path, StandardOpenOption.READ);
        } catch (final IOException err) {
            subscriber.onSubscribe(DUMMY);
            subscriber.onError(err);
            return;
        }
        final Queue<ReadRequest> queue = new ConcurrentLinkedQueue<>();
        final ReadSubscriberState<? super ByteBuffer> wrap = new ReadSubscriberState<>(subscriber);
        wrap.onSubscribe(new ReadSubscription(chan, wrap, this.buffers, queue));
        this.exec.submit(
            new CloseChanOnExit(
                new ErrorOnException(
                    new ReadBusyLoop(queue, wrap, chan),
                    wrap
                ),
                chan
            )
        );
    }

    /**
     * Handle all exceptions including unchecked and signal error state to
     * subscriber.
     * @since 0.1
     */
    private static final class ErrorOnException implements Runnable {

        /**
         * Origin runnable.
         */
        private final Runnable runnable;

        /**
         * Subscriber.
         */
        private final ReadSubscriberState<?> sub;

        /**
         * Wrap runnable.
         * @param runnable Runnable to wrap
         * @param sub Subscriber
         */
        ErrorOnException(final Runnable runnable, final ReadSubscriberState<?> sub) {
            this.runnable = runnable;
            this.sub = sub;
        }

        @Override
        public void run() {
            try {
                this.runnable.run();
            } catch (final Throwable exx) {
                this.sub.onError(exx);
            }
        }
    }
}
