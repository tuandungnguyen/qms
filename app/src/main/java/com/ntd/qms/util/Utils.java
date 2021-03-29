package com.ntd.qms.util;

public class Utils {

    public static String formatQueueNumber(String original_text, int length) {
        StringBuilder res = new StringBuilder();

        if (original_text.isEmpty())
            return "";
        if (original_text.length() < length) {
            for (int i = 0; i < length - original_text.length(); i++) {
                res.append("0");
            }
        }
        res.append(original_text);
        return res.toString();
    }
}
