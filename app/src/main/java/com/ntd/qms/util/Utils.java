package com.ntd.qms.util;

public class Utils {

    public static String formatQueueNumber(int original_number, int length) {
        StringBuilder res = new StringBuilder();
        String original_text = String.valueOf(original_number);

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


    public static int ordinalIndexOf(String str, String substr, int n) {
        int pos = str.indexOf(substr);
        while (--n > 0 && pos != -1)
            pos = str.indexOf(substr, pos + 1);
        return pos;
    }
}
