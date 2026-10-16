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
    const secDetails = Array(seconds);
    const ips = new Map();

    const sevOrder = { CRITICAL: 4, ERROR: 3, WARNING: 2, INFO: 1 };

    logs.forEach(event => {
      counts[event.severity] += 1;
      const secIndex = Math.min(seconds - 1, Math.max(0, Math.floor((event.at - start) / 1000)));
      rates[secIndex] += 1;

      if (!secDetails[secIndex]) {
        secDetails[secIndex] = {
          counts: { INFO: 0, WARNING: 0, ERROR: 0, CRITICAL: 0 },
          categories: {},
          ips: {},
          devices: {},
          highestSeverity: event.severity || "INFO",
          dominantCat: event.category || "APPLICATION",
          topIp: event.ip || "127.0.0.1",
          topDevice: event.device || "Network-Device"
        };
      }

      const d = secDetails[secIndex];
      d.counts[event.severity] = (d.counts[event.severity] || 0) + 1;

      const cat = event.category || "APPLICATION";
      d.categories[cat] = (d.categories[cat] || 0) + 1;

      if (event.ip) d.ips[event.ip] = (d.ips[event.ip] || 0) + 1;
      if (event.device) d.devices[event.device] = (d.devices[event.device] || 0) + 1;

      if ((sevOrder[event.severity] || 0) > (sevOrder[d.highestSeverity] || 0)) {
        d.highestSeverity = event.severity;
      }

      if (event.ip) ips.set(event.ip, (ips.get(event.ip) || 0) + 1);
    });

    // Xác định dominant category, topIp, topDevice cho các giây có dữ liệu
    for (let i = 0; i < seconds; i++) {
      const d = secDetails[i];
      if (d) {
        let maxCat = 0, topC = d.dominantCat;
        Object.entries(d.categories).forEach(([c, n]) => {
          if (n > maxCat) { maxCat = n; topC = c; }
        });
        d.dominantCat = topC;

        let maxIp = 0, topI = d.topIp;
        Object.entries(d.ips).forEach(([ip, n]) => {
          if (n > maxIp) { maxIp = n; topI = ip; }
        });
        d.topIp = topI;

        let maxDev = 0, topD = d.topDevice;
        Object.entries(d.devices).forEach(([dev, n]) => {
          if (n > maxDev) { maxDev = n; topD = dev; }
        });
        d.topDevice = topD;
      }
    }

    return {
      start, end, logs, counts, rates, secDetails,
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
