package org.YanPl.util;

import net.md_5.bungee.api.ChatColor;

/**
 * 颜色工具类：处理自定义颜色代码转换
 * 
 * 支持的颜色代码：
 * - §x -> #11A8CD (青色偏蓝)
 * - §z -> #30AEE5 (明亮的天蓝色)
 */
public class ColorUtil {

    // 自定义颜色代码映射
    private static final ChatColor COLOR_X = ChatColor.of("#11A8CD");  // §x 颜色
    private static final ChatColor COLOR_Z = ChatColor.of("#30AEE5");  // §z 颜色

    /**
     * 转换自定义颜色代码 §x 和 §z 为实际颜色
     * 同时处理标准的 & 和 § 颜色代码
     *
     * @param message 包含颜色代码的消息
     * @return 转换后的消息
     */
    public static String translateCustomColors(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // 先处理标准的 & 和 § 颜色代码
        message = ChatColor.translateAlternateColorCodes('&', message);

        // 处理自定义颜色代码 §x 和 §z
        message = message.replace("§x", COLOR_X.toString());
        message = message.replace("§z", COLOR_Z.toString());

        return message;
    }

    /**
     * 仅转换自定义颜色代码 §x 和 §z
     * 不处理标准的 & 和 § 颜色代码
     *
     * @param message 包含颜色代码的消息
     * @return 转换后的消息
     */
    public static String translateCustomColorsOnly(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        message = message.replace("§x", COLOR_X.toString());
        message = message.replace("§z", COLOR_Z.toString());

        return message;
    }

    /**
     * 获取 §x 对应的颜色值
     *
     * @return #11A8CD
     */
    public static String getColorX() {
        return "#11A8CD";
    }

    /**
     * 获取 §z 对应的颜色值
     *
     * @return #30AEE5
     */
    public static String getColorZ() {
        return "#30AEE5";
    }
}
