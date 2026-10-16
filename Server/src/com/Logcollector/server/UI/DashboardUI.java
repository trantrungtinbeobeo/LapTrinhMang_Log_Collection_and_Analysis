package com.Logcollector.server.UI;

import com.Logcollector.server.Core.LogQueue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet4Address;

/**
 * DashboardUI (Local Embedded Web Server Launcher):
 * Khoi dong may chu HTTP noi bo tai cong 8080 de phuc vu cac trang Web Dashboard,
 * Login, Register va Catalog san pham ma khong can cai them Web Server ngoai (Apache/Nginx/NodeJS).
 * Tu dong mo trinh duyet mac dinh cua may tinh khi khoi dong.
 * Ho tro cac thiet bi khac trong cung mang Wi-Fi/LAN truy cap va nhan dien thiet bi.
 */
public class DashboardUI {

    private static final int DEFAULT_PORT = 8080;
    private final int port;
    private HttpServer httpServer;
    private final File webRoot;

    public DashboardUI() {
        this(DEFAULT_PORT);
    }

    public DashboardUI(int port) {
        this.port = port;
        this.webRoot = locateWebRoot();
    }

    /**
     * Khoi dong HTTP Web Server va mo trinh duyet
     */
    public void start() {
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/api/login", new LoginApiHandler());
            httpServer.createContext("/api/interaction", new InteractionApiHandler());
            httpServer.createContext("/api/block-ip", new BlockIpApiHandler());
            httpServer.createContext("/", new StaticFileHandler(webRoot));
            httpServer.setExecutor(null); // Dung bo dieu phoi mac dinh
            httpServer.start();

            String lanIp = getLocalLanIp();

            System.out.println("==================================================");
            System.out.println("   WEB DASHBOARD DA SAN SANG (LOCAL & MANG LAN)   ");
            System.out.println("   - May chu cuc bo (Localhost): http://localhost:" + port + "/dashboard.html");
            System.out.println("   - May khac cung mang LAN    : http://" + lanIp + ":" + port + "/dashboard.html");
            System.out.println("   - Trang dang nhap test LAN  : http://" + lanIp + ":" + port + "/login.html");
            System.out.println("==================================================");

            // Tu dong mo trinh duyet neu he dieu hanh ho tro Desktop
            openBrowser("http://localhost:" + port + "/dashboard.html");

        } catch (IOException e) {
            System.err.println("[DashboardUI ERROR] Khong the mo cong HTTP " + port + ": " + e.getMessage());
        }
    }

    /**
     * Dung HTTP Web Server
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            System.out.println("[DashboardUI] Da dung Web Server tai cong " + port);
        }
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            // Neu moi truong khong co man hinh thi bo qua
        }
    }

    private static File locateWebRoot() {
        String[] possiblePaths = {
                "Server/src/com/Logcollector/server/UI",
                "src/com/Logcollector/server/UI",
                "LapTrinhMang_Log_Collection_and_Analysis/Server/src/com/Logcollector/server/UI",
                "UI"
        };
        for (String p : possiblePaths) {
            File dir = new File(p);
            if (dir.exists() && dir.isDirectory() && new File(dir, "dashboard.html").exists()) {
                return dir;
            }
        }
        return new File(".");
    }

    /**
     * Handler phuc vu cac file tinh (HTML, CSS, JS, SVG, Images)
     */
    private static class StaticFileHandler implements HttpHandler {
        private final File baseDir;

        public StaticFileHandler(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/dashboard.html";
            }

            File targetFile = new File(baseDir, path.replaceFirst("^/", ""));

            if (!targetFile.exists() || targetFile.isDirectory()) {
                String response = "404 Not Found: " + path;
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String contentType = probeContentType(targetFile.getName());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, targetFile.length());

            try (OutputStream os = exchange.getResponseBody();
                 FileInputStream fis = new FileInputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private String probeContentType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    /**
     * Handler tiep nhan dang nhap tu trinh duyet Web cua cac thiet bi trong mang LAN
     */
    private static class LoginApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (clientIp == null || "0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
                clientIp = "127.0.0.1";
            }
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            String device = parseDeviceFromUserAgent(userAgent);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean success = body.contains("\"success\":true") || body.contains("\"success\": true");

            String email = "khach_demo";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (m.find()) {
                email = m.group(1);
            }

            int pri = success ? 14 : 12; // 14: INFO, 12: WARNING
            String eventType = success ? "LOGIN_SUCCESS" : "LOGIN_FAILED";
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String deviceWithUser = device + " [User: " + email + "]";
            String syslogMsg = String.format("<%d>%s %s %s - Device '%s' [IP: %s] %s user '%s'",
                    pri, timestamp, clientIp, eventType, deviceWithUser, clientIp,
                    (success ? "logged in successfully as" : "failed password for"), email);

            // Day vao hang doi log cua he thong
            LogQueue.offer(syslogMsg);
            System.out.println("[AUTH API] Da ghi nhan dang nhap tu " + clientIp + ": " + email + " tren " + device + " (" + eventType + ")");

            String jsonResp = String.format("{\"status\":\"ok\",\"device\":\"%s\",\"ip\":\"%s\"}", deviceWithUser, clientIp);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Handler tiep nhan tuong tac san pham / thiet bi tu trinh duyet Web
     */
    private static class InteractionApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (clientIp == null || "0:0:0:0:0:0:0:1".equals(clientIp) || "::1".equals(clientIp)) {
                clientIp = "127.0.0.1";
            }
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            String device = parseDeviceFromUserAgent(userAgent);

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String action = "VIEW_PRODUCT";
            String item = "San pham dien tu";
            String user = "Admin (Localhost)";

            java.util.regex.Matcher mAction = java.util.regex.Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (mAction.find()) action = mAction.group(1);
            java.util.regex.Matcher mItem = java.util.regex.Pattern.compile("\"productName\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (mItem.find()) item = mItem.group(1);
            java.util.regex.Matcher mUser = java.util.regex.Pattern.compile("\"user\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (mUser.find()) user = mUser.group(1);

            int pri = 14;
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String deviceWithUser = device + " [User: " + user + "]";
            String syslogMsg = String.format("<%d>%s %s User '%s' on Device '%s' viewed '%s' [%s]",
                    pri, timestamp, clientIp, user, deviceWithUser, item, action);

            LogQueue.offer(syslogMsg);
            System.out.println("[INTERACTION API] Ghi nhan thao tac san pham: " + item + " boi " + user + " tren " + device + " (" + clientIp + ")");

            String jsonResp = "{\"status\":\"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Handler chan IP thu cong tu giao dien Web Dashboard va thanh loc toan bo log cua IP do khoi CSDL
     */
    private static class BlockIpApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                java.util.Set<String> blocked = com.Logcollector.server.ServerMain.BLOCKED_IPS;
                StringBuilder sb = new StringBuilder("{\"blockedIps\":[");
                int idx = 0;
                for (String ip : blocked) {
                    if (idx++ > 0) sb.append(",");
                    sb.append("\"").append(ip).append("\"");
                }
                sb.append("]}");
                byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String targetIp = null;
            String reason = "Quan tri vien chan thu cong tu Web Dashboard";

            java.util.regex.Matcher mIp = java.util.regex.Pattern.compile("\"ip\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (mIp.find()) targetIp = mIp.group(1).trim();

            java.util.regex.Matcher mReason = java.util.regex.Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
            if (mReason.find()) reason = mReason.group(1).trim();

            int purged = 0;
            if (targetIp != null && !targetIp.isEmpty()) {
                purged = com.Logcollector.server.ServerMain.blockIp(targetIp, reason);
            }

            String jsonResp = String.format("{\"status\":\"ok\",\"ip\":\"%s\",\"purged\":%d}",
                    (targetIp != null ? targetIp : ""), purged);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            byte[] bytes = jsonResp.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Phan tich User-Agent de nhan dien loai thiet bi va he dieu hanh cua client
     */
    public static String parseDeviceFromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return "Network-Device";
        }
        if (userAgent.contains("iPhone")) return "Apple iPhone (iOS)";
        if (userAgent.contains("iPad")) return "Apple iPad (iPadOS)";
        if (userAgent.contains("Android")) {
            if (userAgent.contains("Samsung") || userAgent.contains("SM-")) return "Samsung Galaxy (Android)";
            if (userAgent.contains("Xiaomi") || userAgent.contains("Redmi")) return "Xiaomi (Android)";
            return "Android Smartphone";
        }
        if (userAgent.contains("Windows NT 10.0") || userAgent.contains("Windows NT 11.0")) {
            if (userAgent.contains("Edg/")) return "Windows PC (Edge)";
            if (userAgent.contains("Chrome/")) return "Windows PC (Chrome)";
            if (userAgent.contains("Firefox/")) return "Windows PC (Firefox)";
            return "Windows PC (Desktop)";
        }
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return "Apple Mac / MacBook";
        }
        if (userAgent.contains("Linux")) return "Linux Workstation";
        return "Web-Client";
    }

    /**
     * Lay dia chi IP LAN thuc te cua may chu de cac may khac trong Wi-Fi/LAN co the ket noi
     */
    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (!host.startsWith("127.")) {
                            return host;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        DashboardUI ui = new DashboardUI(8080);
        ui.start();
    }
}
