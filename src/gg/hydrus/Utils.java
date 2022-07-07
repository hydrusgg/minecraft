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

    public static void println(String string) {
        System.out.println("[Hydrus] "+string);
    }
}
