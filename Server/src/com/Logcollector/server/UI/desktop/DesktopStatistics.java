package com.Logcollector.server.UI.desktop;

import java.util.*;

/** Bounded dashboard state. Legacy ChartManager remains untouched for ServerMain. */
final class DesktopStatistics {
    static final int LOG_LIMIT = 10_000, ALERT_LIMIT = 1_000, WINDOW = 300;
    record Alert(String time, String type, String ip, String message) {}
    record Rate(long second, long count, boolean available) {}
    record Ip(String address, long count) {}
    record Group(String key, String title, String severity, long count, String ips, String latest) {}
    record Filter(String level, String query, boolean ascending) {
        static Filter all() { return new Filter("Tất cả", "", false); }
        boolean accepts(DesktopLog log) {
            return (level.equals("Tất cả") || level.equals(log.event().getSeverityLevel())) && log.matches(query);
        }
    }
    record Snapshot(long total, Map<String, Long> severity, int ipCount, int retained,
                    List<Ip> ips, List<Group> groups, List<DesktopLog> logs, List<DesktopLog> allLogs, List<Alert> alerts,
                    List<Rate> rates, Long current, double average, long peak, double axisMax,
                    long startedAt, boolean everDisconnected) {}
    private static final class Bucket { long count; boolean available; Bucket(boolean value) { available = value; } }
    private final ArrayDeque<DesktopLog> logs = new ArrayDeque<>();
    private final ArrayDeque<Alert> alerts = new ArrayDeque<>();
    private final Map<String, Long> severity = new TreeMap<>();
    private final NavigableMap<Long, Bucket> rates = new TreeMap<>();
    private final long startedAt;
    private long total, lastSecond;
    private boolean connected, connectedBefore, everDisconnected;

    DesktopStatistics(long now) { startedAt = now; lastSecond = now / 1000; rates.put(lastSecond, new Bucket(false)); }
    private void advance(long now) {
        long second = now / 1000;
        for (long s = Math.max(lastSecond + 1, second - WINDOW); s <= second; s++) rates.put(s, new Bucket(connected));
        lastSecond = Math.max(lastSecond, second);
        rates.headMap(second - WINDOW, false).clear();
    }
    synchronized void connection(boolean value, long now) {
        advance(now);
        if (!value && connectedBefore) everDisconnected = true;
        if (value && !connectedBefore) rates.get(lastSecond).available = true;
        if (!value) rates.get(lastSecond).available = false;
        connected = value;
        connectedBefore |= value;
    }
    synchronized void add(DesktopLog log) {
        total++;
        severity.merge(log.event().getSeverityLevel(), 1L, Long::sum);
        logs.addLast(log);
        if (logs.size() > LOG_LIMIT) logs.removeFirst();
        advance(log.receivedAt());
        Bucket bucket = rates.get(log.receivedAt() / 1000);
        if (bucket != null) bucket.count++;
    }
    synchronized void alert(Alert alert) {
        alerts.addFirst(alert);
        if (alerts.size() > ALERT_LIMIT) alerts.removeLast();
    }
    synchronized Snapshot snapshot(long now, Filter groupFilter, Filter logFilter) {
        advance(now);
        Map<String, Long> ipCounts = new HashMap<>();
        Map<String, List<DesktopLog>> grouped = new HashMap<>();
        List<DesktopLog> visible = new ArrayList<>();
        List<DesktopLog> all = new ArrayList<>();
        Iterator<DesktopLog> iterator = logs.descendingIterator();
        while (iterator.hasNext()) {
            DesktopLog log = iterator.next();
            all.add(log);
            ipCounts.merge(log.event().getIp(), 1L, Long::sum);
            grouped.computeIfAbsent(log.groupKey(), key -> new ArrayList<>()).add(log);
            if (logFilter.accepts(log)) visible.add(log);
        }
        List<Ip> ips = ipCounts.entrySet().stream().map(e -> new Ip(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Ip::count).reversed().thenComparing(Ip::address)).toList();
        List<Group> groups = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<DesktopLog> members = entry.getValue();
            if (members.stream().noneMatch(groupFilter::accepts)) continue;
            DesktopLog latest = members.get(0);
            List<String> addresses = members.stream().map(l -> l.event().getIp()).distinct().sorted().toList();
            String label = String.join(", ", addresses.subList(0, Math.min(3, addresses.size())));
            if (addresses.size() > 3) label += " (+" + (addresses.size() - 3) + " IP)";
            groups.add(new Group(entry.getKey(), latest.eventType(), latest.event().getSeverityLevel(),
                    members.size(), label, latest.event().getTimestamp()));
        }
        Comparator<Group> order = Comparator.comparingLong(Group::count);
        if (!groupFilter.ascending()) order = order.reversed();
        groups.sort(order.thenComparing(Group::title).thenComparing(Group::severity));
        List<Rate> samples = new ArrayList<>();
        long peak = 0, sum = 0, seconds = 0;
        Long current = null;
        for (var entry : rates.entrySet()) {
            if (entry.getKey() >= now / 1000) continue;
            Bucket b = entry.getValue();
            samples.add(new Rate(entry.getKey(), b.count, b.available));
            // Include even partially observed buckets in the axis bound, but not in the average.
            peak = Math.max(peak, b.count);
            if (b.available) { sum += b.count; seconds++; }
            if (entry.getKey() == now / 1000 - 1 && b.available) current = b.count;
        }
        double max = Math.ceil((peak + 100.0) / 25.0) * 25;
        return new Snapshot(total, Map.copyOf(severity), ips.size(), logs.size(), ips, List.copyOf(groups),
                List.copyOf(visible), List.copyOf(all), List.copyOf(alerts), List.copyOf(samples), current,
                seconds == 0 ? 0 : (double) sum / seconds, peak, max, startedAt, everDisconnected);
    }
}
