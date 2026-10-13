/* Browser-only adapter: synthetic logs and actual UI demo actions stay distinguishable. */
(function () {
  "use strict";

  const levels = Object.freeze(["INFO", "WARNING", "ERROR", "CRITICAL"]);
  const seconds = 300;
  let samples = [];
  let additions = [];
  let sequence = 0;
  let generation = 0;

  function resetSamples(now = Date.now()) {
    const end = Math.floor(now / 1000) * 1000;
    const start = end - seconds * 1000;
    generation += 1;
    additions = [];
    // 900 base logs + 21 at the peak + 5 at the end + 274 spread out = 1,200.
    const rates = Array(seconds).fill(3);
    rates[180] = 24;
    rates[299] = 8;
    let remaining = 274;
    for (let index = 0; index < seconds && remaining > 0; index += 1) {
      if (index === 180 || index === 299) continue;
      rates[index] += 1;
      remaining -= 1;
    }
    const times = [];
    rates.forEach((rate, index) => {
      for (let count = 0; count < rate; count += 1) {
        times.push(start + index * 1000 + Math.floor((count + 1) * 1000 / (rate + 1)));
      }
    });
    samples = times.map((unused, index) => {
      const portion = index % 100;
      const severity = portion < 70 ? "INFO" : portion < 90 ? "WARNING" : portion < 98 ? "ERROR" : "CRITICAL";
      const messages = {
        INFO: "Tiếp nhận bản ghi hoạt động từ ứng dụng mẫu.",
        WARNING: "Lưu lượng log mẫu tăng tại một thời điểm.",
        ERROR: "Tài nguyên mẫu trả về lỗi HTTP 404.",
        CRITICAL: "Dịch vụ mẫu không phản hồi trong thời gian quy định."
      };
      const event = {
        id: `sample-${generation}-${index}`, at: times[(index * 37 + 900) % times.length],
        severity, ip: `192.0.2.${10 + index % 12}`, source: "sample",
        type: "LOG_RECEIVED", message: messages[severity]
      };
      // Reserved documentation IPs; these are fixture scenarios, not detection rules.
      if (index === 70 || index === 170) {
        event.type = "BRUTE_FORCE_SAMPLE";
        event.alertTitle = "Đăng nhập thất bại liên tiếp";
        event.message = "Kịch bản brute-force mẫu: nhiều lần đăng nhập thất bại từ cùng IP.";
      } else if (index === 98) {
        event.type = "PORT_SCAN_SAMPLE";
        event.alertTitle = "Truy cập nhiều cổng liên tiếp";
        event.message = "Kịch bản port scan mẫu: một IP truy cập nhiều cổng dịch vụ.";
      }
      return event;
    });
  }

  function addSample() {
    const severity = levels[sequence % levels.length];
    sequence += 1;
    additions.push({
      id: `manual-${sequence}`, at: Date.now(), severity, ip: "192.0.2.10",
      source: "sample", type: "MANUAL_SAMPLE",
      message: `Log ${severity} được thêm bằng nút thử giao diện.`
    });
    additions = additions.slice(-500);
  }

  function getSnapshot(scope = "all", now = Date.now()) {
    // Complete seconds only: counters, chart buckets, alerts and table share one window.
    const end = Math.floor(now / 1000) * 1000;
    const start = end - seconds * 1000;
    const actions = window.DemoData.getEvents().map((event, index) => ({
      ...event, id: `interaction-${event.at}-${index}`, source: "interaction", ip: null
    }));
    const logs = (scope === "interaction" ? actions : [...samples, ...additions, ...actions])
      .filter(event => event.at >= start && event.at < end)
      .sort((a, b) => b.at - a.at || a.id.localeCompare(b.id));
    const counts = Object.fromEntries(levels.map(level => [level, 0]));
    const rates = Array(seconds).fill(0);
    const ips = new Map();
    const interactions = { REGISTER_SUCCESS: 0, LOGIN_SUCCESS: 0, LOGIN_FAILED: 0, PRODUCT_CLICK: 0 };
    logs.forEach(event => {
      counts[event.severity] += 1;
      rates[Math.floor((event.at - start) / 1000)] += 1;
      if (event.ip) ips.set(event.ip, (ips.get(event.ip) || 0) + 1);
      if (event.source === "interaction" && Object.hasOwn(interactions, event.type)) interactions[event.type] += 1;
    });
    return {
      start, end, logs, counts, rates, interactions,
      total: logs.length, current: rates[seconds - 1], peak: Math.max(...rates), average: logs.length / seconds,
      ipCount: ips.size,
      topIps: [...ips].map(([ip, count]) => ({ ip, count })).sort((a, b) => b.count - a.count || a.ip.localeCompare(b.ip)).slice(0, 5),
      alerts: logs.filter(event => event.source === "sample" && event.alertTitle),
      recentProducts: logs.filter(event => event.source === "interaction" && event.type === "PRODUCT_CLICK").slice(0, 3)
    };
  }

  resetSamples();
  window.DashboardData = Object.freeze({ levels, resetSamples, addSample, getSnapshot });
})();
