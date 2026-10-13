(function () {
  "use strict";

  const form = document.getElementById("login-form");
  const email = document.getElementById("email");
  const password = document.getElementById("password");
  const submit = document.getElementById("login-submit");
  const error = document.getElementById("login-error");
  const success = document.getElementById("login-success");
  const customerShortcut = document.getElementById("customer-shortcut");
  let pending = false;

  document.getElementById("registration-notice").hidden = new URLSearchParams(location.search).get("registered") !== "1";

  function renderSession() {
    const session = DemoData.getSession();
    success.hidden = !session;
    form.hidden = Boolean(session);
    customerShortcut.disabled = Boolean(session);
    if (session) {
      document.getElementById("session-description").textContent = `Xin chào ${session.name}. Phiên khách hàng demo đã sẵn sàng.`;
    }
  }

  function startSession() {
    DemoData.startCustomerSession();
    password.value = "";
    DemoUI.resetPasswordVisibility(form);
    error.hidden = true;
    renderSession();
    location.href = "products.html";
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    if (pending) return;
    error.hidden = true;
    email.value = email.value.trim();
    const emailError = DemoUI.validEmail(email) ? "" : "Vui lòng nhập email hợp lệ.";
    const passwordError = password.value ? "" : "Vui lòng nhập mật khẩu demo.";
    DemoUI.setFieldError(email, emailError);
    DemoUI.setFieldError(password, passwordError);
    if (emailError || passwordError) {
      (emailError ? email : password).focus();
      return;
    }

    // Capture only the result, never retain the password in a callback or storage.
    const accepted = DemoData.matchesDemoAccount(email.value, password.value);
    password.value = "";
    DemoUI.resetPasswordVisibility(form);
    pending = true;
    form.setAttribute("aria-busy", "true");
    submit.disabled = true;
    customerShortcut.disabled = true;
    submit.textContent = "Đang xử lý mô phỏng…";
    window.setTimeout(() => {
      pending = false;
      form.setAttribute("aria-busy", "false");
      submit.disabled = false;
      customerShortcut.disabled = false;
      submit.textContent = "Đăng nhập →";
      if (accepted) startSession();
      else {
        DemoData.recordEvent("LOGIN_FAILED");
        error.hidden = false;
        password.focus();
      }
    }, 450);
  });

  customerShortcut.addEventListener("click", startSession);
  document.getElementById("logout-demo").addEventListener("click", () => {
    DemoData.endSession();
    renderSession();
    email.focus();
  });
  renderSession();
})();
