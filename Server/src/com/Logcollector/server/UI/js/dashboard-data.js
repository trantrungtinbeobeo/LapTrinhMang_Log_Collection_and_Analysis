/* Real-time Data Adapter for Dashboard with LocalStorage Persistence */
(function () {
  "use strict";

  const levels = Object.freeze(["INFO", "WARNING", "ERROR", "CRITICAL"]);
  const seconds = 300;
  const STORAGE_KEY = "dashboard_live_logs_v2";

  function loadPersistedLogs() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) {
          return parsed;
        }
      }
    } catch (e) {
      // Fallback khi localStorage khong kha dung
    }
    return [];
  }

  function savePersistedLogs(list) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, 1000)));
    } catch (e) {
      // Bo qua loi vuot han muc quota cua trinh duyet
    }
  }

  let additions = loadPersistedLogs();
  let sequence = additions.length;

  function addLiveLog(liveEvent) {
    if (!liveEvent) return;
    const severity = levels.includes(liveEvent.level) ? liveEvent.level : "INFO";
    sequence += 1;
    const now = liveEvent.at || Date.now();
    const item = {
      id: liveEvent.id || `live-${now}-${sequence}`,
      at: now,
      severity: severity,
      ip: liveEvent.ip || "127.0.0.1",
      device: liveEvent.device || "Network-Device",
      category: liveEvent.category || "APPLICATION",
      source: liveEvent.source || "realtime",
      type: liveEvent.category || liveEvent.alertType || liveEvent.type || "LOG_RECEIVED",
      message: liveEvent.message || liveEvent.raw || "Live log received",
      raw: liveEvent.raw || liveEvent.message || ""
    };
    if (liveEvent.type === "ALERT" || liveEvent.alertType) {
      item.alertTitle = liveEvent.alertType === "BRUTE_FORCE" ? "Phát hiện Brute-Force mật khẩu" : "Phát hiện quét cổng (Port Scan)";
      item.severity = "CRITICAL";
      item.category = "SECURITY";
    }

    const exists = additions.some(a => a.id === item.id);
    if (!exists) {
      additions.unshift(item);
      if (additions.length > 1000) additions.pop();
      savePersistedLogs(additions);
    }
  }

  function clearAllLogs() {
    additions = [];
    sequence = 0;
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch (e) {}
  }

  function getSnapshot(scope = "realtime", now = Date.now()) {
    const end = Math.floor(now / 1000) * 1000;
    const start = end - seconds * 1000;

    // Toan bo logs hien co (sap xep moi nhat len dau)
    const allLogs = additions.slice().sort((a, b) => b.at - a.at || String(a.id).localeCompare(String(b.id)));

    // Logs trong cua so 5 phut gan nhat danh cho Throughput Wave
    const logsInWindow = allLogs.filter(event => event.at >= start && event.at <= now + 1000);

    const counts = Object.fromEntries(levels.map(level => [level, 0]));
    const rates = Array(seconds).fill(0);
    const secDetails = Array(seconds);
    const ips = new Map();

    const sevOrder = { CRITICAL: 4, ERROR: 3, WARNING: 2, INFO: 1 };

    // Thong ke bieu do Donut: Tinh tren toan bo logs hien co de giu nguyen ti le mau sac khi refresh
    const targetLogs = allLogs.length > 0 ? allLogs : logsInWindow;
    targetLogs.forEach(event => {
      if (counts[event.severity] !== undefined) {
        counts[event.severity] += 1;
      }
      if (event.ip) ips.set(event.ip, (ips.get(event.ip) || 0) + 1);
    });

    // Thong ke luu luong 5 phut (Throughput Wave)
    logsInWindow.forEach(event => {
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
    });

    // Xac dinh dominant category, topIp, topDevice cho cac giay co du lieu
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

    // Luon dam bao co it nhat 1 dia chi IP nguon (may admin hien tai)
    if (ips.size === 0) {
      ips.set("127.0.0.1", 0);
    }

    return {
      start, end,
      logs: allLogs, // Luon giu toan bo log trong bang lich su
      counts, rates, secDetails,
      total: allLogs.length,
      current: rates[seconds - 1],
      peak: Math.max(1, ...rates),
      average: logsInWindow.length / seconds,
      ipCount: ips.size,
      topIps: [...ips].filter(([ip, count]) => count > 0).map(([ip, count]) => ({ ip, count })).sort((a, b) => b.count - a.count || a.ip.localeCompare(b.ip)).slice(0, 5),
      alerts: allLogs.filter(event => event.alertTitle)
    };
  }

  function purgeLogsByIp(blockedIp) {
    if (!blockedIp) return;
    const clean = String(blockedIp).trim();
    additions = additions.filter(event => (event.ip || "").trim() !== clean);
    savePersistedLogs(additions);
  }

  // Lang nghe thay doi tu tab khac (vi du khi thao tac ben products.html)
  window.addEventListener("storage", (e) => {
    if (e.key === STORAGE_KEY) {
      additions = loadPersistedLogs();
      if (window.renderDashboard) {
        window.renderDashboard();
      }
    }
  });

  window.DashboardData = Object.freeze({ levels, addLiveLog, purgeLogsByIp, getSnapshot, clearAllLogs });
})();
