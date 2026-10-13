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
        System.out.println("Vui long chon kich ban demo ban muon chay (1 - 4):");
        System.out.println("   [1] Kich ban Dang nhap (Delay 3s test ngat mang & Queue)");
        System.out.println("   [2] Kich ban Nguoi dung chon xem & mua san pham (Web Traffic)");
        System.out.println("   [3] Kich ban Dot bien luu luong (Traffic Spike / Flash Sale)");
        System.out.println("   [4] Kich ban Tan cong an ninh (Brute-force & Port scan alert)");
        System.out.println("   [0] Thoat chuong trinh");
        System.out.print("👉 Lua chon cua ban: ");

        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                LoginOfflineScenario.main(new String[]{});
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
            case "0":
                System.out.println("Da thoat chuong trinh demo.");
                break;
            default:
                System.out.println("Lua chon khong hop le. Dang chay mac dinh Kich ban 2 (Product Click)...");
                ProductClickScenario.main(new String[]{});
                break;
        }
    }

    private static void runByArgument(String[] args) {
        String arg = args[0].toLowerCase();
        switch (arg) {
            case "1":
            case "login":
                LoginOfflineScenario.main(args);
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
            default:
                System.out.println("Khong tim thay kich ban '" + arg + "'. Chay Kich ban 2...");
                ProductClickScenario.main(args);
                break;
        }
    }

    private static void printBanner() {
        System.out.println("==================================================");
        System.out.println("        TRINH DIEU KHOEN DEMO CLIENT AGENT        ");
        System.out.println("==================================================");
    }
}
