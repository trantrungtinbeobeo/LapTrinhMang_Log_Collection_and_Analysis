/* SVG rendering only. Aggregation lives in dashboard-data.js; no Java ChartManager dependency. */
(function () {
  "use strict";
  const namespace = "http://www.w3.org/2000/svg";
  const colors = { INFO: "#2563eb", WARNING: "#d97706", ERROR: "#dc2626", CRITICAL: "#7f1d1d" };
  const number = new Intl.NumberFormat("vi-VN");
  const time = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  function svgElement(name, attributes = {}, content) {
    const element = document.createElementNS(namespace, name);
    Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (content !== undefined) element.textContent = content;
    return element;
  }

  function drawSeverity(container, legend, counts) {
    const total = Object.values(counts).reduce((sum, count) => sum + count, 0);
    const svg = svgElement("svg", { viewBox: "0 0 200 200", role: "img", "aria-label": `Phân bố severity, tổng ${number.format(total)} log. Chi tiết ở chú giải bên dưới.` });
    const radius = 76;
    const circumference = 2 * Math.PI * radius;
    svg.append(svgElement("circle", { cx: 100, cy: 100, r: radius, fill: "none", stroke: "#eaedff", "stroke-width": 22 }));
    let offset = 0;
    const fragment = document.createDocumentFragment();
    Object.entries(colors).forEach(([level, color]) => {
      const count = counts[level] || 0;
      const proportion = total ? count / total : 0;
      if (count) {
        const length = proportion * circumference;
        svg.append(svgElement("circle", {
          cx: 100, cy: 100, r: radius, fill: "none", stroke: color, "stroke-width": 22,
          "stroke-dasharray": `${length} ${circumference - length}`, "stroke-dashoffset": -offset,
          transform: "rotate(-90 100 100)"
        }));
        offset += length;
      }
      const item = document.createElement("li");
      const label = document.createElement("span");
      label.className = "legend-label";
      const swatch = document.createElement("span");
      swatch.className = `legend-swatch swatch-${level.toLowerCase()}`;
      swatch.setAttribute("aria-hidden", "true");
      label.append(swatch, level);
      const value = document.createElement("strong");
      value.textContent = `${number.format(count)} · ${(proportion * 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`;
      item.append(label, value);
      fragment.append(item);
    });
    svg.append(svgElement("text", { x: 100, y: 101, "text-anchor": "middle", class: "donut-total" }, number.format(total)));
    svg.append(svgElement("text", { x: 100, y: 121, "text-anchor": "middle", class: "donut-label" }, "log / 5 phút"));
    container.replaceChildren(svg);
    legend.replaceChildren(fragment);
  }

  function getSeverityColor(severity) {
    switch ((severity || "").toUpperCase()) {
      case "CRITICAL": return "#ef4444";
      case "ERROR": return "#dc2626";
      case "WARNING": return "#f59e0b";
      case "INFO": default: return "#3b82f6";
    }
  }

  function drawTraffic(container, snapshot) {
    const width = 480, height = 260, left = 34, right = 12, top = 20, bottom = 36;
    const plotWidth = width - left - right, plotHeight = height - top - bottom;
    const maximum = Math.max(5, Math.ceil(snapshot.peak / 5) * 5);
    const x = index => left + index / (snapshot.rates.length - 1) * plotWidth;
    const y = count => top + plotHeight - count / maximum * plotHeight;
    const svg = svgElement("svg", {
      viewBox: `0 0 ${width} ${height}`, role: "img",
      "aria-label": `Lưu lượng log từ ${time.format(snapshot.start)} đến ${time.format(snapshot.end)}. Hiện tại ${snapshot.current} log mỗi giây; đỉnh ${snapshot.peak} log mỗi giây. ${snapshot.total ? "" : "Chưa có dữ liệu."}`
    });

    for (let step = 0; step <= 5; step += 1) {
      const value = maximum / 5 * step;
      svg.append(svgElement("line", { x1: left, y1: y(value), x2: width - right, y2: y(value), class: "chart-grid" }));
      svg.append(svgElement("text", { x: left - 7, y: y(value) + 3, "text-anchor": "end", class: "chart-axis" }, number.format(value)));
    }

    // 1. Ve vung song dien tich (Area Wave Background)
    const points = snapshot.rates.map((rate, index) => `${x(index).toFixed(2)},${y(rate).toFixed(2)}`).join(" ");
    svg.append(svgElement("polygon", { points: `${left},${y(0)} ${points} ${x(299)},${y(0)}`, fill: "#2563eb", "fill-opacity": ".08" }));
    svg.append(svgElement("polyline", { points, fill: "none", stroke: "#2563eb", "stroke-width": 1.8, "stroke-linejoin": "round" }));

    // 2. Ve cac THANH (Bars) truc quan - Mau sac thay doi dua vao phan loai log
    const barsGroup = svgElement("g", { class: "traffic-bars-group" });
    const barWidth = Math.max(3.2, Math.min(8, (plotWidth / 300) * 2.6));

    snapshot.rates.forEach((rate, index) => {
      if (rate > 0) {
        const secD = (snapshot.secDetails && snapshot.secDetails[index]) || null;
        const highestSev = secD ? secD.highestSeverity : "INFO";
        const barColor = getSeverityColor(highestSev);
        const isSpike = rate >= 15 || (rate >= 8 && rate > snapshot.average * 2.2);

        const bx = x(index) - barWidth / 2;
        const by = y(rate);
        const bh = Math.max(2, y(0) - by);

        const barRect = svgElement("rect", {
          x: bx.toFixed(1), y: by.toFixed(1),
          width: barWidth.toFixed(1), height: bh.toFixed(1),
          rx: 1.5, ry: 1.5,
          fill: barColor,
          "fill-opacity": isSpike ? "0.95" : "0.75",
          stroke: isSpike ? "#ffffff" : barColor,
          "stroke-width": isSpike ? "1" : "0.5",
          class: `traffic-bar ${isSpike ? "traffic-bar-spike" : ""}`
        });
        barsGroup.append(barRect);
      }
    });
    svg.append(barsGroup);

    [0, 60, 120, 180, 240, 299].forEach((index, tick) => {
      svg.append(svgElement("text", { x: x(index), y: height - 12, "text-anchor": tick === 0 ? "start" : tick === 5 ? "end" : "middle", class: "chart-axis" }, tick === 5 ? "Hiện tại" : `−${5 - tick} phút`));
    });
    if (!snapshot.total) svg.append(svgElement("text", { x: width / 2, y: height / 2, "text-anchor": "middle", class: "chart-axis" }, "Chưa có log trong 5 phút gần nhất"));

    // Cac thanh phan tuong tac khi re chuot (Hover Tooltip & Spike Indicator)
    const cursorLine = svgElement("line", {
      x1: left, y1: top, x2: left, y2: top + plotHeight,
      stroke: "#60a5fa", "stroke-width": "1.5", "stroke-dasharray": "3,3",
      style: "display: none; pointer-events: none;"
    });

    const cursorDot = svgElement("circle", {
      cx: left, cy: top, r: "5.5", stroke: "#ffffff", "stroke-width": "2", fill: "#2563eb",
      style: "display: none; pointer-events: none;"
    });

    const activeBarHighlight = svgElement("rect", {
      x: 0, y: top, width: barWidth + 4, height: plotHeight,
      fill: "none", stroke: "#ffffff", "stroke-width": "1.5", rx: 2,
      style: "display: none; pointer-events: none;"
    });

    const tooltipGroup = svgElement("g", { class: "traffic-tooltip", style: "display: none; pointer-events: none;" });
    const tooltipBg = svgElement("rect", {
      class: "traffic-tooltip-bg", width: "220", height: "92", rx: "8",
      fill: "#0f172a", "fill-opacity": "0.96", stroke: "#334155", "stroke-width": "1.2"
    });
    const timeText = svgElement("text", { class: "traffic-tooltip-time", x: "10", y: "17", fill: "#94a3b8", "font-size": "11px" });
    const rateText = svgElement("text", { class: "traffic-tooltip-rate", x: "10", y: "35", fill: "#38bdf8", "font-weight": "700", "font-size": "13px" });
    const statusText = svgElement("text", { class: "traffic-tooltip-status", x: "10", y: "52", fill: "#4ade80", "font-weight": "600", "font-size": "11px" });
    const ratioText = svgElement("text", { class: "traffic-tooltip-ratio", x: "10", y: "68", fill: "#e2e8f0", "font-size": "10.5px" });
    const streamText = svgElement("text", { class: "traffic-tooltip-stream", x: "10", y: "84", fill: "#94a3b8", "font-size": "10.5px" });

    tooltipGroup.append(tooltipBg, timeText, rateText, statusText, ratioText, streamText);

    // Vung bat su kien chuot (Transparent Overlay)
    const overlay = svgElement("rect", {
      x: left, y: top, width: plotWidth, height: plotHeight,
      fill: "transparent", style: "cursor: crosshair; pointer-events: all;"
    });

    overlay.addEventListener("pointermove", (e) => {
      const rect = overlay.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const clampedX = Math.max(0, Math.min(plotWidth, mouseX));
      const rawIndex = Math.round((clampedX / plotWidth) * 299);

      // CO CHE TU TINH (Magnetic Snapping):
      // Tu dong hut chuot vao dinh dot bien (Spike / Bar) trong pham vi ±12 giay (~18px)
      let index = rawIndex;
      let maxNearRate = snapshot.rates[rawIndex] || 0;
      const searchRadius = 12;
      for (let offset = -searchRadius; offset <= searchRadius; offset++) {
        const ni = rawIndex + offset;
        if (ni >= 0 && ni < snapshot.rates.length) {
          const nr = snapshot.rates[ni] || 0;
          if (nr > maxNearRate) {
            maxNearRate = nr;
            index = ni;
          }
        }
      }

      const rate = snapshot.rates[index] || 0;
      const secD = (snapshot.secDetails && snapshot.secDetails[index]) || null;
      const highestSev = secD ? secD.highestSeverity : "INFO";
      const sevColor = getSeverityColor(highestSev);
      const isSpike = rate >= 15 || (rate >= 8 && rate > snapshot.average * 2.2);

      const cx = x(index);
      const cy = y(rate);

      cursorLine.setAttribute("x1", cx);
      cursorLine.setAttribute("x2", cx);
      cursorLine.setAttribute("stroke", sevColor);
      cursorLine.style.display = "block";

      cursorDot.setAttribute("cx", cx);
      cursorDot.setAttribute("cy", cy);
      cursorDot.setAttribute("fill", sevColor);
      cursorDot.setAttribute("r", isSpike ? "6.5" : "5");
      cursorDot.style.display = "block";

      if (rate > 0) {
        activeBarHighlight.setAttribute("x", cx - (barWidth + 4) / 2);
        activeBarHighlight.setAttribute("y", cy - 2);
        activeBarHighlight.setAttribute("height", Math.max(4, y(0) - cy + 2));
        activeBarHighlight.setAttribute("stroke", sevColor);
        activeBarHighlight.style.display = "block";
      } else {
        activeBarHighlight.style.display = "none";
      }

      const atTime = new Date(snapshot.start + index * 1000);
      const diffSec = Math.max(0, Math.round((snapshot.end - atTime.getTime()) / 1000));
      const minAgo = (diffSec / 60).toFixed(1);

      timeText.textContent = `⏱️ ${time.format(atTime)} (−${minAgo} phút)`;
      rateText.textContent = `📈 ${rate} log/giây ${isSpike ? "⚡ [ĐỘT BIẾN LƯU LƯỢNG]" : ""}`;
      rateText.setAttribute("fill", isSpike ? sevColor : "#38bdf8");

      let statusMsg = "● Trạng thái: Ổn định";
      if (highestSev === "CRITICAL") {
        statusMsg = "🔴 NGUY HIỂM: " + (secD ? secD.dominantCat : "CRITICAL");
      } else if (highestSev === "ERROR") {
        statusMsg = "🛑 LỖI HỆ THỐNG: " + (secD ? secD.dominantCat : "ERROR");
      } else if (highestSev === "WARNING") {
        statusMsg = "🟡 CẢNH BÁO NGHẼN: " + (secD ? secD.dominantCat : "WARNING");
      } else if (isSpike) {
        statusMsg = "🔵 ĐỘT BIẾN TẢI: " + (secD ? secD.dominantCat : "INFO");
      }
      statusText.textContent = statusMsg;
      statusText.setAttribute("fill", sevColor);

      if (secD && secD.counts) {
        const parts = [];
        if (secD.counts.CRITICAL) parts.push(`${secD.counts.CRITICAL} CRITICAL`);
        if (secD.counts.ERROR) parts.push(`${secD.counts.ERROR} ERROR`);
        if (secD.counts.WARNING) parts.push(`${secD.counts.WARNING} WARNING`);
        if (secD.counts.INFO) parts.push(`${secD.counts.INFO} INFO`);
        ratioText.textContent = `📊 Phân loại: ${parts.join(" · ") || "0 log"}`;
        streamText.textContent = `📱 Thiết bị: ${secD.topDevice || 'Máy client'} · IP: ${secD.topIp || '127.0.0.1'}`;
      } else {
        ratioText.textContent = `📊 Phân loại: Chưa có lưu lượng`;
        streamText.textContent = `🌐 Luồng định kỳ hệ thống`;
      }

      let tooltipX = cx + 12;
      if (tooltipX + 225 > width - right) {
        tooltipX = cx - 232;
      }
      let tooltipY = cy - 46;
      if (tooltipY < top) tooltipY = top + 4;
      if (tooltipY + 92 > height - bottom) tooltipY = height - bottom - 92;

      tooltipGroup.setAttribute("transform", `translate(${tooltipX}, ${tooltipY})`);
      tooltipGroup.style.display = "block";
    });

    overlay.addEventListener("pointerleave", () => {
      cursorLine.style.display = "none";
      cursorDot.style.display = "none";
      activeBarHighlight.style.display = "none";
      tooltipGroup.style.display = "none";
    });

    svg.append(cursorLine, activeBarHighlight, cursorDot, tooltipGroup, overlay);
    container.replaceChildren(svg);
  }

  window.DashboardCharts = Object.freeze({ drawSeverity, drawTraffic });
})();
