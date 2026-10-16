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

  const STORAGE_KEY = "dashboard_live_logs_v2";

  function persistStoreLogToLocal(actionType, prod, latency = 20, burst = 1) {
    try {
      let stored = [];
      try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (raw) stored = JSON.parse(raw);
      } catch (e) { stored = []; }
      if (!Array.isArray(stored)) stored = [];

      const session = DemoData.getSession();
      const userName = (session && session.name) ? session.name : "Admin (Localhost)";
      const now = Date.now();
      const prodName = prod ? prod.name : "Thiết bị điện tử";
      const prodId = prod ? prod.id : "PRD-DEMO";

      for (let i = 0; i < burst; i++) {
        let subType = actionType;
        let subDesc = `User '${userName}' performed ${actionType} for '${prodName}' [${prodId}] | Latency: ${latency}ms`;

        if (actionType === "ADD_TO_CART" && burst > 1) {
          switch (i % 4) {
            case 0: subType = "ADD_TO_CART"; subDesc = `User '${userName}' added '${prodName}' to cart [ADD_TO_CART] | Latency: ${latency}ms`; break;
            case 1: subType = "CART_UPDATE"; subDesc = `Shopping-Cart-Service synchronized subtotal for User '${userName}' [CART_UPDATE] | Latency: ${latency}ms`; break;
            case 2: subType = "PROMO_CHECK"; subDesc = `Promo-Engine applied flash vouchers for '${prodName}' [PROMO_CHECK] | Latency: ${latency}ms`; break;
            case 3: subType = "CHECKOUT_INTENT"; subDesc = `Checkout-Pipeline initialized intent for User '${userName}' [CHECKOUT_INTENT] | Latency: ${latency}ms`; break;
          }
        }

        stored.unshift({
          id: `prod-local-${now}-${i}-${Math.random().toString(36).substring(2, 7)}`,
          at: now + i * 15,
          severity: "INFO",
          ip: "127.0.0.1",
          device: "Web Store Browser [User: " + userName + "]",
          category: "APPLICATION",
          source: "interaction",
          type: subType,
          message: subDesc,
          raw: `<14>${new Date(now + i * 15).toISOString()} 127.0.0.1 ${subDesc}`
        });
      }

      if (stored.length > 1000) stored = stored.slice(0, 1000);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    } catch (e) {
      // Bo qua loi luu tru
    }
  }

  function sendTelemetryEvent(actionType, prod, latency = 20, burst = 1) {
    if (!prod) return Promise.resolve();
    // Luu ngay vao localStorage de khi chuyen qua Dashboard hoac F5 se thay ngay lap tuc
    persistStoreLogToLocal(actionType, prod, latency, burst);

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

  // Quan ly Gio hang (Cart State Management)
  const CART_STORAGE_KEY = "demo_store_cart_v1";

  function getCartItems() {
    try {
      const raw = localStorage.getItem(CART_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) return parsed;
      }
    } catch (e) {}
    return [];
  }

  function saveCartItems(items) {
    try {
      localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items));
    } catch (e) {}
    updateCartBadge();
  }

  function updateCartBadge() {
    const items = getCartItems();
    const totalCount = items.reduce((sum, item) => sum + (item.quantity || 1), 0);
    const badge = document.getElementById("cart-total-badge");
    if (badge) badge.textContent = String(totalCount);
  }

  function addItemToCart(product) {
    if (!product) return;
    const items = getCartItems();
    const existing = items.find(item => item.id === product.id);
    if (existing) {
      existing.quantity = (existing.quantity || 1) + 1;
    } else {
      items.push({
        id: product.id,
        name: product.name,
        price: product.price,
        image: product.image,
        categoryName: product.categoryName,
        quantity: 1
      });
    }
    saveCartItems(items);
  }

  function updateItemQuantity(productId, delta) {
    let items = getCartItems();
    const item = items.find(i => i.id === productId);
    if (!item) return;
    item.quantity = (item.quantity || 1) + delta;
    if (item.quantity <= 0) {
      items = items.filter(i => i.id !== productId);
    }
    saveCartItems(items);
    renderCartView();
  }

  function removeItemFromCart(productId) {
    let items = getCartItems();
    items = items.filter(i => i.id !== productId);
    saveCartItems(items);
    renderCartView();
  }

  function clearCart() {
    saveCartItems([]);
    renderCartView();
  }

  function renderCartView() {
    const items = getCartItems();
    const emptyView = document.getElementById("cart-empty-view");
    const contentView = document.getElementById("cart-content-view");
    const listEl = document.getElementById("cart-items-list");
    const totalEl = document.getElementById("cart-total-price");

    if (items.length === 0) {
      if (emptyView) emptyView.hidden = false;
      if (contentView) contentView.hidden = true;
      if (totalEl) totalEl.textContent = "0 VNĐ";
      return;
    }

    if (emptyView) emptyView.hidden = true;
    if (contentView) contentView.hidden = false;

    let totalPrice = 0;
    if (listEl) {
      listEl.innerHTML = "";
      items.forEach(item => {
        const itemTotal = item.price * (item.quantity || 1);
        totalPrice += itemTotal;

        const row = document.createElement("div");
        row.className = "cart-item-row";

        const info = document.createElement("div");
        info.className = "cart-item-info";

        const nameEl = document.createElement("div");
        nameEl.className = "cart-item-name";
        nameEl.textContent = item.name;

        const priceEl = document.createElement("div");
        priceEl.className = "cart-item-price";
        priceEl.textContent = `${price(item.price)} × ${item.quantity} = ${price(itemTotal)}`;

        info.append(nameEl, priceEl);

        const controls = document.createElement("div");
        controls.className = "cart-item-controls";

        const decBtn = document.createElement("button");
        decBtn.type = "button";
        decBtn.className = "cart-qty-btn";
        decBtn.textContent = "-";
        decBtn.title = "Giảm số lượng";
        decBtn.addEventListener("click", () => updateItemQuantity(item.id, -1));

        const qtyVal = document.createElement("span");
        qtyVal.className = "cart-qty-value";
        qtyVal.textContent = item.quantity;

        const incBtn = document.createElement("button");
        incBtn.type = "button";
        incBtn.className = "cart-qty-btn";
        incBtn.textContent = "+";
        incBtn.title = "Tăng số lượng";
        incBtn.addEventListener("click", () => updateItemQuantity(item.id, 1));

        const remBtn = document.createElement("button");
        remBtn.type = "button";
        remBtn.className = "cart-remove-btn";
        remBtn.textContent = "Xóa";
        remBtn.title = "Xóa sản phẩm khỏi giỏ";
        remBtn.addEventListener("click", () => removeItemFromCart(item.id));

        controls.append(decBtn, qtyVal, incBtn, remBtn);
        row.append(info, controls);
        listEl.append(row);
      });
    }

    if (totalEl) totalEl.textContent = price(totalPrice);
  }

  // Khoi tao hien thi so dem tren badge gio hang
  updateCartBadge();

  // Hop thoai Gio hang
  const cartDialog = document.getElementById("cart-dialog");
  const openCartBtn = document.getElementById("open-cart-btn");
  const closeCartBtn = document.getElementById("close-cart-dialog");
  const clearCartBtn = document.getElementById("btn-clear-cart");
  const checkoutCartBtn = document.getElementById("btn-cart-checkout");

  if (openCartBtn && cartDialog) {
    openCartBtn.addEventListener("click", () => {
      renderCartView();
      cartDialog.showModal();
      document.body.classList.add("modal-open");
    });
  }

  if (closeCartBtn && cartDialog) {
    closeCartBtn.addEventListener("click", () => {
      cartDialog.close();
      document.body.classList.remove("modal-open");
    });
  }

  if (cartDialog) {
    cartDialog.addEventListener("click", event => {
      if (event.target === cartDialog) {
        cartDialog.close();
        document.body.classList.remove("modal-open");
      }
    });
  }

  if (clearCartBtn) {
    clearCartBtn.addEventListener("click", () => {
      if (confirm("Bạn có chắc chắn muốn xóa toàn bộ sản phẩm trong giỏ hàng?")) {
        clearCart();
      }
    });
  }

  if (checkoutCartBtn) {
    checkoutCartBtn.addEventListener("click", async () => {
      const items = getCartItems();
      if (items.length === 0) return;
      const totalCount = items.reduce((s, i) => s + (i.quantity || 1), 0);
      const notice = document.getElementById("cart-telemetry-notice");
      if (notice) notice.textContent = "⚡ Đang xử lý giao dịch thanh toán đơn hàng lên ServerMain...";

      // Thao tac nguoi dung that: Sinh DUNG 1 LOG CHECKOUT_ORDER duy nhat
      await sendTelemetryEvent("CHECKOUT_ORDER", items[0], 28, 1);
      clearCart();

      if (notice) notice.textContent = `✓ Đã thanh toán thành công đơn hàng (${totalCount} món)! Đã phát sinh 1 log CHECKOUT_ORDER tới ServerMain.`;
      setTimeout(() => {
        if (cartDialog && cartDialog.open) cartDialog.close();
        document.body.classList.remove("modal-open");
      }, 1200);
    });
  }

  // Delegated handler: click card -> mo dialog xem chi tiet -> sinh DUNG 1 LOG VIEW_PRODUCT
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
    if (modalNotice) modalNotice.textContent = "⚡ Đang truyền 1 log xem chi tiết thiết bị lên Server...";
    setImage(modalImage, product);
    dialog.showModal();
    document.body.classList.add("modal-open");
    DemoData.recordProductClick(product.id);
    renderClickCount();

    // Sinh DUNG 1 LOG duy nhat: VIEW_PRODUCT
    sendTelemetryEvent("VIEW_PRODUCT", product, 18, 1).then(() => {
      if (modalNotice) modalNotice.textContent = `✓ Đã phát sinh 1 log VIEW_PRODUCT (${product.id}) tới ServerMain thành công!`;
    });
  });

  // Nut "Them vao gio hang" trong Dialog -> sinh DUNG 1 LOG ADD_TO_CART
  const btnAddToCart = document.getElementById("btn-add-to-cart");
  if (btnAddToCart) {
    btnAddToCart.addEventListener("click", async () => {
      if (!activeProduct) return;
      addItemToCart(activeProduct);
      const modalNotice = document.getElementById("modal-telemetry-notice");
      if (modalNotice) modalNotice.textContent = `🛒 Đang thêm "${activeProduct.name}" vào giỏ hàng...`;

      // Sinh DUNG 1 LOG duy nhat: ADD_TO_CART
      await sendTelemetryEvent("ADD_TO_CART", activeProduct, 22, 1);
      if (modalNotice) modalNotice.textContent = `✓ Đã thêm "${activeProduct.name}" vào giỏ hàng (+1 log ADD_TO_CART tới ServerMain)!`;
    });
  }

  // Nut "Thanh toan ngay" trong Dialog -> sinh DUNG 1 LOG CHECKOUT_ORDER va mo gio hang
  const btnCheckoutNow = document.getElementById("btn-checkout-now");
  if (btnCheckoutNow) {
    btnCheckoutNow.addEventListener("click", async () => {
      if (!activeProduct) return;
      addItemToCart(activeProduct);
      const modalNotice = document.getElementById("modal-telemetry-notice");
      if (modalNotice) modalNotice.textContent = "⚡ Đang khởi tạo phiên thanh toán...";

      // Sinh DUNG 1 LOG duy nhat: CHECKOUT_ORDER
      await sendTelemetryEvent("CHECKOUT_ORDER", activeProduct, 28, 1);
      if (modalNotice) modalNotice.textContent = `✓ Đã tạo đơn hàng "${activeProduct.name}" thành công (+1 log CHECKOUT_ORDER tới ServerMain)!`;
      setTimeout(() => {
        if (dialog && dialog.open) dialog.close();
        document.body.classList.remove("modal-open");
        if (openCartBtn) openCartBtn.click();
      }, 700);
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
