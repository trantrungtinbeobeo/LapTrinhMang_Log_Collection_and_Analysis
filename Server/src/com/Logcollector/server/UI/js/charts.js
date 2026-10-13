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
    container.replaceChildren(svg);
  }

  window.DashboardCharts = Object.freeze({ drawSeverity, drawTraffic });
})();
