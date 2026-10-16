/* Connect dashboard elements to the browser-only data adapter and renderers. */
(function () {
  "use strict";
  const byId = id => document.getElementById(id);
  const number = new Intl.NumberFormat("vi-VN");
  const time = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  const scope = byId("data-scope");
  let statusTimer, timer;
  let contentSignature = null;

  function announce(message) {
    clearTimeout(statusTimer);
    byId("dashboard-status").textContent = message;
    statusTimer = setTimeout(() => { byId("dashboard-status").textContent = ""; }, 6000);
  }

  const logTable = window.DashboardLogTable.create(announce);

  function emptyList(container, text, tag = "p") {
    const message = document.createElement(tag);
    message.className = "small muted";
    message.textContent = text;
    container.replaceChildren(message);
  }

  function renderProducts(events) {
    const container = byId("recent-products");
    if (!container) return;
    if (!events.length) {
      emptyList(container, "Chưa có lượt xem thiết bị trong 5 phút gần nhất.", "li");
      return;
    }
    container.replaceChildren(...events.map(event => {
      const item = document.createElement("li");
      const name = document.createElement("strong");
      name.textContent = event.productName || event.productId || "Thiết bị demo";
      const details = document.createElement("span");
      details.textContent = `${event.actor || "Kỹ thuật viên"} · ${time.format(event.at)}`;
      item.append(name, details);
      return item;
    }));
  }

  function renderAlerts(events) {
    const container = byId("security-alerts");
    if (!container) return;
    if (!events.length) {
      emptyList(container, "Không có cảnh báo mẫu trong nguồn và cửa sổ đang chọn.");
      return;
    }
    container.replaceChildren(...events.map(event => {
      const item = document.createElement("article");
      item.className = `alert-item${event.severity === "CRITICAL" ? " alert-critical" : ""}`;
      const title = document.createElement("h4");
      title.textContent = event.alertTitle;
      const source = document.createElement("span");
      source.className = "small mono";
      source.textContent = `${event.ip} · ${time.format(event.at)}`;
      const message = document.createElement("p");
      message.textContent = event.message;
      item.append(title, source, message);
      return item;
    }));
  }

  function renderIps(ips) {
    const container = byId("top-ips");
    if (!container) return;
    if (!ips.length) {
      emptyList(container, "Chưa có log chứa IP mẫu trong cửa sổ này.", "li");
      return;
    }
    container.replaceChildren(...ips.map(entry => {
      const item = document.createElement("li");
      const header = document.createElement("div");
      header.className = "top-ip-header";
      const address = document.createElement("span");
      address.className = "mono";
      address.textContent = entry.ip;
      const count = document.createElement("strong");
      count.textContent = `${number.format(entry.count)} log`;
      header.append(address, count);
      const track = document.createElement("div");
      track.className = "ip-track";
      track.setAttribute("aria-hidden", "true");
      const bar = document.createElement("div");
      bar.className = "ip-bar";
      bar.style.width = `${entry.count / ips[0].count * 100}%`;
      track.append(bar);
      item.append(header, track);
      return item;
    }));
  }

  function render() {
    const snapshot = window.DashboardData.getSnapshot(scope.value);
    const values = {
      "total-logs": snapshot.total, "current-rate": snapshot.current, "total-ips": snapshot.ipCount,
      "total-alerts": snapshot.alerts.length, "traffic-current": snapshot.current, "peak-rate": snapshot.peak,
      "register-count": snapshot.interactions.REGISTER_SUCCESS, "login-count": snapshot.interactions.LOGIN_SUCCESS,
      "failed-count": snapshot.interactions.LOGIN_FAILED, "click-count": snapshot.interactions.PRODUCT_CLICK
    };
    Object.entries(values).forEach(([id, value]) => {
      const el = byId(id);
      if (el) el.textContent = number.format(value);
    });
    const avgRateEl = byId("average-rate");
    if (avgRateEl) avgRateEl.textContent = snapshot.average.toLocaleString("vi-VN", { maximumFractionDigits: 1 });
    const updateEl = byId("updated-at");
    if (updateEl) {
      updateEl.textContent = time.format(snapshot.end);
      updateEl.dateTime = new Date(snapshot.end).toISOString();
    }
    const trafficChartEl = byId("traffic-chart");
    if (trafficChartEl) window.DashboardCharts.drawTraffic(trafficChartEl, snapshot);
    // Avoid rebuilding unchanged lists and the donut on every timer tick.
    const signature = snapshot.logs.map(event => event.id).join("|");
    if (signature !== contentSignature) {
      contentSignature = signature;
      const sevChartEl = byId("severity-chart");
      const sevLegendEl = byId("severity-legend");
      if (sevChartEl && sevLegendEl) {
        window.DashboardCharts.drawSeverity(sevChartEl, sevLegendEl, snapshot.counts);
      }
      renderProducts(snapshot.recentProducts);
      renderAlerts(snapshot.alerts);
      renderIps(snapshot.topIps);
    }
    logTable.update(snapshot.logs);
  }

  scope.addEventListener("change", () => {
    logTable.reset();
    render();
    const onlyActions = scope.value === "interaction";
    const addSampleBtn = byId("add-sample");
    const resetSampleBtn = byId("reset-samples");
    if (addSampleBtn) addSampleBtn.disabled = onlyActions;
    if (resetSampleBtn) resetSampleBtn.disabled = onlyActions;
    announce("Đã đổi nguồn cho toàn dashboard, xóa bộ lọc và tiếp tục cập nhật bảng.");
  });
  const addSampleBtn = byId("add-sample");
  if (addSampleBtn) {
    addSampleBtn.addEventListener("click", () => {
      window.DashboardData.addSample();
      announce("Đã thêm log mẫu.");
    });
  }
  const resetSampleBtn = byId("reset-samples");
  if (resetSampleBtn) {
    resetSampleBtn.addEventListener("click", () => {
      window.DashboardData.resetSamples();
      logTable.reset();
      render();
      announce("Đã tạo lại log mẫu.");
    });
  }

  function startUpdating() {
    clearInterval(timer);
    const addSampleBtn = byId("add-sample");
    const resetSampleBtn = byId("reset-samples");
    if (addSampleBtn) addSampleBtn.disabled = scope.value === "interaction";
    if (resetSampleBtn) resetSampleBtn.disabled = scope.value === "interaction";
    render();
    if (!document.hidden) timer = setInterval(render, 1000);
  }
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clearInterval(timer);
    else startUpdating();
  });
  window.addEventListener("pagehide", () => { clearInterval(timer); clearTimeout(statusTimer); });
  window.addEventListener("pageshow", startUpdating);
  startUpdating();

  // Ket noi WebSocket Real-time toi ServerMain qua cong 8085
  function connectWebSocket() {
    try {
      const badge = document.querySelector(".demo-badge");
      const ws = new WebSocket("ws://localhost:8085");

      ws.onopen = () => {
        if (badge) {
          badge.textContent = "● ĐÃ KẾT NỐI REALTIME (ws://localhost:8085)";
          badge.style.background = "#059669";
          badge.style.color = "#ffffff";
        }
        if (scope) {
          scope.value = "realtime";
        }
        render();
        announce("Đã kết nối thành công tới WebSocket Server (Cổng 8085). Dữ liệu log trực tiếp đang được cập nhật!");
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (window.DashboardData && window.DashboardData.addLiveLog) {
            window.DashboardData.addLiveLog(data);
            if (scope && scope.value === "interaction") {
              scope.value = "realtime";
            }
            render();
          }
        } catch (ignored) {}
      };

      ws.onclose = () => {
        if (badge) {
          badge.textContent = "BẢN DEMO · CHƯA KẾT NỐI BACKEND";
          badge.style.background = "";
          badge.style.color = "";
        }
        // Thu ket noi lai sau 3 giay neu mat ket noi
        setTimeout(connectWebSocket, 3000);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch (e) {
      // Trinh duyet hoac mang khong ho tro websocket thi giu nguyen che do demo
    }
  }

  connectWebSocket();
})();
