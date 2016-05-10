package scapdash;

public class Util {
    static String getenv(String variable, String defaultValue) {
        String value = System.getenv(variable);
        return value == null ? defaultValue : value;
    }
}
