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
    const points = snapshot.rates.map((rate, index) => `${x(index).toFixed(2)},${y(rate).toFixed(2)}`).join(" ");
    svg.append(svgElement("polygon", { points: `${left},${y(0)} ${points} ${x(299)},${y(0)}`, fill: "#2563eb", "fill-opacity": ".09" }));
    svg.append(svgElement("polyline", { points, fill: "none", stroke: "#2563eb", "stroke-width": 2, "stroke-linejoin": "round" }));
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

    const tooltipGroup = svgElement("g", { class: "traffic-tooltip", style: "display: none; pointer-events: none;" });
    const tooltipBg = svgElement("rect", {
      class: "traffic-tooltip-bg", width: "185", height: "76", rx: "8",
      fill: "#0f172a", "fill-opacity": "0.96", stroke: "#334155", "stroke-width": "1"
    });
    const timeText = svgElement("text", { class: "traffic-tooltip-time", x: "10", y: "18", fill: "#94a3b8" });
    const rateText = svgElement("text", { class: "traffic-tooltip-rate", x: "10", y: "36", fill: "#38bdf8", "font-weight": "700" });
    const statusText = svgElement("text", { class: "traffic-tooltip-status", x: "10", y: "53", fill: "#4ade80", "font-weight": "600" });
    const streamText = svgElement("text", { class: "traffic-tooltip-stream", x: "10", y: "69", fill: "#cbd5e1" });

    tooltipGroup.append(tooltipBg, timeText, rateText, statusText, streamText);

    // Vung bat su kien chuot (Transparent Overlay)
    const overlay = svgElement("rect", {
      x: left, y: top, width: plotWidth, height: plotHeight,
      fill: "transparent", style: "cursor: crosshair; pointer-events: all;"
    });

    function getStreamDetails(secIndex) {
      const secStart = snapshot.start + secIndex * 1000;
      const secEnd = secStart + 1000;
      const matchingLogs = (snapshot.logs || []).filter(l => l.at >= secStart && l.at < secEnd);
      const ipCounts = {};
      const catCounts = {};
      matchingLogs.forEach(l => {
        const ip = l.ip || "127.0.0.1";
        ipCounts[ip] = (ipCounts[ip] || 0) + 1;
        const cat = l.category || l.type || "TRAFFIC";
        catCounts[cat] = (catCounts[cat] || 0) + 1;
      });
      let topIp = "127.0.0.1", maxIpCount = 0;
      Object.entries(ipCounts).forEach(([ip, cnt]) => {
        if (cnt > maxIpCount) { maxIpCount = cnt; topIp = ip; }
      });
      let topCat = "Lưu lượng chuẩn", maxCatCount = 0;
      Object.entries(catCounts).forEach(([cat, cnt]) => {
        if (cnt > maxCatCount) { maxCatCount = cnt; topCat = cat; }
      });
      return { topIp, maxIpCount, topCat, count: matchingLogs.length };
    }

    overlay.addEventListener("pointermove", (e) => {
      const rect = overlay.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const clampedX = Math.max(0, Math.min(plotWidth, mouseX));
      const index = Math.round((clampedX / plotWidth) * 299);
      const rate = snapshot.rates[index] || 0;
      const cx = x(index);
      const cy = y(rate);
      const isSpike = rate >= 15 || (rate >= 8 && rate > snapshot.average * 2.2);

      cursorLine.setAttribute("x1", cx);
      cursorLine.setAttribute("x2", cx);
      cursorLine.style.display = "block";

      cursorDot.setAttribute("cx", cx);
      cursorDot.setAttribute("cy", cy);
      cursorDot.setAttribute("fill", isSpike ? "#dc2626" : "#2563eb");
      cursorDot.style.display = "block";

      const stream = getStreamDetails(index);
      const atTime = new Date(snapshot.start + index * 1000);
      const diffSec = Math.max(0, Math.round((snapshot.end - atTime.getTime()) / 1000));
      const minAgo = (diffSec / 60).toFixed(1);

      timeText.textContent = `⏱️ ${time.format(atTime)} (−${minAgo} phút)`;
      rateText.textContent = `📈 ${rate} log/giây ${isSpike ? "⚡ [ĐỘT BIẾN]" : ""}`;
      rateText.setAttribute("fill", isSpike ? "#f87171" : "#38bdf8");

      statusText.textContent = isSpike
        ? `⚠️ ĐỘT BIẾN: ${stream.topCat}`
        : `● Trạng thái luồng: Ổn định`;
      statusText.setAttribute("fill", isSpike ? "#fbbf24" : "#4ade80");

      streamText.textContent = stream.count > 0
        ? `🌐 Luồng IP: ${stream.topIp} (${stream.maxIpCount} log)`
        : `🌐 Luồng định kỳ hệ thống`;

      let tooltipX = cx + 12;
      if (tooltipX + 190 > width - right) {
        tooltipX = cx - 198;
      }
      let tooltipY = cy - 42;
      if (tooltipY < top) tooltipY = top + 4;
      if (tooltipY + 76 > height - bottom) tooltipY = height - bottom - 76;

      tooltipGroup.setAttribute("transform", `translate(${tooltipX}, ${tooltipY})`);
      tooltipGroup.style.display = "block";
    });

    overlay.addEventListener("pointerleave", () => {
      cursorLine.style.display = "none";
      cursorDot.style.display = "none";
      tooltipGroup.style.display = "none";
    });

    svg.append(cursorLine, cursorDot, tooltipGroup, overlay);
    container.replaceChildren(svg);
  }

  window.DashboardCharts = Object.freeze({ drawSeverity, drawTraffic });
})();
