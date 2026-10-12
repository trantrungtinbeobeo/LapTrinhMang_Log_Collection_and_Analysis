
package com.Logcollector.agent;

import java.util.ArrayDeque;
import java.util.Queue;

public class AgentLocalQueue {

    private final Queue<EventGenerator.LogEvent> queue = new ArrayDeque<>();

    // Them log vao cuoi hang doi
    public synchronized void enqueue(EventGenerator.LogEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Log event must not be null");
        }

        queue.offer(event);
    }

    // Lay va xoa log o dau hang doi
    public synchronized EventGenerator.LogEvent dequeue() {
        return queue.poll();
    }

    // Xem log dau hang doi, khong xoa
    public synchronized EventGenerator.LogEvent peek() {
        return queue.peek();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }
}
