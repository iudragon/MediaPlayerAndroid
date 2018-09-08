package com.leejunhyung.utils;

import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextCaseUtils {
    private static String firstLetterToUpperCase(String target) {
        return target.substring(0, 1).toUpperCase() + target.substring(1);
    }

    public static String toPascalCaseWithSpace(String target) {
        Pattern pattern = Pattern.compile("_[a-zA-Z]");
        Matcher matcher = pattern.matcher(target);

        target = TextCaseUtils.firstLetterToUpperCase(target);

        if (matcher.find()) {
            return target.replaceAll(
                    pattern.toString(), " " +
                            matcher.group().toUpperCase().substring(1));
        }

        return target;
    }
}
