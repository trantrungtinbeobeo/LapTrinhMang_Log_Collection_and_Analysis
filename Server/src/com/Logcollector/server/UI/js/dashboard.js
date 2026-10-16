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
      "active-online-users": Math.max(1, snapshot.ipCount),
      "total-alerts": snapshot.alerts.length, "traffic-current": snapshot.current, "peak-rate": snapshot.peak
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
      renderAlerts(snapshot.alerts);
      renderIps(snapshot.topIps);
    }
    logTable.update(snapshot.logs);
  }
  window.renderDashboard = render;

  if (scope) {
    scope.addEventListener("change", () => {
      logTable.reset();
      render();
    });
  }

  function startUpdating() {
    clearInterval(timer);
    render();
    if (!document.hidden) timer = setInterval(render, 1000);
  }
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) clearInterval(timer);
    else startUpdating();
  });
  window.addEventListener("pagehide", () => { clearInterval(timer); clearTimeout(statusTimer); });
  startUpdating();

  // Quan ly cac badge va bieu do Tang truong 200 Nguoi dung
  function updateConcurrencyBadges(m) {
    if (!m) return;
    const uEl = byId("sim-active-users");
    if (uEl) uEl.innerHTML = `${m.users} <small style="font-size: 13px; font-weight: normal; color: #94a3b8;">/ 200 người</small>`;
    const latEl = byId("sim-latency");
    if (latEl) {
      latEl.innerHTML = `${m.latency} <small style="font-size: 13px; font-weight: normal; color: #94a3b8;">ms</small>`;
      latEl.style.color = m.latency > 500 ? "#ef4444" : m.latency > 150 ? "#f59e0b" : "#f8fafc";
    }
    const impEl = byId("sim-shopping-impact");
    if (impEl) {
      impEl.innerHTML = `${m.shoppingImpact}<small style="font-size: 13px; font-weight: normal; color: #94a3b8;">%</small>`;
      impEl.style.color = m.shoppingImpact > 50 ? "#ef4444" : m.shoppingImpact > 20 ? "#f59e0b" : "#f8fafc";
    }
    const errEl = byId("sim-error-rate");
    if (errEl) {
      errEl.innerHTML = `${m.pred404Rate}<small style="font-size: 13px; font-weight: normal; color: #94a3b8;">%</small>`;
      errEl.style.color = m.pred404Rate > 15 ? "#ef4444" : m.pred404Rate > 3 ? "#f59e0b" : "#f87171";
    }
    const badge = byId("concurrency-status-badge");
    if (badge) {
      if (m.users > 150) {
        badge.textContent = "🔴 NGHẼN TẢI NẶNG: >150 Users (Lỗi 404 cao)";
        badge.style.background = "#7f1d1d";
        badge.style.color = "#fecaca";
      } else if (m.users > 80) {
        badge.textContent = "🟡 CẢNH BÁO: 81–150 Users (Bắt đầu chậm)";
        badge.style.background = "#78350f";
        badge.style.color = "#fef3c7";
      } else {
        badge.textContent = "● Tải ổn định: 1–80 Users";
        badge.style.background = "#065f46";
        badge.style.color = "#34d399";
      }
    }
  }
  window.updateConcurrencyBadges = updateConcurrencyBadges;

  // Khoi tao ve bieu do Concurrency ngay khi load trang
  const concContainer = byId("concurrency-chart");
  if (concContainer && window.DashboardCharts && window.DashboardCharts.drawConcurrency) {
    window.DashboardCharts.drawConcurrency(concContainer, null);
  }

  // Quan ly Khung Terminal Log Thô (Raw Syslog Stream Console)
  const rawConsole = byId("raw-console");
  const rawCountBadge = byId("raw-count-badge");
  const autoscrollBtn = byId("terminal-autoscroll-btn");
  const clearTerminalBtn = byId("terminal-clear-btn");
  let rawPacketCount = 0;
  let autoScrollRaw = true;

  if (autoscrollBtn) {
    autoscrollBtn.addEventListener("click", () => {
      autoScrollRaw = !autoScrollRaw;
      autoscrollBtn.setAttribute("aria-pressed", String(autoScrollRaw));
      autoscrollBtn.className = `terminal-btn ${autoScrollRaw ? "active" : ""}`;
      autoscrollBtn.textContent = autoScrollRaw ? "Cuộn tự động: BẬT" : "Cuộn tự động: TẮT";
    });
  }

  if (clearTerminalBtn) {
    clearTerminalBtn.addEventListener("click", () => {
      if (rawConsole) {
        rawConsole.innerHTML = '<p class="raw-empty-text">Màn hình terminal đã được xóa. Đang chờ gói tin mới...</p>';
      }
      rawPacketCount = 0;
      if (rawCountBadge) rawCountBadge.textContent = "0 gói tin";
    });
  }

  function appendRawTerminal(rawText, isBlocked = false) {
    if (!rawConsole || !rawText) return;
    const emptyNotice = rawConsole.querySelector(".raw-empty-text");
    if (emptyNotice) emptyNotice.remove();

    rawPacketCount += 1;
    if (rawCountBadge) rawCountBadge.textContent = `${number.format(rawPacketCount)} gói tin`;

    const line = document.createElement("pre");
    line.className = "raw-line";

    const timeSpan = document.createElement("span");
    timeSpan.className = "raw-line-time";
    timeSpan.textContent = `[${time.format(Date.now())}]`;
    line.append(timeSpan);

    if (isBlocked) {
      const dropSpan = document.createElement("span");
      dropSpan.className = "raw-line-drop";
      dropSpan.textContent = rawText;
      line.append(dropSpan);
    } else {
      const priMatch = rawText.match(/^<(\d{1,3})>/);
      if (priMatch) {
        const priSpan = document.createElement("span");
        priSpan.className = "raw-line-pri";
        priSpan.textContent = `<${priMatch[1]}>`;
        line.append(priSpan);
        const textNode = document.createTextNode(rawText.substring(priMatch[0].length));
        line.append(textNode);
      } else {
        line.append(document.createTextNode(rawText));
      }
    }

    rawConsole.appendChild(line);

    while (rawConsole.childNodes.length > 150) {
      rawConsole.removeChild(rawConsole.firstChild);
    }

    if (autoScrollRaw) {
      rawConsole.scrollTop = rawConsole.scrollHeight;
    }
  }

  // Quan ly Danh sach IP bi khoa (Firewall Blacklist)
  const blacklistCard = byId("firewall-blacklist-card");
  const blacklistChips = byId("firewall-blacklist-chips");
  const blockedIpsSet = new Set();

  function addBlockedIpChip(ip, reason, purged = 0) {
    if (!ip || blockedIpsSet.has(ip)) return;
    blockedIpsSet.add(ip);
    if (blacklistCard) blacklistCard.hidden = false;
    if (!blacklistChips) return;

    const chip = document.createElement("span");
    chip.className = "blacklist-chip";
    chip.title = `Lý do: ${reason || 'Vi phạm an ninh'}`;

    const ipText = document.createElement("span");
    ipText.textContent = `⛔ ${ip}`;

    const purgedBadge = document.createElement("span");
    purgedBadge.className = "blacklist-chip-purged";
    purgedBadge.textContent = `-${purged} log`;
    purgedBadge.title = `Đã thanh lọc xóa ${purged} log khỏi SQLite`;

    chip.append(ipText, purgedBadge);
    blacklistChips.append(chip);
  }

  // Khoi tao tai danh sach IP da bi chan tu Server
  fetch("/api/block-ip")
    .then(r => r.json())
    .then(data => {
      if (data && data.blockedIps && data.blockedIps.length) {
        data.blockedIps.forEach(ip => addBlockedIpChip(ip, "Khởi tạo Firewall", 0));
      }
    })
    .catch(() => {});

  // Xu ly nut bam Chan IP thu cong tu Dialog Modal
  const modalBlockBtn = byId("modal-block-ip-btn");
  if (modalBlockBtn) {
    modalBlockBtn.addEventListener("click", async () => {
      const targetIp = modalBlockBtn.dataset.ip;
      if (!targetIp || targetIp === "127.0.0.1") return;

      const confirmBlock = confirm(
        `[XÁC NHẬN AN NINH MẠNG]\n\nBạn có chắc chắn muốn KHÓA địa chỉ IP ${targetIp} vào Firewall?\n\n- Toàn bộ gói tin tiếp theo từ IP này sẽ bị DROP.\n- Toàn bộ log độc hại của IP này sẽ bị XÓA SẠCH khỏi CSDL SQLite để đảm bảo tính toàn vẹn.`
      );
      if (!confirmBlock) return;

      try {
        const resp = await fetch("/api/block-ip", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ip: targetIp, reason: "Quản trị viên chặn thủ công từ Web Dashboard" })
        });
        const result = await resp.json();
        const dialog = byId("log-detail-dialog");
        if (dialog) dialog.close();
        announce(`⛔ Đã kích hoạt Firewall chặn IP ${targetIp}! Đã thanh lọc ${result.purged || 0} log khỏi SQLite.`);
      } catch (err) {
        alert("Lỗi khi gửi yêu cầu khóa IP tới Server: " + err.message);
      }
    });
  }

  // Xu ly nut bam Xoa toan bo log khoi giao dien va localStorage
  const clearAllLogsBtn = byId("btn-clear-all-logs");
  if (clearAllLogsBtn) {
    clearAllLogsBtn.addEventListener("click", () => {
      const confirmed = confirm(
        "[XÁC NHẬN XÓA LỊCH SỬ LOG]\n\nBạn có chắc chắn muốn XÓA TOÀN BỘ lịch sử log trên Dashboard không?\n\n- Toàn bộ log trong bộ nhớ và bảng sẽ được làm trống.\n- Biểu đồ lưu lượng và tỷ lệ sẽ trở về ban đầu.\n\n(Lưu ý: Log chỉ bị xóa khi bạn bấm nút này, việc tải lại trang/F5 sẽ không bao giờ làm mất log)."
      );
      if (!confirmed) return;

      if (window.DashboardData && window.DashboardData.clearAllLogs) {
        window.DashboardData.clearAllLogs();
      }
      if (rawConsole) {
        rawConsole.innerHTML = '<p class="raw-empty-text">Màn hình terminal đã được xóa. Đang chờ gói tin mới...</p>';
      }
      rawPacketCount = 0;
      if (rawCountBadge) rawCountBadge.textContent = "0 gói tin";
      contentSignature = null;
      render();
      announce("✓ Đã xóa toàn bộ lịch sử log trên giao diện!");
    });
  }

  // Ket noi WebSocket Real-time toi ServerMain qua cong 8085
  function connectWebSocket() {
    try {
      const badge = document.querySelector(".demo-badge");
      const wsHost = window.location.hostname || "localhost";
      const ws = new WebSocket("ws://" + wsHost + ":8085");

      ws.onopen = () => {
        if (badge) {
          badge.textContent = "● ĐÃ KẾT NỐI REALTIME (ws://" + wsHost + ":8085)";
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

          if (data.type === "IP_BLOCKED") {
            if (window.DashboardData && window.DashboardData.purgeLogsByIp) {
              window.DashboardData.purgeLogsByIp(data.ip);
            }
            addBlockedIpChip(data.ip, data.reason, data.purged);
            appendRawTerminal(`[FIREWALL BLACKLIST] ĐÃ KHÓA IP: ${data.ip} | Lý do: ${data.reason || 'CRITICAL'} | Đã xóa ${data.purged} log khỏi SQLite`, true);
            announce(`⛔ AN NINH: Đã khóa IP ${data.ip} và xóa sạch ${data.purged} log độc hại khỏi CSDL!`);
            render();
            return;
          }

          if (data.raw) {
            appendRawTerminal(data.raw);
          } else if (data.message) {
            appendRawTerminal(data.message);
          }

          // Xu ly du lieu do luong va du bao tang truong 200 nguoi dung
          const rawContent = (data.raw || data.message || "");
          if (rawContent.includes("ActiveUsers:")) {
            const mUsers = rawContent.match(/ActiveUsers:\s*(\d+)/i);
            const mLat = rawContent.match(/Latency:\s*(\d+)ms/i);
            const mImpact = rawContent.match(/(?:ShoppingImpact|Impact):\s*([0-9.]+)%/i);
            const mPred404 = rawContent.match(/(?:Pred404|ErrorRate):\s*([0-9.]+)%/i);
            if (mUsers) {
              const u = parseInt(mUsers[1], 10);
              const lat = mLat ? parseInt(mLat[1], 10) : 18;
              const imp = mImpact ? parseFloat(mImpact[1]) : 0;
              const p404 = mPred404 ? parseFloat(mPred404[1]) : 0;
              const simData = { activeUsers: u, latency: lat, shoppingImpact: imp, pred404Rate: p404 };
              updateConcurrencyBadges(simData);
              const chartEl = byId("concurrency-chart");
              if (chartEl && window.DashboardCharts && window.DashboardCharts.drawConcurrency) {
                window.DashboardCharts.drawConcurrency(chartEl, simData);
              }
            }
          }

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
        setTimeout(connectWebSocket, 3000);
      };

      ws.onerror = () => {
        ws.close();
      };
    } catch (e) {
      // Giu nguyen che do demo neu khong ket noi duoc
    }
  }

  connectWebSocket();
})();
