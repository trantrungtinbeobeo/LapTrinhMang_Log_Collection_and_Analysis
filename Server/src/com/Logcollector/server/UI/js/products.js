/* Catalog UI only. A click opens one modal and records one local demo event. */
(function () {
  "use strict";

  const catalog = window.DemoCatalog || [];
  const grid = document.getElementById("product-grid");
  const search = document.getElementById("product-search");
  const clear = document.getElementById("clear-search");
  const tabs = Array.from(document.querySelectorAll("[data-category]"));
  const dialog = document.getElementById("product-dialog");
  const modalImage = document.getElementById("detail-image");
  const placeholder = "assets/images/product-placeholder.svg";
  const money = new Intl.NumberFormat("vi-VN");
  let category = "all";
  let previousFocus = null;

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function searchable(text) {
    return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[đĐ]/g, "d").toLowerCase().trim();
  }

  function price(value) {
    return money.format(value) + " VNĐ";
  }

  function setImage(image, product) {
    image.alt = product.name;
    image.dataset.fallback = "false";
    image.src = product.image;
  }

  function fallbackImage(event) {
    const image = event.currentTarget;
    if (image.dataset.fallback === "true") return;
    image.dataset.fallback = "true";
    image.src = placeholder;
  }

  function openButton(product, className, text) {
    const button = element("button", className, text);
    button.type = "button";
    button.dataset.productId = product.id;
    button.setAttribute("aria-label", "Xem chi tiết " + product.name);
    button.setAttribute("aria-haspopup", "dialog");
    button.setAttribute("aria-controls", "product-dialog");
    return button;
  }

  const cards = catalog.map(product => {
    const card = element("article", "product-card");
    const imageButton = openButton(product, "product-image-button");
    const image = element("img", "product-image");
    image.width = 400;
    image.height = 400;
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.addEventListener("error", fallbackImage);
    setImage(image, product);
    imageButton.append(image, element("span", "product-category", product.categoryName));

    const heading = element("h2", "product-name");
    heading.append(openButton(product, "", product.name));
    const bottom = element("div", "product-bottom");
    const priceLabel = element("div", "product-price");
    priceLabel.append(element("span", "small muted", "Giá niêm yết"), element("strong", "", price(product.price)));
    bottom.append(priceLabel, openButton(product, "btn btn-small", "Chi tiết →"));
    card.append(imageButton, element("p", "product-code", "Mã: " + product.id), heading, bottom);
    grid.append(card);
    return { product, card, terms: searchable(`${product.name} ${product.id} ${product.categoryName}`) };
  });

  function filterProducts() {
    const query = searchable(search.value);
    let count = 0;
    cards.forEach(item => {
      const visible = (category === "all" || item.product.category === category) && item.terms.includes(query);
      item.card.hidden = !visible;
      if (visible) count++;
    });
    clear.hidden = !search.value;
    grid.hidden = count === 0;
    document.getElementById("product-empty").hidden = count !== 0;
    document.getElementById("product-count").textContent = `Hiển thị ${count} / ${catalog.length} sản phẩm.`;
  }

  search.addEventListener("input", filterProducts);
  clear.addEventListener("click", () => {
    search.value = "";
    filterProducts();
    search.focus();
  });
  tabs.forEach(tab => tab.addEventListener("click", () => {
    category = tab.dataset.category;
    tabs.forEach(item => item.setAttribute("aria-pressed", String(item === tab)));
    filterProducts();
    // Filtering is not a product click and must not increment the counter.
  }));
  document.getElementById("reset-products").addEventListener("click", () => {
    search.value = "";
    category = "all";
    tabs.forEach(tab => tab.setAttribute("aria-pressed", String(tab.dataset.category === "all")));
    filterProducts();
    search.focus();
  });

  let totalTelemetrySent = 0;
  let activeProduct = null;

  function getBackendApiUrl(endpoint) {
    if (window.location.protocol.startsWith("http") && window.location.port === "8080") {
      return endpoint;
    }
    const host = window.location.hostname || "localhost";
    return `http://${host}:8080${endpoint}`;
  }

  function updateTelemetryCount(delta = 1) {
    totalTelemetrySent += delta;
    const badge = document.getElementById("telemetry-sent-count");
    if (badge) badge.textContent = money.format(totalTelemetrySent);
  }

  function sendTelemetryEvent(actionType, prod, latency = 20, burst = 1) {
    if (!prod) return Promise.resolve();
    try {
      const session = DemoData.getSession();
      const userName = (session && session.name) ? session.name : "Admin (Localhost)";
      const url = getBackendApiUrl("/api/interaction");
      return fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: actionType,
          productName: prod.name,
          productId: prod.id,
          user: userName,
          latency: latency,
          burst: burst
        })
      })
      .then(r => r.json())
      .then(res => {
        updateTelemetryCount(burst);
        console.log(`[Demo Store] Da gui log "${actionType}" len ServerMain:`, res);
        return res;
      })
      .catch(err => {
        console.warn("[Demo Store] Khong the gui log toi Server:", err);
      });
    } catch (ignored) {
      return Promise.resolve();
    }
  }

  function renderClickCount() {
    document.getElementById("session-click-count").textContent = money.format(DemoData.getEvents().filter(event => event.type === "PRODUCT_CLICK").length);
  }

  modalImage.referrerPolicy = "no-referrer";
  modalImage.addEventListener("error", fallbackImage);

  function dispatchProductViewPipeline(product) {
    const steps = [
      { type: "VIEW_PRODUCT", delay: 0, lat: 18 },
      { type: "INVENTORY_CHECK", delay: 35, lat: 24 },
      { type: "RECOMMENDATION", delay: 70, lat: 32 },
      { type: "MEDIA_ASSET", delay: 105, lat: 15 },
      { type: "TELEMETRY_PING", delay: 140, lat: 19 }
    ];
    steps.forEach(step => {
      setTimeout(() => {
        sendTelemetryEvent(step.type, product, step.lat, 1);
      }, step.delay);
    });
    setTimeout(() => {
      const modalNotice = document.getElementById("modal-telemetry-notice");
      if (modalNotice) modalNotice.textContent = `✓ Đã phát sinh 5 gói tin telemetry (${product.id}) đẩy lên ServerMain thành công!`;
    }, 200);
  }

  // Delegated handler: click card -> open dialog -> dispatch 5 telemetry logs
  grid.addEventListener("click", event => {
    const button = event.target.closest("button[data-product-id]");
    if (!button || !grid.contains(button) || dialog.open) return;
    const product = catalog.find(item => item.id === button.dataset.productId);
    if (!product) return;
    previousFocus = button;
    activeProduct = product;
    document.getElementById("detail-name").textContent = product.name;
    document.getElementById("detail-code").textContent = product.id;
    document.getElementById("detail-category").textContent = product.categoryName;
    document.getElementById("detail-price").textContent = price(product.price);
    document.getElementById("detail-description").textContent = product.description;
    const modalNotice = document.getElementById("modal-telemetry-notice");
    if (modalNotice) modalNotice.textContent = "⚡ Đang phát sinh luồng telemetry xem chi tiết thiết bị lên Server...";
    setImage(modalImage, product);
    dialog.showModal();
    document.body.classList.add("modal-open");
    DemoData.recordProductClick(product.id);
    renderClickCount();

    // Phat sinh luu luong telemetry thuc te
    dispatchProductViewPipeline(product);
  });

  // Nut "Them vao gio hang" trong Dialog
  const btnAddToCart = document.getElementById("btn-add-to-cart");
  if (btnAddToCart) {
    btnAddToCart.addEventListener("click", () => {
      if (!activeProduct) return;
      const modalNotice = document.getElementById("modal-telemetry-notice");
      if (modalNotice) modalNotice.textContent = "🛒 Đang cập nhật giỏ hàng và đồng bộ lên Server...";
      const cartSteps = [
        { type: "ADD_TO_CART", delay: 0, lat: 25 },
        { type: "CART_UPDATE", delay: 35, lat: 19 },
        { type: "PROMO_CHECK", delay: 70, lat: 22 },
        { type: "CHECKOUT_INTENT", delay: 105, lat: 16 }
      ];
      cartSteps.forEach(s => {
        setTimeout(() => sendTelemetryEvent(s.type, activeProduct, s.lat, 1), s.delay);
      });
      setTimeout(() => {
        if (modalNotice) modalNotice.textContent = `✓ Đã thêm "${activeProduct.name}" vào giỏ hàng (+4 gói tin tới ServerMain)!`;
      }, 160);
    });
  }

  // Nut "Mua ngay" trong Dialog
  const btnCheckoutNow = document.getElementById("btn-checkout-now");
  if (btnCheckoutNow) {
    btnCheckoutNow.addEventListener("click", () => {
      if (!activeProduct) return;
      const modalNotice = document.getElementById("modal-telemetry-notice");
      if (modalNotice) modalNotice.textContent = "⚡ Đang khởi tạo phiên thanh toán nhanh...";
      const checkoutSteps = [
        { type: "CHECKOUT_ORDER", delay: 0, lat: 32 },
        { type: "PAYMENT_GATEWAY", delay: 40, lat: 45 },
        { type: "ORDER_CREATED", delay: 80, lat: 28 },
        { type: "TELEMETRY_PING", delay: 120, lat: 18 }
      ];
      checkoutSteps.forEach(s => {
        setTimeout(() => sendTelemetryEvent(s.type, activeProduct, s.lat, 1), s.delay);
      });
      setTimeout(() => {
        if (modalNotice) modalNotice.textContent = `✓ Đã tạo đơn hàng "${activeProduct.name}" thành công (+4 gói tin tới ServerMain)!`;
      }, 170);
    });
  }

  // Nut "Mo phong luu luong mua sam cuc bo (+15 log)"
  const btnShoppingSpike = document.getElementById("btn-simulate-shopping-spike");
  if (btnShoppingSpike) {
    btnShoppingSpike.addEventListener("click", () => {
      btnShoppingSpike.disabled = true;
      btnShoppingSpike.textContent = "⚡ Đang bắn lưu lượng...";
      const prods = catalog.slice(0, 5);
      let count = 0;
      for (let i = 0; i < 15; i++) {
        const prod = prods[i % prods.length];
        const action = i % 3 === 0 ? "VIEW_PRODUCT" : (i % 3 === 1 ? "ADD_TO_CART" : "CHECKOUT_ORDER");
        setTimeout(() => {
          sendTelemetryEvent(action, prod, 15 + Math.floor(Math.random() * 25), 1);
          count++;
          if (count === 15) {
            btnShoppingSpike.disabled = false;
            btnShoppingSpike.textContent = "⚡ Mô phỏng lưu lượng (+15 log)";
          }
        }, i * 25);
      }
    });
  }

  document.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", () => dialog.close()));
  dialog.addEventListener("click", event => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
  });
  // Native dialog handles Escape, focus trapping and inert background content.
  dialog.addEventListener("close", () => {
    document.body.classList.remove("modal-open");
    if (previousFocus && previousFocus.isConnected) previousFocus.focus();
  });

  function renderSession() {
    const session = DemoData.getSession();
    document.getElementById("user-name").textContent = session ? session.name : "Khách demo";
    document.getElementById("user-email").textContent = session ? session.email : "Chưa đăng nhập demo";
    document.getElementById("user-avatar").textContent = session ? session.name.charAt(0) : "K";
    document.getElementById("login-link").hidden = Boolean(session);
    document.getElementById("logout-button").hidden = !session;
  }
  document.getElementById("logout-button").addEventListener("click", () => {
    DemoData.endSession();
    location.href = "login.html";
  });
  const accountMenu = document.getElementById("account-menu");
  document.addEventListener("click", event => {
    if (!accountMenu.contains(event.target)) accountMenu.open = false;
  });
  document.addEventListener("keydown", event => {
    if (event.key === "Escape" && accountMenu.open) {
      accountMenu.open = false;
      accountMenu.querySelector("summary").focus();
    }
  });

  window.addEventListener("pageshow", event => {
    if (event.persisted) { renderSession(); renderClickCount(); }
  });
  renderSession();
  filterProducts();
  renderClickCount();
})();
