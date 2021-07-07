package org.veriblock;

public class Utility {
    public static String encodeTimestamp(int tick) {
        int hrs = tick / 1000 / 3600;
        int min = ((tick / 1000 / 60) % 60);
        int sec = (tick / 1000) % 60;
        return ((hrs + "").length() == 1 ? "0" : "") + hrs + ":" + ((min + "").length() == 1 ? "0" : "") + min + ":" + ((sec + "").length() == 1 ? "0" : "") + sec;
    }
}
