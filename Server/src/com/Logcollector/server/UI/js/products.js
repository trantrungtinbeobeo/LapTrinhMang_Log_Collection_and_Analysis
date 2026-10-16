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

  function renderClickCount() {
    document.getElementById("session-click-count").textContent = money.format(DemoData.getEvents().filter(event => event.type === "PRODUCT_CLICK").length);
  }

  modalImage.referrerPolicy = "no-referrer";
  modalImage.addEventListener("error", fallbackImage);
  // One delegated handler prevents image/title/button bubbling from double-counting.
  grid.addEventListener("click", event => {
    const button = event.target.closest("button[data-product-id]");
    if (!button || !grid.contains(button) || dialog.open) return;
    const product = catalog.find(item => item.id === button.dataset.productId);
    if (!product) return;
    previousFocus = button;
    document.getElementById("detail-name").textContent = product.name;
    document.getElementById("detail-code").textContent = product.id;
    document.getElementById("detail-category").textContent = product.categoryName;
    document.getElementById("detail-price").textContent = price(product.price);
    document.getElementById("detail-description").textContent = product.description;
    setImage(modalImage, product);
    dialog.showModal();
    document.body.classList.add("modal-open");
    DemoData.recordProductClick(product.id);
    renderClickCount();
    sendProductInteraction("VIEW_PRODUCT", product);
  });

  function sendProductInteraction(actionType, prod) {
    if (!prod) return;
    try {
      const session = DemoData.getSession();
      const userName = (session && session.name) ? session.name : "Admin (Localhost)";
      fetch("/api/interaction", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: actionType,
          productName: prod.name,
          productId: prod.id,
          user: userName
        })
      })
      .then(r => r.json())
      .then(res => {
        console.log(`[Demo Store] Da gui log "${actionType}" len Server thanh cong:`, res);
      })
      .catch(err => {
        console.warn("[Demo Store] Khong the gui log toi Server:", err);
      });
    } catch (ignored) {}
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
