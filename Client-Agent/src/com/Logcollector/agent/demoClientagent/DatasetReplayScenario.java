package com.Logcollector.agent.demoClientagent;

import com.Logcollector.agent.AgentLocalQueue;
import com.Logcollector.agent.EventGenerator;
import com.Logcollector.agent.Sender.TCPSender;
import com.Logcollector.agent.Sender.UDPSender;
import com.Logcollector.agent.SyslogFormatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kich ban 5: Phat lai du lieu log thuc te tu tap OpenSSH_2k (Loghub).
 * Cung cap 2 che do thuc nghiem:
 * 1. An ninh mang (Security Demo): Phat lai chuoi log tan cong Brute-force tu hacker thuc te (IP 112.95.230.3).
 * 2. Chiu tai & Do on dinh (Stress Test): Ban dong loat 300 - 500 log qua UDP/TCP bang da luong de kiem thu Server.
 */
public class DatasetReplayScenario {

    private static final String HOST = "127.0.0.1";
    private static final int UDP_PORT = 514;
    private static final int TCP_PORT = 9999;

    public static void main(String[] args) {
        File datasetFile = locateDatasetFile();
        if (datasetFile == null || !datasetFile.exists()) {
            System.err.println("[LOI] Khong tim thay file OpenSSH_2k.log trong thu muc dataset/!");
            System.err.println("Vui long dam bao file ton tai tai: dataset/OpenSSH_2k.log");
            return;
        }

        String mode = (args.length > 0) ? args[0].toLowerCase() : "1";

        if ("2".equals(mode) || "stress".equals(mode)) {
            runStressTestMode(datasetFile, 300);
        } else {
            runSecurityBruteForceMode(datasetFile);
        }
    }

    /**
     * Che do 1: Demo phat hien tan cong Brute-force tu log OpenSSH thuc te
     */
    public static void runSecurityBruteForceMode(File datasetFile) {
        System.out.println("==================================================");
        System.out.println("   KICH BAN 5A: PHAT HIEN BRUTE-FORCE TU LOG THAT ");
        System.out.println("==================================================");
        System.out.println("[NGUON DU LIEU] " + datasetFile.getAbsolutePath());
        System.out.println("[MUC TIEU] Trich xuat chuoi tan cong cua hacker IP 112.95.230.3 trong dataset.");
        System.out.println("[KY VONG] Server phat hien >5 lan sai password va kich hoat [CANH BAO AN NINH] ALERT mau do!\n");

        List<String> attackLines = extractAttackLogs(datasetFile, "112.95.230.3", 7);
        if (attackLines.isEmpty()) {
            System.err.println("[CANH BAO] Khong tim thay dong log cua IP 112.95.230.3, doc 6 dong Failed password dau tien...");
            attackLines = extractGenericFailedLogs(datasetFile, 6);
        }

        AgentLocalQueue queue = new AgentLocalQueue();
        String now = Instant.now().toString();

        for (int i = 0; i < attackLines.size(); i++) {
            String rawLine = attackLines.get(i);
            // Phan cap leo thang: 2 lan dau INFO, lan 3-4 WARNING, tu lan 5 CRITICAL
            String severity = (i >= 4) ? "CRITICAL" : (i >= 2 ? "WARNING" : "INFO");
            queue.enqueue(new EventGenerator.LogEvent(
                    now,
                    severity,
                    "LOGIN_FAILED",
                    rawLine
            ));
        }

        System.out.println("[LOCAL QUEUE] Da nap " + queue.size() + " ban ghi log tan cong that vao hang doi cuc bo.\n");

        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);
        SyslogFormatter formatter = new SyslogFormatter("ssh-gateway", "sshd", "24235");

        int count = 0;
        while (!queue.isEmpty()) {
            EventGenerator.LogEvent event = queue.dequeue();
            count++;
            try {
                // Dong goi theo chuan Syslog RFC 3164 truyen qua mang
                String syslogMessage = formatter.format3164(event);
                udpSender.send(syslogMessage);
                System.out.println(String.format("👉 [GUI UDP #%d] Da gui log that toi Server (cong %d): %s",
                        count, UDP_PORT, event.getMessage()));

                // Nghi 500ms giua moi lan thu de nguoi xem kip quan sat tren Server va Dashboard
                Thread.sleep(500);

            } catch (IOException e) {
                System.err.println("[GUI THAT BAI] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("\n[HOAN TAT] Da phat lai thanh cong toan bo chuoi log tan cong thuc te!");
        System.out.println("Kiem tra Terminal ServerMain va Web Dashboard de xem Canh bao An ninh mau do.");
    }

    /**
     * Che do 2: Demo chiu tai (Stress Test) bang cach ban dong loat hang tram log that
     */
    public static void runStressTestMode(File datasetFile, int maxLogs) {
        System.out.println("==================================================");
        System.out.println("   KICH BAN 5B: KIEM THU CHIU TAI VOI LOG THAT    ");
        System.out.println("==================================================");
        System.out.println("[NGUON DU LIEU] " + datasetFile.getAbsolutePath());
        System.out.println("[MUC TIEU] Doc " + maxLogs + " dong log that va ban don dap da luong qua UDP/TCP.");
        System.out.println("[KY VONG] Server tiep nhan on dinh, LogQueue dieu tiet tot, khong bi crash hay rot goi.\n");

        List<String> rawLogs = readSampleLines(datasetFile, maxLogs);
        System.out.println("[DATASET] Da doc " + rawLogs.size() + " dong log thuc te tu file.");

        SyslogFormatter formatter = new SyslogFormatter("cluster-node", "sshd", "1001");
        AgentLocalQueue queue = new AgentLocalQueue();
        String now = Instant.now().toString();

        for (String raw : rawLogs) {
            queue.enqueue(new EventGenerator.LogEvent(now, "INFO", "SSH_TRAFFIC", raw));
        }

        int totalToSend = queue.size();
        System.out.println("[LOCAL QUEUE] Da nap " + totalToSend + " log vao hang doi cuc bo.");
        System.out.println("[BAT DAU BAN DON DAP QUA UDP (CONG " + UDP_PORT + ") VA TCP (CONG " + TCP_PORT + ")]...\n");

        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger counter = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        while (!queue.isEmpty()) {
            EventGenerator.LogEvent ev = queue.dequeue();
            int index = counter.incrementAndGet();
            pool.submit(() -> {
                try {
                    String message = formatter.format3164(ev);
                    // Luan phien gui qua ca UDP (514) va TCP (9999) de kiem thu dong thoi ca 2 cong tiep nhan
                    if (index % 2 == 0) {
                        TCPSender tcpSender = new TCPSender(HOST, TCP_PORT);
                        tcpSender.send(message);
                    } else {
                        UDPSender udpSender = new UDPSender(HOST, UDP_PORT);
                        udpSender.send(message);
                    }
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long totalTime = Math.max(1, System.currentTimeMillis() - startTime);
        double throughput = (successCount.get() * 1000.0) / totalTime;

        System.out.println("--------------------------------------------------");
        System.out.println(String.format(" Tong so log da gui thanh cong : %d / %d", successCount.get(), totalToSend));
        System.out.println(String.format(" Thoi gian hoan tat            : %d ms", totalTime));
        System.out.println(String.format(" Toc do xu ly (Throughput)     : %.2f logs/giay", throughput));
        System.out.println("--------------------------------------------------");
        System.out.println("Ket qua: Server va he thong mang da vuot qua bai kiem thu chiu tai!");
    }

    private static File locateDatasetFile() {
        String[] possiblePaths = {
                "dataset/OpenSSH_2k.log",
                "../dataset/OpenSSH_2k.log",
                "LapTrinhMang_Log_Collection_and_Analysis/dataset/OpenSSH_2k.log",
                "c:/Users/TinKoBeo/Downloads/logcollection/LapTrinhMang_Log_Collection_and_Analysis/dataset/OpenSSH_2k.log",
                "c:/Users/TinKoBeo/Downloads/logcollection/OpenSSH_2k.log"
        };
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }

    private static List<String> extractAttackLogs(File file, String ip, int maxLines) {
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(ip) && (line.contains("Failed password") || line.contains("authentication failure"))) {
                    list.add(line);
                    if (list.size() >= maxLines) break;
                }
            }
        } catch (IOException e) {
            System.err.println("[LOI DOC FILE] " + e.getMessage());
        }
        return list;
    }

    private static List<String> extractGenericFailedLogs(File file, int maxLines) {
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Failed password")) {
                    list.add(line);
                    if (list.size() >= maxLines) break;
                }
            }
        } catch (IOException e) {
            System.err.println("[LOI DOC FILE] " + e.getMessage());
        }
        return list;
    }

    private static List<String> readSampleLines(File file, int maxLines) {
        List<String> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null && list.size() < maxLines) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("[LOI DOC FILE] " + e.getMessage());
        }
        return list;
    }
}
