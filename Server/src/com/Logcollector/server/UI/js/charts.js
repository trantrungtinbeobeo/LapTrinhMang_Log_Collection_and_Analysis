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

    // Label nho gon gang (Mini Tooltip Badge) theo yeu cau nguoi dung
    const tooltipGroup = svgElement("g", { class: "traffic-tooltip", style: "display: none; pointer-events: none;" });
    const tooltipBg = svgElement("rect", {
      class: "traffic-tooltip-bg", width: "205", height: "56", rx: "6", ry: "6",
      fill: "#0f172a", "fill-opacity": "0.96", stroke: "#334155", "stroke-width": "1.2"
    });
    const dotIndicator = svgElement("circle", { cx: "12", cy: "15", r: "3.5", fill: "#38bdf8" });
    const speedText = svgElement("text", { x: "21", y: "19", fill: "#f8fafc", "font-weight": "700", "font-size": "12px" });
    const timeText = svgElement("text", { x: "105", y: "19", fill: "#94a3b8", "font-size": "9.5px" });
    const totalLogText = svgElement("text", { x: "12", y: "35", fill: "#38bdf8", "font-weight": "600", "font-size": "11px" });
    const catText = svgElement("text", { x: "12", y: "49", fill: "#94a3b8", "font-size": "9.5px" });

    tooltipGroup.append(tooltipBg, dotIndicator, speedText, timeText, totalLogText, catText);

    // Vung bat su kien chuot (Transparent Overlay)
    const overlay = svgElement("rect", {
      x: left, y: top, width: plotWidth, height: plotHeight,
      fill: "transparent", style: "cursor: crosshair; pointer-events: all;"
    });

    function updateHover(clientX, clientY) {
      const rect = overlay.getBoundingClientRect();
      if (!rect || rect.width <= 0) return;
      // Chuan hoa toa do chuot tu man hinh sang ty le thuc te cua bieu do [0..1]
      const normalizedX = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
      const rawIndex = Math.min(299, Math.round(normalizedX * 299));

      // Co che tu tinh (Magnetic Snapping):
      // Tu dong hut chuot vao cot dinh dot bien gan nhat trong pham vi ±12 giay
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
      cursorDot.setAttribute("r", isSpike ? "6" : "4.5");
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

      dotIndicator.setAttribute("fill", sevColor);
      speedText.textContent = `${rate} log/giây`;
      timeText.textContent = `· ${time.format(atTime)} (−${minAgo}m)`;
      totalLogText.textContent = `📊 Tổng log: ${number.format(snapshot.total)} gói tin`;
      catText.textContent = isSpike ? `⚡ ĐỘT BIẾN LƯU LƯỢNG [${highestSev}]` : (rate > 0 ? `● ${highestSev} (${secD ? secD.dominantCat : 'TẢI MẠNG'})` : `● Hệ thống ổn định (0 log)`);
      catText.setAttribute("fill", isSpike ? sevColor : "#94a3b8");

      let tooltipX = cx + 12;
      const tipWidth = 205;
      const tipHeight = 56;
      if (tooltipX + tipWidth > width - right) {
        tooltipX = cx - tipWidth - 12;
      }
      let tooltipY = cy - 28;
      if (tooltipY < top) tooltipY = top + 4;
      if (tooltipY + tipHeight > height - bottom) tooltipY = height - bottom - tipHeight;

      tooltipGroup.setAttribute("transform", `translate(${tooltipX}, ${tooltipY})`);
      tooltipGroup.style.display = "block";
    }

    overlay.addEventListener("pointermove", (e) => {
      container._hoverState = { active: true, clientX: e.clientX, clientY: e.clientY };
      updateHover(e.clientX, e.clientY);
    });

    overlay.addEventListener("pointerleave", () => {
      container._hoverState = { active: false };
      cursorLine.style.display = "none";
      cursorDot.style.display = "none";
      activeBarHighlight.style.display = "none";
      tooltipGroup.style.display = "none";
    });

    // Neu truoc do nguoi dung dang de chuot o day thi lap tuc tai hien vi tri hover ngay sau khi render
    if (container._hoverState && container._hoverState.active) {
      updateHover(container._hoverState.clientX, container._hoverState.clientY);
    }

    svg.append(cursorLine, activeBarHighlight, cursorDot, tooltipGroup, overlay);
    container.replaceChildren(svg);
  }

  function drawConcurrency(container, liveData) {
    if (!container) return;
    const width = 960, height = 240, left = 48, right = 48, top = 20, bottom = 32;
    const plotWidth = width - left - right, plotHeight = height - top - bottom;
    const maxUsers = 200;
    const maxLatency = 1200;
    const maxErrorRate = 40;

    const x = u => left + (u / maxUsers) * plotWidth;
    const yLatency = lat => top + plotHeight - (Math.min(maxLatency, lat) / maxLatency) * plotHeight;
    const yError = err => top + plotHeight - (Math.min(maxErrorRate, err) / maxErrorRate) * plotHeight;

    function calcMetrics(u) {
      let lat, impact, err404;
      if (u <= 80) {
        lat = 18 + u * 0.9;
        impact = (u / 80.0) * 8.0;
        err404 = 0.05 + (u / 80.0) * 0.45;
      } else if (u <= 150) {
        const extra = u - 80;
        lat = 90 + Math.pow(extra, 1.45) * 2.2;
        impact = 10.0 + (extra / 70.0) * 50.0;
        err404 = 1.0 + (extra / 70.0) * 12.0;
      } else {
        const extra = u - 150;
        lat = 450 + Math.pow(extra, 1.6) * 7.5;
        impact = 65.0 + (extra / 50.0) * 30.0;
        err404 = 15.0 + (extra / 50.0) * 20.0;
      }
      return {
        users: u,
        latency: Math.round(lat),
        shoppingImpact: Number(impact.toFixed(1)),
        pred404Rate: Number(err404.toFixed(1))
      };
    }

    const svg = svgElement("svg", {
      viewBox: `0 0 ${width} ${height}`, role: "img",
      style: "width: 100%; height: 100%; display: block;",
      "aria-label": "Biểu đồ mô hình tăng trưởng 200 người dùng đồng thời và dự báo độ trễ + lỗi 404"
    });

    // 1. Ve cac vung phan cap chiu tai (Zones)
    const x80 = x(80), x150 = x(150), x200 = x(200);
    svg.append(svgElement("rect", {
      x: left, y: top, width: x80 - left, height: plotHeight,
      fill: "#10b981", "fill-opacity": "0.06"
    }));
    svg.append(svgElement("rect", {
      x: x80, y: top, width: x150 - x80, height: plotHeight,
      fill: "#f59e0b", "fill-opacity": "0.08"
    }));
    svg.append(svgElement("rect", {
      x: x150, y: top, width: x200 - x150, height: plotHeight,
      fill: "#ef4444", "fill-opacity": "0.10"
    }));

    // Grid lines & Axis labels
    [0, 300, 600, 900, 1200].forEach(latVal => {
      const yVal = yLatency(latVal);
      svg.append(svgElement("line", { x1: left, y1: yVal, x2: width - right, y2: yVal, class: "chart-grid" }));
      svg.append(svgElement("text", { x: left - 8, y: yVal + 3, "text-anchor": "end", class: "chart-axis", fill: "#f59e0b", "font-size": "10px" }, `${latVal}ms`));
      const errVal = Math.round((latVal / maxLatency) * maxErrorRate);
      svg.append(svgElement("text", { x: width - right + 8, y: yVal + 3, "text-anchor": "start", class: "chart-axis", fill: "#f43f5e", "font-size": "10px" }, `${errVal}%`));
    });

    // X Axis ticks: 0, 50, 80, 100, 150, 200 users
    [0, 50, 80, 100, 150, 200].forEach(uVal => {
      const xVal = x(uVal);
      svg.append(svgElement("line", { x1: xVal, y1: top, x2: xVal, y2: top + plotHeight, stroke: "#334155", "stroke-dasharray": "2,4" }));
      svg.append(svgElement("text", {
        x: xVal, y: height - 10, "text-anchor": "middle", class: "chart-axis",
        fill: uVal > 150 ? "#f87171" : uVal > 80 ? "#fbbf24" : "#94a3b8", "font-size": "10.5px"
      }, `${uVal} Users`));
    });

    // Generate path points for 1 to 200 users
    const latPoints = [];
    const errPoints = [];
    for (let u = 0; u <= maxUsers; u += 2) {
      const m = calcMetrics(u);
      latPoints.push(`${x(u).toFixed(1)},${yLatency(m.latency).toFixed(1)}`);
      errPoints.push(`${x(u).toFixed(1)},${yError(m.pred404Rate).toFixed(1)}`);
    }

    svg.append(svgElement("polyline", {
      points: latPoints.join(" "), fill: "none", stroke: "#f59e0b", "stroke-width": 2.5, "stroke-linejoin": "round"
    }));

    svg.append(svgElement("polyline", {
      points: errPoints.join(" "), fill: "none", stroke: "#f43f5e", "stroke-width": 2.2, "stroke-dasharray": "4,3", "stroke-linejoin": "round"
    }));

    // Current live progress marker
    const curU = (liveData && liveData.activeUsers) ? liveData.activeUsers : 0;
    if (curU > 0) {
      const liveX = x(curU);
      const liveM = calcMetrics(curU);
      svg.append(svgElement("line", {
        x1: liveX, y1: top, x2: liveX, y2: top + plotHeight,
        stroke: "#38bdf8", "stroke-width": "2"
      }));
      svg.append(svgElement("circle", {
        cx: liveX, cy: yLatency(liveM.latency), r: "6",
        fill: "#38bdf8", stroke: "#ffffff", "stroke-width": "2"
      }));
    }

    // Interactive Hover Elements
    const hoverLine = svgElement("line", {
      x1: left, y1: top, x2: left, y2: top + plotHeight,
      stroke: "#cbd5e1", "stroke-width": "1.2", "stroke-dasharray": "3,3",
      style: "display: none; pointer-events: none;"
    });
    const hoverDotLat = svgElement("circle", {
      cx: left, cy: top, r: "5.5", fill: "#f59e0b", stroke: "#ffffff", "stroke-width": "2",
      style: "display: none; pointer-events: none;"
    });
    const hoverDotErr = svgElement("circle", {
      cx: left, cy: top, r: "5.5", fill: "#f43f5e", stroke: "#ffffff", "stroke-width": "2",
      style: "display: none; pointer-events: none;"
    });

    const tipGroup = svgElement("g", { style: "display: none; pointer-events: none;" });
    const tipBg = svgElement("rect", {
      width: "250", height: "88", rx: "8",
      fill: "#0f172a", "fill-opacity": "0.96", stroke: "#334155", "stroke-width": "1.2"
    });
    const tipUsers = svgElement("text", { x: "10", y: "18", fill: "#38bdf8", "font-weight": "700", "font-size": "11.5px" });
    const tipLat = svgElement("text", { x: "10", y: "36", fill: "#fbbf24", "font-size": "11px" });
    const tipImpact = svgElement("text", { x: "10", y: "54", fill: "#cbd5e1", "font-size": "10.5px" });
    const tip404 = svgElement("text", { x: "10", y: "72", fill: "#f87171", "font-weight": "600", "font-size": "11px" });
    tipGroup.append(tipBg, tipUsers, tipLat, tipImpact, tip404);

    const overlay = svgElement("rect", {
      x: left, y: top, width: plotWidth, height: plotHeight,
      fill: "transparent", style: "cursor: crosshair; pointer-events: all;"
    });

    overlay.addEventListener("pointermove", (e) => {
      const rect = overlay.getBoundingClientRect();
      if (!rect || rect.width <= 0) return;
      const normalizedX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const u = Math.max(1, Math.min(maxUsers, Math.round(normalizedX * maxUsers)));
      const m = calcMetrics(u);

      const cx = x(u);
      const cyLat = yLatency(m.latency);
      const cyErr = yError(m.pred404Rate);

      hoverLine.setAttribute("x1", cx);
      hoverLine.setAttribute("x2", cx);
      hoverLine.style.display = "block";

      hoverDotLat.setAttribute("cx", cx);
      hoverDotLat.setAttribute("cy", cyLat);
      hoverDotLat.style.display = "block";

      hoverDotErr.setAttribute("cx", cx);
      hoverDotErr.setAttribute("cy", cyErr);
      hoverDotErr.style.display = "block";

      tipUsers.textContent = `👥 MỐC: ${m.users} KHÁCH HÀNG ĐỒNG THỜI`;
      tipLat.textContent = `⚡ Độ trễ phản hồi: ${m.latency}ms (${m.users > 150 ? 'Cực chậm' : m.users > 80 ? 'Bắt đầu trễ' : 'Nhanh'})`;
      tipImpact.textContent = `🛒 Ảnh hưởng khách khác: ${m.shoppingImpact}% bị chậm đơn`;
      tip404.textContent = `⚠️ Dự đoán tỉ lệ lỗi 404: ${m.pred404Rate}% (Rớt kết nối)`;

      let tipX = cx + 14;
      if (tipX + 255 > width - right) tipX = cx - 265;
      let tipY = cyLat - 40;
      if (tipY < top) tipY = top + 4;
      if (tipY + 88 > height - bottom) tipY = height - bottom - 88;

      tipGroup.setAttribute("transform", `translate(${tipX}, ${tipY})`);
      tipGroup.style.display = "block";

      if (window.updateConcurrencyBadges) {
        window.updateConcurrencyBadges(m);
      }
    });

    overlay.addEventListener("pointerleave", () => {
      hoverLine.style.display = "none";
      hoverDotLat.style.display = "none";
      hoverDotErr.style.display = "none";
      tipGroup.style.display = "none";
      if (liveData && window.updateConcurrencyBadges) {
        window.updateConcurrencyBadges(calcMetrics(liveData.activeUsers || 0));
      }
    });

    svg.append(hoverLine, hoverDotLat, hoverDotErr, tipGroup, overlay);
    container.replaceChildren(svg);
  }

  window.DashboardCharts = Object.freeze({ drawSeverity, drawTraffic, drawConcurrency });
})();
