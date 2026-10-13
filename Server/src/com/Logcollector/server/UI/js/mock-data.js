/* Frontend-only demo state. No password, real account or backend token is stored. */
(function () {
  "use strict";

  const KEY = "logcollector.ui.demo.v1";
  const customer = Object.freeze({ name: "Minh An", email: "minhan@demostore.vn", role: "customer" });
  const messages = Object.freeze({
    REGISTER_SUCCESS: "Hoàn tất biểu mẫu đăng ký mô phỏng; không tạo tài khoản thật.",
    LOGIN_SUCCESS: "Đăng nhập bằng tài khoản mẫu Minh An.",
    LOGIN_FAILED: "Thông tin đăng nhập không khớp tài khoản mẫu.",
    LOGOUT: "Kết thúc phiên trải nghiệm demo."
  });
  let canPersist = true;
  let state = { version: 1, session: null, events: [] };

  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) || "null");
    if (saved && saved.version === 1 && Array.isArray(saved.events)) {
      state.session = saved.session && saved.session.email === customer.email ? { ...customer } : null;
      // Reconstruct allowed fields; never propagate arbitrary stored attributes.
      state.events = saved.events.filter(event => event && Object.hasOwn(messages, event.type)
        && Number.isFinite(event.at)).slice(-500).map(event => ({
          type: event.type, at: event.at,
          severity: event.type === "LOGIN_FAILED" ? "WARNING" : "INFO",
          message: messages[event.type]
        }));
    }
  } catch {
    // file://, browser privacy settings or malformed storage must not break the forms.
    canPersist = false;
  }

  function persist() {
    if (!canPersist) return;
    try {
      sessionStorage.setItem(KEY, JSON.stringify(state));
    } catch {
      canPersist = false;
      window.dispatchEvent(new Event("demo-storage-unavailable"));
    }
  }

  function recordEvent(type) {
    if (!Object.hasOwn(messages, type)) return;
    state.events.push({ type, at: Date.now(), severity: type === "LOGIN_FAILED" ? "WARNING" : "INFO", message: messages[type] });
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

  window.DemoData = Object.freeze({
    customer,
    // Public fixture only. This comparison is NOT an authentication boundary.
    matchesDemoAccount: (email, password) => email.trim().toLowerCase() === customer.email && password === "Demo123!",
    startCustomerSession,
    endSession,
    recordEvent,
    getSession: () => state.session ? { ...state.session } : null,
    storageAvailable: () => canPersist
  });
})();
