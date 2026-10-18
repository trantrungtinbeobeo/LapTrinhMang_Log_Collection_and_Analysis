/* Frontend-only demo state. No password, real account or backend token is stored. */
(function () {
  "use strict";

  const KEY = "logcollector.ui.demo.v1";
  const customer = Object.freeze({ name: "Minh An", email: "minhan@demostore.vn", role: "customer" });
  const messages = Object.freeze({
    REGISTER_SUCCESS: "Hoàn tất biểu mẫu đăng ký mô phỏng; không tạo tài khoản thật.",
    LOGIN_SUCCESS: "Đăng nhập bằng tài khoản mẫu Minh An.",
    LOGIN_FAILED: "Thông tin đăng nhập không khớp tài khoản mẫu.",
    LOGOUT: "Kết thúc phiên trải nghiệm demo.",
    PRODUCT_CLICK: "Xem chi tiết sản phẩm trong cửa hàng demo."
  });
  let canPersist = true;
  let state = { version: 1, session: null, events: [] };

  function restoreState() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(KEY) || "null");
      if (saved && saved.version === 1 && Array.isArray(saved.events)) {
        state.session = saved.session && saved.session.email === customer.email ? { ...customer } : null;
        // Reconstruct allowed fields; never propagate arbitrary stored attributes.
        state.events = saved.events.filter(event => event && Object.hasOwn(messages, event.type)
          && Number.isFinite(event.at)).slice(-500).map(event => ({
            type: event.type, at: event.at,
            severity: event.type === "LOGIN_FAILED" ? "WARNING" : "INFO",
            message: messages[event.type],
            ...(event.type === "PRODUCT_CLICK" ? productDetails(event) : {})
          }));
      } else {
        state = { version: 1, session: null, events: [] };
      }
    } catch {
      // file://, browser privacy settings or malformed storage must not break the UI.
      canPersist = false;
      window.dispatchEvent(new Event("demo-storage-unavailable"));
    }
  }

  restoreState();
  // Back/Forward may restore an old page from memory without rerunning its scripts.
  window.addEventListener("pageshow", event => {
    if (event.persisted && canPersist) restoreState();
  });

  function persist() {
    if (!canPersist) return;
    try {
      sessionStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      canPersist = false;
      window.dispatchEvent(new Event("demo-storage-unavailable"));
    }
  }

  function productDetails(details) {
    return {
      productId: typeof details.productId === "string" ? details.productId.slice(0, 40) : "",
      productName: typeof details.productName === "string" ? details.productName.slice(0, 160) : "",
      actor: details.actor === customer.email ? customer.email : "Khách demo"
    };
  }

  function recordEvent(type, details = {}) {
    if (!Object.hasOwn(messages, type)) return;
    state.events.push({
      type, at: Date.now(), severity: type === "LOGIN_FAILED" ? "WARNING" : "INFO", message: messages[type],
      ...(type === "PRODUCT_CLICK" ? productDetails(details) : {})
    });
    state.events = state.events.slice(-500);
    persist();
  }

  function startCustomerSession() {
    state.session = { ...customer };
    recordEvent("LOGIN_SUCCESS");
    return { ...customer };
  }

  function endSession() {
    if (state.session) recordEvent("LOGOUT");
    state.session = null;
    persist();
  }

  function recordProductClick(productId) {
    const product = (window.DemoCatalog || []).find(item => item.id === productId);
    if (!product) return;
    recordEvent("PRODUCT_CLICK", {
      productId: product.id,
      productName: product.name,
      actor: state.session ? customer.email : "Khách demo"
    });
  }

  window.DemoData = Object.freeze({
    customer,
    // Public fixture only. This comparison is NOT an authentication boundary.
    matchesDemoAccount: (email, password) => {
      if (!email || !password) return false;
      const p = String(password).trim();
      // Chap nhan ca mat khau mac dinh Demo123! va cac mat khau test nhanh 123456, admin, admin123
      if (p === "Demo123!" || p === "123456" || p === "admin" || p === "admin123") {
        return true;
      }
      return email.trim().toLowerCase() === customer.email && p === "Demo123!";
    },
    startCustomerSession,
    endSession,
    recordEvent,
    recordProductClick,
    getEvents: () => state.events.map(event => ({ ...event })),
    getSession: () => state.session ? { ...state.session } : null,
    storageAvailable: () => canPersist
  });
})();
