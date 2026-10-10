package com.Logcollector.server.Core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogQueue {
    // Giới hạn 100,000 log trong RAM để tránh tràn bộ nhớ khi tải cao
    private static final int CAPACITY = 100000;
    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>(CAPACITY);

    public static boolean offer(String rawLog) {
        return queue.offer(rawLog);
    }

    public static String take() throws InterruptedException {
        return queue.take();
    }

    public static String poll() {
        return queue.poll();
    }

    public static int size() {
        return queue.size();
    }
}