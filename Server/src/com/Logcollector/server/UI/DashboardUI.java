package com.Logcollector.server.UI;

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

/**
 * DashboardUI (Local Embedded Web Server Launcher):
 * Khoi dong may chu HTTP noi bo tai cong 8080 de phuc vu cac trang Web Dashboard,
 * Login, Register va Catalog san pham ma khong can cai them Web Server ngoai (Apache/Nginx/NodeJS).
 * Tu dong mo trinh duyet mac dinh cua may tinh khi khoi dong.
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
            httpServer.createContext("/", new StaticFileHandler(webRoot));
            httpServer.setExecutor(null); // Dung bo dieu phoi mac dinh
            httpServer.start();

            System.out.println("==================================================");
            System.out.println("   WEB DASHBOARD DA SAN SANG TAI LOCALHOST        ");
            System.out.println("   URL: http://localhost:" + port + "/dashboard.html");
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

    public static void main(String[] args) {
        DashboardUI ui = new DashboardUI(8080);
        ui.start();
    }
}
