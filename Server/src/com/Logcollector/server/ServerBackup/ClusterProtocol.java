package com.Logcollector.server.ServerBackup;

/**
 * Dinh nghia giao thuc truyen thong va cac hang so dong bo giua Server 1 (Primary)
 * va Server 2 (Backup / Secondary Server) qua kenh TCP rieng.
 */
public final class ClusterProtocol {

    private ClusterProtocol() {}

    public static final int DEFAULT_SYNC_PORT = 9997;

    // Cac loai hanh dong (Action) trao doi giua 2 Server
    public static final String ACTION_CLUSTER_JOIN = "CLUSTER_JOIN";
    public static final String ACTION_CLUSTER_ACK = "CLUSTER_ACK";
    public static final String ACTION_SYNC_BATCH = "SYNC_BATCH";
    public static final String ACTION_REPLICATE_LOG = "REPLICATE_LOG";
    public static final String ACTION_HEARTBEAT = "HEARTBEAT";
    public static final String ACTION_OVERLOAD_STATUS = "OVERLOAD_STATUS";
    public static final String ACTION_SYNC_PROGRESS = "SYNC_PROGRESS";
    public static final String ACTION_CLUSTER_STATUS = "CLUSTER_STATUS";

    public static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
