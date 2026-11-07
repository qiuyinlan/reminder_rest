package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

/** 本机个人资料：目前只有性别，用于决定是否展示经期相关功能。 */
final class UserProfile {
    static final String FEMALE = "female";
    static final String MALE = "male";
    static final String UNSET = "";

    private static final String PREFS = "user_profile";
    private static final String KEY_GENDER = "gender";

    private UserProfile() { }

    static String gender(Context context) {
        String value = prefs(context).getString(KEY_GENDER, UNSET);
        return FEMALE.equals(value) || MALE.equals(value) ? value : UNSET;
    }

    static void setGender(Context context, String gender) {
        String value = FEMALE.equals(gender) ? FEMALE : MALE.equals(gender) ? MALE : UNSET;
        prefs(context).edit().putString(KEY_GENDER, value).apply();
    }

    static boolean isFemale(Context context) {
        return FEMALE.equals(gender(context));
    }

    /** 设置页展示用的中文名。 */
    static String label(Context context) {
        String gender = gender(context);
        return FEMALE.equals(gender) ? "女生" : MALE.equals(gender) ? "男生" : "未选择";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
