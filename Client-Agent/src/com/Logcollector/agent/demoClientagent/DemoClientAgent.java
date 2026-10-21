package com.Logcollector.agent.demoClientagent;

import java.util.Scanner;

/**
 * Lop tong hop dieu phoi tat ca cac kich ban demo Client Agent.
 * Ban co the:
 * 1. Chay truc tiep tung file kich ban rieng le (LoginOfflineScenario, ProductClickScenario,...),
 * 2. HOAC chay file DemoClientAgent nay de chon kich ban theo Menu [1], [2], [3], [4].
 */
public class DemoClientAgent {

    public static void main(String[] args) {
        if (args.length > 0) {
            runByArgument(args);
            return;
        }

        printBanner();
        System.out.println("Vui long chon kich ban demo ban muon chay (1 - 5):");
        System.out.println("   [1] Kich ban Tang truong 200 Nguoi dung (Du bao cham, anh huong mua hang & ti le 404)");
        System.out.println("   [2] Kich ban Nguoi dung chon xem & mua san pham (Web Traffic)");
        System.out.println("   [3] Kich ban Dot bien luu luong (Traffic Spike / Flash Sale)");
        System.out.println("   [4] Kich ban Tan cong an ninh (Brute-force & Port scan alert)");
        System.out.println("   [5] Kich ban Dataset that OpenSSH_2k (Bao mat & Kiem thu chiu tai)");
        System.out.println("   [0] Thoat chuong trinh");
        System.out.print("👉 Lua chon cua ban: ");

        // Su dung try-with-resources de tu dong dong Scanner, tranh loi 'Resource leak: scanner is never closed'
        try (Scanner scanner = new Scanner(System.in)) {
            String choice = "";
            if (scanner.hasNextLine()) {
                choice = scanner.nextLine().trim();
            }

            switch (choice) {
                case "1":
                    UserSudendlyIncreaseScenario.main(new String[]{});
                    break;
                case "2":
                    ProductClickScenario.main(new String[]{});
                    break;
                case "3":
                    TrafficSpikeScenario.main(new String[]{});
                    break;
                case "4":
                    CyberAttackScenario.main(new String[]{});
                    break;
                case "5":
                    System.out.println("\nChon che do kiem thu Dataset OpenSSH_2k (Tong cong 2,000 log thuc te):");
                    System.out.println("   [1] Kich ban An ninh: Phat hien tan cong Brute-force tu hacker that (IP 112.95.230.3)");
                    System.out.println("   [2] Kich ban Chiu tai nhanh: Ban 500 log thuc te da luong qua UDP/TCP");
                    System.out.println("   [3] Kich ban Toan dien (Full Dataset): Phat lai TOAN BO 2,000 log thuc te tu OpenSSH_2k.log!");
                    System.out.print("👉 Che do cua ban (1, 2 hoac 3): ");
                    String subChoice = scanner.hasNextLine() ? scanner.nextLine().trim() : "3";
                    if ("3".equals(subChoice)) {
                        DatasetReplayScenario.main(new String[]{"2", "2000"});
                    } else if ("2".equals(subChoice)) {
                        DatasetReplayScenario.main(new String[]{"2", "500"});
                    } else {
                        DatasetReplayScenario.main(new String[]{"1"});
                    }
                    break;
                case "0":
                    System.out.println("Da thoat chuong trinh demo.");
                    break;
                default:
                    System.out.println("Dang chay mac dinh Kich ban 5 (Phat lai TOAN BO 2,000 log OpenSSH that)...");
                    DatasetReplayScenario.main(new String[]{"2", "2000"});
                    break;
            }
        }
    }

    private static void runByArgument(String[] args) {
        String arg = args[0].toLowerCase();
        switch (arg) {
            case "1":
            case "users":
            case "usersuddenly":
            case "login":
                UserSudendlyIncreaseScenario.main(args);
                break;
            case "2":
            case "product":
                ProductClickScenario.main(args);
                break;
            case "3":
            case "spike":
            case "traffic-spike":
                String[] passArgs = args.length > 1 ? new String[]{args[1]} : new String[]{};
                TrafficSpikeScenario.main(passArgs);
                break;
            case "4":
            case "attack":
                CyberAttackScenario.main(args);
                break;
            case "5":
            case "dataset":
            case "real":
                String[] datasetArgs = args.length > 1 ? new String[]{args[1]} : new String[]{};
                DatasetReplayScenario.main(datasetArgs);
                break;
            default:
                System.out.println("Khong tim thay kich ban '" + arg + "'. Chay Kich ban 2...");
                ProductClickScenario.main(args);
                break;
        }
    }

    private static void printBanner() {
        String dev = com.Logcollector.agent.SyslogFormatter.getDeviceDescription();
        String host = System.getProperty("server.host", "192.168.100.71");
        System.out.println("==================================================");
        System.out.println("        TRINH DIEU KHOEN DEMO CLIENT AGENT        ");
        System.out.println("   [THIET BI MAY CLIENT]: " + dev);
        System.out.println("   [SERVER LOG TIEP NHAN]: " + host + " (UDP: 514 / TCP: 9999)");
        System.out.println("==================================================");
    }
}
