package com.Logcollector.server;

import com.Logcollector.server.Core.IngestionEngine;
import com.Logcollector.server.Core.LogQueue;
import com.Logcollector.server.Parser.LogParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    private static final int UDP_PORT = 514;
    private static final int TCP_PORT = 9999;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   KHOI DONG SERVER TIEP NHAN LOG (TEST MODE)     ");
        System.out.println("==================================================");

        try {
            // 1. Luồng tiêu thụ log từ LogQueue và in ra màn hình
            ExecutorService consumer = Executors.newSingleThreadExecutor();
            consumer.submit(() -> {
                System.out.println("[PIPELINE] San sang lay log tu Queue...");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String rawLog = LogQueue.take();
                        System.out.println("[SERVER NHAN DUOC DULIEU]: " + rawLog);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[Error] " + e.getMessage());
                    }
                }
            });

            // 2. Khởi động IngestionEngine (lắng nghe UDP và TCP)
            System.out.println("[CORE] Dang mo cong UDP " + UDP_PORT + " va TCP " + TCP_PORT + "...");
            IngestionEngine engine = new IngestionEngine(UDP_PORT, TCP_PORT);
            engine.start();

            System.out.println("==================================================");
            System.out.println("   SERVER DANG CHAY! SAN SANG TEST VOI CLIENT     ");
            System.out.println("==================================================");

            // Giữ cho chương trình chạy liên tục
            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}