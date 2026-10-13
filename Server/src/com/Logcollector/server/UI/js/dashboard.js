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
    if (!events.length) {
      emptyList(container, "Chưa có lượt xem sản phẩm trong 5 phút gần nhất.", "li");
      return;
    }
    container.replaceChildren(...events.map(event => {
      const item = document.createElement("li");
      const name = document.createElement("strong");
      name.textContent = event.productName || event.productId || "Sản phẩm demo";
      const details = document.createElement("span");
      details.textContent = `${event.actor || "Khách demo"} · ${time.format(event.at)}`;
      item.append(name, details);
      return item;
    }));
  }

  function renderAlerts(events) {
    const container = byId("security-alerts");
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
    Object.entries(values).forEach(([id, value]) => { byId(id).textContent = number.format(value); });
    byId("average-rate").textContent = snapshot.average.toLocaleString("vi-VN", { maximumFractionDigits: 1 });
    byId("updated-at").textContent = time.format(snapshot.end);
    byId("updated-at").dateTime = new Date(snapshot.end).toISOString();
    window.DashboardCharts.drawTraffic(byId("traffic-chart"), snapshot);
    // Avoid rebuilding unchanged lists and the donut on every timer tick.
    const signature = snapshot.logs.map(event => event.id).join("|");
    if (signature !== contentSignature) {
      contentSignature = signature;
      window.DashboardCharts.drawSeverity(byId("severity-chart"), byId("severity-legend"), snapshot.counts);
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
    byId("add-sample").disabled = onlyActions;
    byId("reset-samples").disabled = onlyActions;
    announce("Đã đổi nguồn cho toàn dashboard, xóa bộ lọc và tiếp tục cập nhật bảng.");
  });
  byId("add-sample").addEventListener("click", () => {
    window.DashboardData.addSample();
    announce("Đã thêm log mẫu. Log sẽ xuất hiện khi chốt giây kế tiếp; bảng đang tạm dừng hoặc có bộ lọc có thể chưa hiển thị log này.");
  });
  byId("reset-samples").addEventListener("click", () => {
    window.DashboardData.resetSamples();
    logTable.reset();
    render();
    announce("Đã tạo lại 1.200 log mẫu và tiếp tục bảng. Giữ nguyên tài khoản cùng các thao tác demo đã ghi nhận.");
  });

  function startUpdating() {
    clearInterval(timer);
    byId("add-sample").disabled = scope.value === "interaction";
    byId("reset-samples").disabled = scope.value === "interaction";
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
})();
