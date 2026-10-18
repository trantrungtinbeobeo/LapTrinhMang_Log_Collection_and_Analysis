package com.Logcollector.server.UI.desktop;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

interface DesktopSource extends AutoCloseable {
    void start();
    void close();

    final class Demo implements DesktopSource {
        private final BiConsumer<Boolean, String> status;
        Demo(BiConsumer<Boolean, String> status) { this.status = status; }
        public void start() { status.accept(true, "DEMO CỤC BỘ — dữ liệu thử nghiệm"); }
        public void close() { }
    }

    final class Server implements DesktopSource {
        private final URI uri;
        private final Consumer<String> receive;
        private final BiConsumer<Boolean, String> status;
        private final Runnable malformed;
        private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(daemon("ui-reconnect"));
        private final ExecutorService httpThreads = Executors.newFixedThreadPool(2, daemon("ui-websocket"));
        private final HttpClient client = HttpClient.newBuilder().executor(httpThreads).connectTimeout(Duration.ofSeconds(4)).build();
        private volatile boolean closed;
        private volatile WebSocket socket;
        private volatile CompletableFuture<WebSocket> pending;
        Server(URI uri, Consumer<String> receive, BiConsumer<Boolean, String> status, Runnable malformed) {
            this.uri = uri; this.receive = receive; this.status = status; this.malformed = malformed;
        }
        public void start() { retry.execute(this::connect); }
        private void connect() {
            if (closed) return;
            status.accept(false, "Đang kết nối " + uri);
            Listener listener = new Listener();
            pending = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(4)).buildAsync(uri, listener);
            pending.whenComplete((ws, error) -> { if (error != null) listener.fail(); else if (closed) ws.abort(); });
        }
        private final class Listener implements WebSocket.Listener {
            private final AtomicBoolean ended = new AtomicBoolean();
            private final StringBuilder message = new StringBuilder();
            private boolean oversized;
            public void onOpen(WebSocket ws) {
                if (closed) { ws.abort(); return; }
                socket = ws; status.accept(true, "Đã kết nối " + uri); ws.request(1);
            }
            public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
                if (closed || ended.get()) return null;
                if (!oversized && message.length() + text.length() <= 262144) message.append(text);
                else oversized = true;
                if (last) {
                    if (oversized) malformed.run(); else receive.accept(message.toString());
                    message.setLength(0); oversized = false;
                }
                ws.request(1); return null;
            }
            public CompletionStage<?> onClose(WebSocket ws, int code, String reason) { fail(); return null; }
            public void onError(WebSocket ws, Throwable error) { ws.abort(); fail(); }
            void fail() {
                if (!ended.compareAndSet(false, true) || closed) return;
                status.accept(false, "Mất kết nối / chưa có Server — thử lại sau 3 giây");
                try { retry.schedule(Server.this::connect, 3, TimeUnit.SECONDS); }
                catch (RejectedExecutionException ignored) { }
            }
        }
        public void close() {
            closed = true;
            if (pending != null) pending.cancel(true);
            if (socket != null) socket.abort();
            retry.shutdownNow(); httpThreads.shutdownNow();
        }
    }
    static ThreadFactory daemon(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }
}
