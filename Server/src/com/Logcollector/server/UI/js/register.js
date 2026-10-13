(function () {
  "use strict";

  const form = document.getElementById("register-form");
  const fullname = document.getElementById("fullname");
  const email = document.getElementById("email");
  const password = document.getElementById("password");
  const confirm = document.getElementById("confirm-password");
  const fields = [fullname, email, password, confirm];
  const submit = document.getElementById("register-submit");
  const success = document.getElementById("register-success");
  const conflict = document.getElementById("register-error");
  const scenarios = Array.from(document.querySelectorAll("[data-scenario]"));
  let pending = false;
  let completed = false;

  function clearFeedback() {
    success.hidden = true;
    conflict.hidden = true;
    fields.forEach(field => DemoUI.setFieldError(field, ""));
  }

  function setBusy(busy) {
    pending = busy;
    form.setAttribute("aria-busy", String(busy));
    submit.disabled = busy;
    fields.forEach(field => { field.readOnly = busy; });
    scenarios.forEach(button => { button.disabled = busy; });
    submit.textContent = busy ? "Đang xử lý mô phỏng…" : "Đăng ký tài khoản";
  }

  scenarios.forEach(button => button.addEventListener("click", () => {
    if (pending) return;
    clearFeedback();
    completed = false;
    submit.disabled = false;
    submit.textContent = "Đăng ký tài khoản";
    form.reset();
    DemoUI.resetPasswordVisibility(form);
    scenarios.forEach(item => item.setAttribute("aria-pressed", String(item === button)));
    if (button.dataset.scenario !== "default") {
      fullname.value = "Nguyễn Minh An";
      email.value = button.dataset.scenario === "duplicate" ? "demo@example.com" : "user.test@example.com";
      password.value = "Demo123!";
      confirm.value = "Demo123!";
    }
    // Scenario buttons prepare the form. Only a valid submit records an event.
    if (button.dataset.scenario === "duplicate") {
      conflict.hidden = false;
      DemoUI.setFieldError(email, "Email này đã được sử dụng trong kịch bản demo.");
    }
    fullname.focus();
  }));

  form.addEventListener("input", event => {
    if (!fields.includes(event.target)) return;
    DemoUI.setFieldError(event.target, "");
    if (event.target === email) conflict.hidden = true;
    if (completed) {
      completed = false;
      success.hidden = true;
      submit.disabled = false;
      submit.textContent = "Đăng ký tài khoản";
    }
  });

  form.addEventListener("submit", event => {
    event.preventDefault();
    if (pending || completed) return;
    clearFeedback();
    email.value = email.value.trim();
    const duplicate = ["demo@example.com", DemoData.customer.email].includes(email.value.toLowerCase());
    const errors = [
      fullname.value.trim() ? "" : "Vui lòng nhập họ và tên.",
      !DemoUI.validEmail(email) ? "Email không đúng định dạng." : duplicate ? "Email đã được sử dụng trong kịch bản demo." : "",
      password.value.length >= 6 ? "" : "Mật khẩu demo cần ít nhất 6 ký tự.",
      confirm.value && confirm.value === password.value ? "" : "Mật khẩu xác nhận không khớp."
    ];
    fields.forEach((field, index) => DemoUI.setFieldError(field, errors[index]));
    conflict.hidden = !duplicate;
    const invalid = errors.findIndex(Boolean);
    if (invalid !== -1) {
      fields[invalid].focus();
      return;
    }

    password.value = "";
    confirm.value = "";
    DemoUI.resetPasswordVisibility(form);
    setBusy(true);
    window.setTimeout(() => {
      DemoData.recordEvent("REGISTER_SUCCESS");
      setBusy(false);
      completed = true;
      form.reset();
      submit.disabled = true;
      submit.textContent = "Đã hoàn tất mô phỏng";
      success.hidden = false;
      success.querySelector("a").focus();
    }, 450);
  });
})();
