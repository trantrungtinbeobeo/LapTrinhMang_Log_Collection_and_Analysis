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
        row.title = "Nhấp chuột để xem chi tiết log này";
        row.addEventListener("click", () => openLogDetail(event));

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
        const ipText = document.createElement("div");
        ipText.className = "mono";
        ipText.style.fontWeight = "600";
        ipText.textContent = event.ip || "127.0.0.1";
        const devText = document.createElement("div");
        devText.className = "small muted";
        devText.style.fontSize = "10px";
        devText.style.whiteSpace = "nowrap";
        devText.textContent = event.device || (event.source === "realtime" ? "Local-Host" : "Network-Host");
        ipCell.append(ipText, devText);

        const catCell = document.createElement("td");
        const catBadge = document.createElement("span");
        const catName = (event.category || (event.type && event.type.includes("LOGIN") ? "AUTH" : event.type && event.type.includes("ATTACK") ? "SECURITY" : "APPLICATION")).toUpperCase();
        catBadge.className = `badge category-badge category-${catName.toLowerCase()}`;
        catBadge.textContent = catName;
        catCell.append(catBadge);

        const messageCell = document.createElement("td");
        const type = document.createElement("span");
        type.className = "event-name mono";
        type.textContent = event.type;
        const message = document.createElement("span");
        message.textContent = event.message + (event.type === "PRODUCT_CLICK" ? ` ${event.productName || event.productId || "Sản phẩm"} · ${event.actor || "Khách demo"}.` : "");
        messageCell.append(type, message);
        const actionCell = document.createElement("td");
        actionCell.style.textAlign = "center";
        const detailBtn = document.createElement("button");
        detailBtn.type = "button";
        detailBtn.className = "btn btn-outline btn-tiny";
        detailBtn.textContent = "Xem";
        detailBtn.title = "Xem chi tiết bản ghi log này";
        detailBtn.addEventListener("click", (e) => {
          e.stopPropagation();
          openLogDetail(event);
        });
        actionCell.append(detailBtn);

        row.append(dateCell, severityCell, ipCell, catCell, messageCell, actionCell);
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

    function openLogDetail(event) {
      const dialog = byId("log-detail-dialog");
      if (!dialog) return;

      const logIdEl = byId("modal-log-id");
      if (logIdEl) logIdEl.textContent = event.id || ("LOG-" + Math.abs(event.at).toString(36).toUpperCase());

      const logTimeEl = byId("modal-log-time");
      if (logTimeEl) logTimeEl.textContent = `${new Date(event.at).toLocaleString("vi-VN")} (${event.at})`;

      const logIpEl = byId("modal-log-ip");
      if (logIpEl) logIpEl.textContent = event.ip || "127.0.0.1";

      const logDevEl = byId("modal-log-device");
      if (logDevEl) logDevEl.textContent = event.device || "Máy chủ / Client-Agent";

      const logCatEl = byId("modal-log-category");
      if (logCatEl) {
        const cat = (event.category || (event.type && event.type.includes("LOGIN") ? "AUTH" : "APPLICATION")).toUpperCase();
        logCatEl.innerHTML = `<span class="badge category-badge category-${cat.toLowerCase()}">${cat}</span>`;
      }

      const sevEl = byId("modal-log-severity");
      if (sevEl) {
        sevEl.textContent = event.severity;
        sevEl.className = `detail-val severity severity-${(event.severity || "info").toLowerCase()}`;
      }

      const typeEl = byId("modal-log-type");
      if (typeEl) typeEl.textContent = event.type || "SYSLOG_EVENT";

      const msgEl = byId("modal-log-message");
      if (msgEl) msgEl.textContent = event.message || "—";

      const rawEl = byId("modal-log-raw");
      if (rawEl) {
        if (event.raw) {
          rawEl.textContent = event.raw;
        } else {
          const pri = event.severity === "CRITICAL" ? 11 : event.severity === "WARNING" ? 12 : 14;
          rawEl.textContent = `<${pri}>1 ${new Date(event.at).toISOString()} ${event.ip || "127.0.0.1"} ${event.type || "syslog"} - - [flow id="${event.id || 'N/A'}"] ${event.message}`;
        }
      }

      const adviceEl = byId("modal-log-advice");
      if (adviceEl) {
        const sev = (event.severity || "").toUpperCase();
        const type = (event.type || "").toUpperCase();
        const msg = (event.message || "").toLowerCase();

        if (sev === "CRITICAL" || type.includes("CRITICAL") || type.includes("BRUTE_FORCE") || type.includes("ATTACK")) {
          adviceEl.className = "advice-box advice-critical";
          adviceEl.innerHTML = `<strong>⚠️ MỨC ĐỘ NGUY HIỂM CAO:</strong> Phát hiện chuỗi đăng nhập thất bại vượt ngưỡng (&ge; 5 lần) hoặc tấn công từ IP <code>${event.ip || "nguồn"}</code>.<br><strong>Khuyến nghị ứng phó:</strong> Kích hoạt chính sách Firewall/iptables khóa tức thời IP nguồn (Drop packet), cô lập cổng SSH/RDP và thu hồi phiên người dùng bị ảnh hưởng.`;
        } else if (sev === "WARNING" || type.includes("WARNING") || type.includes("LOGIN_FAILED") || msg.includes("failed") || msg.includes("tre cao")) {
          adviceEl.className = "advice-box advice-warning";
          adviceEl.innerHTML = `<strong>⚡ CẢNH BÁO TIỀM TÀNG:</strong> Ghi nhận 3–4 lần đăng nhập sai hoặc độ trễ mạng vượt ngưỡng chuẩn (200ms – 400ms).<br><strong>Khuyến nghị ứng phó:</strong> Giám sát liên tục IP <code>${event.ip || "nguồn"}</code>, chuẩn bị cơ chế Rate Limiting / CAPTCHA để ngăn chặn leo thang thành tấn công Brute-force.`;
        } else if (type.includes("LOGIN_SUCCESS") || msg.includes("accepted")) {
          adviceEl.className = "advice-box";
          adviceEl.innerHTML = `<strong>✅ XÁC THỰC THÀNH CÔNG:</strong> Phiên đăng nhập hợp lệ từ IP <code>${event.ip || "127.0.0.1"}</code> đã được hệ thống cấp quyền truy cập an toàn.`;
        } else if (type.includes("TRAFFIC") || type.includes("NETWORK")) {
          adviceEl.className = "advice-box";
          adviceEl.innerHTML = `<strong>📊 GIÁM SÁT HẠ TẦNG:</strong> Đo kiểm viễn trắc mạng định kỳ của thiết bị mạng. Băng thông và độ trễ phản hồi đang ở mức ổn định.`;
        } else {
          adviceEl.className = "advice-box";
          adviceEl.innerHTML = `<strong>ℹ️ NHẬT KÝ BÌNH THƯỜNG:</strong> Sự kiện vận hành tiêu chuẩn của hệ thống và dịch vụ mạng. Không phát hiện dấu hiệu bất thường.`;
        }
      }

      const targetIp = (event.ip || "").trim();
      const isBlocked = Boolean(window.isIpBlocked && window.isIpBlocked(targetIp));

      const blockBtn = byId("modal-block-ip-btn");
      if (blockBtn) {
        blockBtn.dataset.ip = targetIp;
        if (!targetIp || targetIp === "127.0.0.1" || targetIp === "localhost") {
          blockBtn.disabled = true;
          blockBtn.style.display = "inline-flex";
          blockBtn.textContent = `⛔ Chặn IP (${targetIp || 'Loopback'})`;
          blockBtn.title = "Không thể chặn IP loopback cục bộ";
        } else if (isBlocked) {
          blockBtn.style.display = "none";
        } else {
          blockBtn.disabled = false;
          blockBtn.style.display = "inline-flex";
          blockBtn.textContent = `⛔ Chặn IP [${targetIp}] & Xóa log CSDL`;
          blockBtn.title = `Khóa IP ${targetIp} vào Firewall và xóa toàn bộ log của IP này khỏi SQLite`;
        }
      }

      const unblockBtn = byId("modal-unblock-ip-btn");
      if (unblockBtn) {
        unblockBtn.dataset.ip = targetIp;
        if (isBlocked && targetIp) {
          unblockBtn.style.display = "inline-flex";
          unblockBtn.textContent = `🔓 Bỏ chặn IP [${targetIp}]`;
          unblockBtn.title = `Mở khóa IP ${targetIp} để máy chủ tiếp tục nhận gói tin`;
        } else {
          unblockBtn.style.display = "none";
        }
      }

      if (typeof dialog.showModal === "function") {
        dialog.showModal();
      } else {
        dialog.setAttribute("open", "");
      }
    }

    const closeDialog = () => {
      const dialog = byId("log-detail-dialog");
      if (dialog) dialog.close();
    };
    const modalClose = byId("modal-close-btn");
    const modalOk = byId("modal-ok-btn");
    if (modalClose) modalClose.addEventListener("click", closeDialog);
    if (modalOk) modalOk.addEventListener("click", closeDialog);
    const dialogEl = byId("log-detail-dialog");
    if (dialogEl) {
      dialogEl.addEventListener("click", (e) => {
        if (e.target === dialogEl) closeDialog();
      });
    }

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
