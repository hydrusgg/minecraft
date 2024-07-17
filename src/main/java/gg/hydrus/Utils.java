package gg.hydrus;

public class Utils {

    public static boolean sleep(int ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    public static void println(String template, Object ...args) {
        System.out.println("[Hydrus] "+String.format(template, args));
    }
}
