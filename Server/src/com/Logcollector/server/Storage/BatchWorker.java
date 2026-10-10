package com.Logcollector.server.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import com.Logcollector.server.Parser.LogEvent;

public class BatchWorker implements Runnable {
    private final BlockingQueue<LogEvent> queue;
    private final int batchSize;
    private volatile boolean isRunning = true;

    public BatchWorker(BlockingQueue<LogEvent> queue, int batchSize) {
        this.queue = queue;
        this.batchSize = batchSize;
    }

    public void stopWorker() {
        isRunning = false;
    }

    @Override
    public void run() {
        System.out.println("[Worker] Thread da khoi dong, dang cho du lieu tu Queue...");
        List<LogEvent> batch = new ArrayList<>();

        while (isRunning) {
            try {
                // Rút log từ hàng đợi (nếu rỗng sẽ block/ngủ chờ ở đây)
                LogEvent event = queue.take();
                batch.add(event);

                // Rút thêm các log đang có sẵn trong Queue để gom nhanh thành lô
                queue.drainTo(batch, batchSize - 1);

                // Nếu gom đủ số lượng lô (batchSize) thì thực hiện Batch Insert
                if (batch.size() >= batchSize || queue.isEmpty()) {
                    DatabaseManager.insertLogBatch(batch);
                    batch.clear(); // Xóa lô cũ, chuẩn bị gom lô mới
                }

            } catch (InterruptedException e) {
                System.out.println("[Worker] Thread bi ngat.");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[Worker] Loi xu ly lô: " + e.getMessage());
            }
        }
    }

    /**
     * Hàm Main để test nghiệm thu kết quả Issue #5
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("================= TEST BATCH INSERT & WORKER (ISSUE #5) =================");
        
        // 1. Khởi tạo Database
        DatabaseManager.initializeDatabase();
        
        // 2. Tạo hàng đợi chia sẻ (BlockingQueue) với sức chứa 100 log
        BlockingQueue<LogEvent> mockQueue = new ArrayBlockingQueue<>(100);
        
        // 3. Đưa nhanh 5 log giả lập vào hàng đợi
        System.out.println("[Main] Dang day 5 log vao BlockingQueue...");
        mockQueue.put(new LogEvent("192.168.1.10", "2026-10-09 15:00:01", 16, "INFO", "Login success"));
        mockQueue.put(new LogEvent("192.168.1.11", "2026-10-09 15:00:02", 16, "ERROR", "Failed password"));
        mockQueue.put(new LogEvent("10.0.0.5", "2026-10-09 15:00:03", 16, "WARNING", "High CPU load"));
        mockQueue.put(new LogEvent("172.16.0.2", "2026-10-09 15:00:04", 16, "INFO", "User logout"));
        mockQueue.put(new LogEvent("192.168.1.100", "2026-10-09 15:00:05", 16, "CRITICAL", "System crash"));
        
        // 4. Khởi chạy luồng Worker (Thiết lập cứ gom đủ 3 log thì ghi 1 lô)
        BatchWorker worker = new BatchWorker(mockQueue, 3);
        Thread workerThread = new Thread(worker);
        workerThread.start();
        
        // Chờ 2 giây để Worker làm việc, sau đó tắt chương trình
        Thread.sleep(2000);
        worker.stopWorker();
        workerThread.interrupt();
        System.out.println("=========================================================================");
    }
}