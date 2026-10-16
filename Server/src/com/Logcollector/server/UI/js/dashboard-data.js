/* Real-time Data Adapter for Dashboard */
(function () {
  "use strict";

  const levels = Object.freeze(["INFO", "WARNING", "ERROR", "CRITICAL"]);
  const seconds = 300;
  let additions = [];
  let sequence = 0;

  function addLiveLog(liveEvent) {
    if (!liveEvent) return;
    const severity = levels.includes(liveEvent.level) ? liveEvent.level : "INFO";
    sequence += 1;
    const item = {
      id: `live-${sequence}`,
      at: Date.now(),
      severity: severity,
      ip: liveEvent.ip || "127.0.0.1",
      device: liveEvent.device || "Network-Device",
      category: liveEvent.category || "APPLICATION",
      source: "realtime",
      type: liveEvent.category || liveEvent.alertType || liveEvent.type || "LOG_RECEIVED",
      message: liveEvent.message || liveEvent.raw || "Live log received",
      raw: liveEvent.raw
    };
    if (liveEvent.type === "ALERT" || liveEvent.alertType) {
      item.alertTitle = liveEvent.alertType === "BRUTE_FORCE" ? "Phát hiện Brute-Force mật khẩu" : "Phát hiện quét cổng (Port Scan)";
      item.severity = "CRITICAL";
      item.category = "SECURITY";
    }
    additions.unshift(item);
    if (additions.length > 500) additions.pop();
  }

  function getSnapshot(scope = "realtime", now = Date.now()) {
    const end = Math.floor(now / 1000) * 1000;
    const start = end - seconds * 1000;

    const logs = additions
      .filter(event => event.at >= start && event.at <= now + 1000)
      .sort((a, b) => b.at - a.at || a.id.localeCompare(b.id));

    const counts = Object.fromEntries(levels.map(level => [level, 0]));
    const rates = Array(seconds).fill(0);
    const ips = new Map();

    logs.forEach(event => {
      counts[event.severity] += 1;
      const secIndex = Math.min(seconds - 1, Math.max(0, Math.floor((event.at - start) / 1000)));
      rates[secIndex] += 1;
      if (event.ip) ips.set(event.ip, (ips.get(event.ip) || 0) + 1);
    });

    return {
      start, end, logs, counts, rates,
      total: logs.length,
      current: rates[seconds - 1],
      peak: Math.max(1, ...rates),
      average: logs.length / seconds,
      ipCount: ips.size,
      topIps: [...ips].map(([ip, count]) => ({ ip, count })).sort((a, b) => b.count - a.count || a.ip.localeCompare(b.ip)).slice(0, 5),
      alerts: logs.filter(event => event.alertTitle)
    };
  }

  function purgeLogsByIp(blockedIp) {
    if (!blockedIp) return;
    const clean = String(blockedIp).trim();
    additions = additions.filter(event => (event.ip || "").trim() !== clean);
  }

  window.DashboardData = Object.freeze({ levels, addLiveLog, purgeLogsByIp, getSnapshot });
})();
