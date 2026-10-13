/* Small shared UI helpers. Classic scripts keep the demo free of a build step. */
(function () {
  "use strict";

  function setFieldError(input, message) {
    const error = document.getElementById(input.id + "-error");
    input.setAttribute("aria-invalid", String(Boolean(message)));
    if (error) {
      error.textContent = message;
      error.hidden = !message;
    }
  }

  function resetPasswordVisibility(scope) {
    scope.querySelectorAll("[data-toggle-password]").forEach(button => {
      const input = document.getElementById(button.dataset.togglePassword);
      input.type = "password";
      button.textContent = "Hiện";
      button.setAttribute("aria-pressed", "false");
      button.setAttribute("aria-label", input.id === "confirm-password" ? "Hiện mật khẩu xác nhận" : "Hiện mật khẩu");
    });
  }

  document.querySelectorAll("[data-toggle-password]").forEach(button => {
    button.addEventListener("click", () => {
      const input = document.getElementById(button.dataset.togglePassword);
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      button.textContent = show ? "Ẩn" : "Hiện";
      button.setAttribute("aria-pressed", String(show));
      button.setAttribute("aria-label", `${show ? "Ẩn" : "Hiện"} mật khẩu${input.id === "confirm-password" ? " xác nhận" : ""}`);
    });
  });

  function showStorageNotice() {
    if (document.getElementById("storage-notice")) return;
    const notice = document.createElement("p");
    notice.id = "storage-notice";
    notice.className = "notice storage-notice";
    notice.setAttribute("role", "status");
    notice.textContent = "Trình duyệt không lưu được phiên demo. Bạn vẫn có thể thử form; phiên sẽ đặt lại khi đổi trang hoặc tải lại.";
    document.querySelector("main").prepend(notice);
  }

  window.addEventListener("demo-storage-unavailable", showStorageNotice);
  if (!window.DemoData.storageAvailable()) showStorageNotice();

  window.DemoUI = Object.freeze({
    setFieldError,
    resetPasswordVisibility,
    validEmail: input => Boolean(input.value.trim()) && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.value.trim())
  });
})();
