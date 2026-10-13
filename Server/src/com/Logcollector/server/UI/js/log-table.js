/* Bounded table, local filters, pagination and export. No collection/backend logic. */
(function () {
  "use strict";
  const byId = id => document.getElementById(id);
  const number = new Intl.NumberFormat("vi-VN");
  const time = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit", fractionalSecondDigits: 3 });
  const normalize = value => String(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase();

  function create(announce) {
    const search = byId("log-search");
    const severity = byId("log-severity");
    const ip = byId("log-ip");
    const pauseButton = byId("pause-table");
    const rows = byId("log-rows");
    const pageSize = 10;
    let records = [], latest = [], filtered = [];
    let page = 1, paused = false, signature = null;

    function setIpOptions() {
      const selected = ip.value;
      const values = [...new Set(records.map(event => event.ip || "unknown"))].sort();
      // Keep an expired selected IP rather than silently changing the user's filter.
      if (selected && !values.includes(selected)) values.push(selected);
      ip.replaceChildren(new Option("Tất cả IP", ""));
      values.forEach(value => ip.add(new Option(value === "unknown" ? "Chưa thu thập IP" : value, value)));
      ip.value = selected;
    }

    function render() {
      const query = normalize(search.value.trim());
      filtered = records.filter(event => (!severity.value || event.severity === severity.value)
        && (!ip.value || (event.ip || "unknown") === ip.value)
        && (!query || normalize([event.message, event.type, event.productId || "", event.productName || "", event.actor || "", event.ip || ""].join(" ")).includes(query)));
      const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
      page = Math.min(page, pages);
      const offset = (page - 1) * pageSize;
      const fragment = document.createDocumentFragment();
      filtered.slice(offset, offset + pageSize).forEach(event => {
        const row = document.createElement("tr");
        const dateCell = document.createElement("td");
        const date = document.createElement("time");
        date.dateTime = new Date(event.at).toISOString();
        date.title = new Date(event.at).toLocaleString("vi-VN");
        date.textContent = time.format(event.at);
        dateCell.className = "mono";
        dateCell.append(date);
        const severityCell = document.createElement("td");
        const badge = document.createElement("span");
        badge.className = `severity severity-${event.severity.toLowerCase()}`;
        badge.textContent = event.severity;
        severityCell.append(badge);
        const ipCell = document.createElement("td");
        ipCell.className = "mono";
        ipCell.textContent = event.ip || "—";
        ipCell.title = event.ip ? "Địa chỉ IP mẫu, không phải IP thu thập thật" : "Frontend chưa thu thập IP";
        const sourceCell = document.createElement("td");
        const source = document.createElement("span");
        source.className = `badge log-source${event.source === "interaction" ? " log-source-interaction" : ""}`;
        source.textContent = event.source === "interaction" ? "Thao tác demo" : "Mẫu";
        sourceCell.append(source);
        const messageCell = document.createElement("td");
        const type = document.createElement("span");
        type.className = "event-name mono";
        type.textContent = event.type;
        const message = document.createElement("span");
        message.textContent = event.message + (event.type === "PRODUCT_CLICK" ? ` ${event.productName || event.productId || "Sản phẩm"} · ${event.actor || "Khách demo"}.` : "");
        messageCell.append(type, message);
        row.append(dateCell, severityCell, ipCell, sourceCell, messageCell);
        fragment.append(row);
      });
      rows.replaceChildren(fragment);
      byId("logs-empty").hidden = filtered.length > 0;
      byId("log-result-count").textContent = filtered.length
        ? `${number.format(offset + 1)}–${number.format(Math.min(offset + pageSize, filtered.length))} / ${number.format(filtered.length)} log phù hợp · Bộ đệm ${number.format(records.length)} log`
        : `0 log phù hợp · Bộ đệm ${number.format(records.length)} log`;
      byId("page-label").textContent = `${page} / ${pages}`;
      byId("previous-page").disabled = page === 1;
      byId("next-page").disabled = page === pages;
      byId("export-logs").disabled = filtered.length === 0;
    }

    function acceptLatest() {
      const nextSignature = latest.map(event => event.id).join("|");
      if (nextSignature === signature) return;
      signature = nextSignature;
      records = latest;
      setIpOptions();
      render();
    }

    function setPaused(value) {
      paused = value;
      pauseButton.setAttribute("aria-pressed", String(paused));
      pauseButton.textContent = paused ? "Tiếp tục bảng" : "Tạm dừng bảng";
      byId("table-paused").hidden = !paused;
      if (!paused) acceptLatest();
    }

    search.addEventListener("input", () => { page = 1; render(); });
    [severity, ip].forEach(control => control.addEventListener("change", () => { page = 1; render(); }));
    byId("reset-filters").addEventListener("click", () => {
      search.value = ""; severity.value = ""; ip.value = ""; page = 1;
      render();
      announce("Đã xóa bộ lọc của bảng log.");
    });
    byId("previous-page").addEventListener("click", () => { page = Math.max(1, page - 1); render(); });
    byId("next-page").addEventListener("click", () => { page += 1; render(); });
    pauseButton.addEventListener("click", () => {
      setPaused(!paused);
      announce(paused ? "Đã tạm dừng riêng bảng log. Biểu đồ vẫn cập nhật." : "Bảng đã tiếp tục cập nhật log mới.");
    });
    byId("export-logs").addEventListener("click", () => {
      // Export all filtered pages, without session/password fields.
      const exported = filtered.map(event => ({
        timestamp: new Date(event.at).toISOString(), severity: event.severity,
        ip: event.ip, source: event.source, type: event.type, message: event.message,
        ...(event.type === "PRODUCT_CLICK" ? { productId: event.productId, productName: event.productName, actor: event.actor } : {})
      }));
      const url = URL.createObjectURL(new Blob([JSON.stringify(exported, null, 2)], { type: "application/json;charset=utf-8" }));
      const link = document.createElement("a");
      link.href = url;
      link.download = `demo-logs-${new Date().toISOString().replace(/[:.]/g, "-")}.json`;
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      announce(`Đã yêu cầu tải JSON gồm ${number.format(exported.length)} log theo bộ lọc hiện tại.`);
    });

    return Object.freeze({
      update(logs) { latest = logs.slice(0, 500); if (!paused) acceptLatest(); },
      reset() {
        search.value = ""; severity.value = ""; ip.value = ""; page = 1;
        signature = null;
        setPaused(false);
      }
    });
  }

  window.DashboardLogTable = Object.freeze({ create });
})();
