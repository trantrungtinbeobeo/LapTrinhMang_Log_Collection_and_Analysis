package com.Logcollector.server.UI;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChartManager (Server-side Metrics Engine):
 * Thu thap, tong hop va tinh toan cac chi so thong ke luu luong log theo thoi gian thuc.
 * Ap dung ky thuat Cua so truot (Sliding Window) va Thread-safe de cung cap du lieu cho bieu do.
 */
public class ChartManager {

    private static final int WINDOW_SECONDS = 300; // Cua so 5 phut

    // Dem so luong theo muc do nghiem trong (INFO, WARNING, ERROR, CRITICAL)
    private final Map<String, AtomicInteger> severityCounts = new ConcurrentHashMap<>();

    // Thong ke luu luong theo tung giay (Timestamp giay -> So log)
    private final Map<Long, AtomicInteger> secondRates = new ConcurrentHashMap<>();

    // Thong ke top IP gui log nhieu nhat
    private final Map<String, AtomicInteger> ipCounts = new ConcurrentHashMap<>();

    private final AtomicInteger totalLogs = new AtomicInteger(0);

    public ChartManager() {
        // Khoi tao cac muc do mac dinh
        severityCounts.put("INFO", new AtomicInteger(0));
        severityCounts.put("WARNING", new AtomicInteger(0));
        severityCounts.put("ERROR", new AtomicInteger(0));
        severityCounts.put("CRITICAL", new AtomicInteger(0));
    }

    /**
     * Ghi nhan mot su kien log moi vao bo thong ke
     */
    public void recordLog(String severity, String ip) {
        totalLogs.incrementAndGet();

        // 1. Dem theo muc do
        String level = (severity != null) ? severity.toUpperCase() : "INFO";
        severityCounts.computeIfAbsent(level, k -> new AtomicInteger(0)).incrementAndGet();

        // 2. Dem theo IP nguon
        if (ip != null && !ip.isEmpty() && !"127.0.0.1".equals(ip)) {
            ipCounts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        }

        // 3. Dem luu luong theo giay hien tai
        long currentSecond = System.currentTimeMillis() / 1000L;
        secondRates.computeIfAbsent(currentSecond, k -> new AtomicInteger(0)).incrementAndGet();

        // Don dep cac giay cu hon cua so 5 phut
        cleanOldSeconds(currentSecond);
    }

    /**
     * Don dep cac giay da troi qua khoi cua so thoi gian
     */
    private void cleanOldSeconds(long currentSecond) {
        long threshold = currentSecond - WINDOW_SECONDS;
        secondRates.keySet().removeIf(sec -> sec < threshold);
    }

    /**
     * Lay toc do log hien tai o giay gan nhat (log/giay)
     */
    public int getCurrentRate() {
        long lastSecond = (System.currentTimeMillis() / 1000L) - 1;
        AtomicInteger count = secondRates.get(lastSecond);
        return (count != null) ? count.get() : 0;
    }

    /**
     * Lay dinh luu luong (Peak Rate) trong cua so 5 phut
     */
    public int getPeakRate() {
        int peak = 0;
        for (AtomicInteger count : secondRates.values()) {
            if (count.get() > peak) {
                peak = count.get();
            }
        }
        return peak;
    }

    /**
     * Lay tong so log da ghi nhan
     */
    public int getTotalLogs() {
        return totalLogs.get();
    }

    /**
     * Lay ban snapshot so luong theo tung muc do severity
     */
    public Map<String, Integer> getSeveritySnapshot() {
        Map<String, Integer> snapshot = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> entry : severityCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    /**
     * Lay danh sach Top IP phat sinh nhieu log nhat
     */
    public List<Map.Entry<String, Integer>> getTopIps(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, AtomicInteger> entry : ipCounts.entrySet()) {
            list.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().get()));
        }
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list.subList(0, Math.min(limit, list.size()));
    }
}
