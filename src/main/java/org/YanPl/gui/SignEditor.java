package org.YanPl.gui;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import de.rapha149.signgui.exception.SignGUIVersionException;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.YanPl.FancyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 告示牌编辑器
 * 使用 SignGUI 库提供虚拟告示牌编辑功能
 * 支持长文本通过多次告示牌编辑
 */
public class SignEditor {

    private final FancyHelper plugin;
    private final Player player;
    private final String defaultText;
    private final EditType editType;

    // 每行最大显示宽度（告示牌限制）
    public static final int MAX_LINE_WIDTH = 15;
    // 每页最大宽度（4行 x 15宽度）
    private static final int MAX_WIDTH_PER_PAGE = 60;

    // 静态会话映射，用于多页编辑
    private static final Map<UUID, EditSession> editSessions = new ConcurrentHashMap<>();

    /**
     * 编辑类型枚举
     */
    public enum EditType {
        TITLE_DESC,      // 编辑计划标题和描述
        STEP_DESC_NOTES, // 编辑步骤描述和备注
        SINGLE_LINE      // 编辑单行文本
    }

    /**
     * 计算字符串的显示宽度
     * 汉字等全角字符宽度为2，英文等半角字符宽度为1
     */
    public static int getDisplayWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int width = 0;
        for (char c : text.toCharArray()) {
            // 判断是否为全角字符
            if (isFullWidth(c)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    /**
     * 判断字符是否为全角字符
     */
    private static boolean isFullWidth(char c) {
        // CJK统一汉字范围
        if (c >= '\u4E00' && c <= '\u9FFF') return true;
        // CJK扩展A
        if (c >= '\u3400' && c <= '\u4DBF') return true;
        // 全角符号、日文假名等
        if (c >= '\u3000' && c <= '\u303F') return true;
        if (c >= '\u3040' && c <= '\u309F') return true; // 平假名
        if (c >= '\u30A0' && c <= '\u30FF') return true; // 片假名
        if (c >= '\uFF00' && c <= '\uFFEF') return true; // 全角ASCII、全角标点
        // 朝鲜文
        if (c >= '\uAC00' && c <= '\uD7AF') return true;
        
        return false;
    }

    /**
     * 按显示宽度截取字符串
     * @param text 原文本
     * @param maxWidth 最大宽度
     * @return 截取后的文本
     */
    private static String substringByWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        int width = 0;
        StringBuilder result = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            int charWidth = isFullWidth(c) ? 2 : 1;
            if (width + charWidth > maxWidth) {
                break;
            }
            width += charWidth;
            result.append(c);
        }
        
        return result.toString();
    }

    /**
     * 按显示宽度分割字符串为多行
     * @param text 原文本
     * @param maxWidth 每行最大宽度
     * @return 分割后的行列表
     */
    private static List<String> splitByWidth(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return lines;
        }
        
        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;
        
        for (char c : text.toCharArray()) {
            int charWidth = isFullWidth(c) ? 2 : 1;
            
            if (currentWidth + charWidth > maxWidth) {
                // 当前行已满，添加到列表
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentWidth = 0;
            }
            
            currentLine.append(c);
            currentWidth += charWidth;
        }
        
        // 添加最后一行
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }

    /**
     * 编辑会话，用于处理多页编辑
     */
    private static class EditSession {
        final StringBuilder fullText;
        final Consumer<String> callback;
        int currentPage;
        final int totalPages;

        EditSession(String initialText, Consumer<String> callback) {
            this.fullText = new StringBuilder(initialText != null ? initialText : "");
            this.callback = callback;
            this.currentPage = 0;
            // 按显示宽度计算总页数
            int totalWidth = getDisplayWidth(fullText.toString());
            this.totalPages = Math.max(1, (int) Math.ceil((double) totalWidth / MAX_WIDTH_PER_PAGE));
        }

        String getCurrentPageText() {
            int totalWidth = getDisplayWidth(fullText.toString());
            int startWidth = currentPage * MAX_WIDTH_PER_PAGE;
            int endWidth = Math.min(startWidth + MAX_WIDTH_PER_PAGE, totalWidth);
            
            // 按宽度提取文本
            return extractByWidth(fullText.toString(), startWidth, endWidth);
        }

        /**
         * 按宽度范围提取文本
         */
        private String extractByWidth(String text, int startWidth, int endWidth) {
            StringBuilder result = new StringBuilder();
            int currentWidth = 0;
            
            for (char c : text.toCharArray()) {
                int charWidth = isFullWidth(c) ? 2 : 1;
                
                if (currentWidth >= startWidth && currentWidth < endWidth) {
                    result.append(c);
                }
                
                currentWidth += charWidth;
                
                if (currentWidth >= endWidth) {
                    break;
                }
            }
            
            return result.toString();
        }

        void updateCurrentPage(String newText) {
            int totalWidth = getDisplayWidth(fullText.toString());
            int startWidth = currentPage * MAX_WIDTH_PER_PAGE;
            int endWidth = Math.min(startWidth + MAX_WIDTH_PER_PAGE, totalWidth);
            
            // 找到要删除的字符范围
            int startChar = 0;
            int endChar = fullText.length();
            int currentWidth = 0;
            boolean foundStart = false;
            
            for (int i = 0; i < fullText.length(); i++) {
                char c = fullText.charAt(i);
                int charWidth = isFullWidth(c) ? 2 : 1;
                
                if (!foundStart && currentWidth >= startWidth) {
                    startChar = i;
                    foundStart = true;
                }
                
                if (currentWidth >= endWidth) {
                    endChar = i;
                    break;
                }
                
                currentWidth += charWidth;
            }
            
            // 删除当前页内容
            if (foundStart) {
                fullText.delete(startChar, endChar);
                // 插入新内容
                fullText.insert(startChar, newText);
            } else {
                // 追加到末尾
                fullText.append(newText);
            }
        }

        boolean hasNextPage() {
            int totalWidth = getDisplayWidth(fullText.toString());
            return currentPage < totalPages - 1 || 
                   (currentPage == totalPages - 1 && totalWidth > (currentPage + 1) * MAX_WIDTH_PER_PAGE);
        }

        boolean hasPrevPage() {
            return currentPage > 0;
        }

        void nextPage() {
            if (hasNextPage()) {
                currentPage++;
            }
        }

        void prevPage() {
            if (hasPrevPage()) {
                currentPage--;
            }
        }

        String getFullText() {
            return fullText.toString().trim();
        }
    }

    /**
     * 构造函数
     *
     * @param plugin      插件实例
     * @param player      玩家
     * @param defaultText 默认文本
     * @param editType    编辑类型
     */
    public SignEditor(FancyHelper plugin, Player player, String defaultText, EditType editType) {
        this.plugin = plugin;
        this.player = player;
        this.defaultText = defaultText != null ? defaultText : "";
        this.editType = editType;
    }

    /**
     * 打开编辑器
     *
     * @param callback 编辑完成回调，返回完整文本
     */
    public void open(Consumer<String> callback) {
        // 检查文本长度，决定编辑方式
        if (getDisplayWidth(defaultText) > MAX_WIDTH_PER_PAGE) {
            // 长文本：使用多页编辑
            openMultiPageEditor(callback);
        } else {
            // 短文本：单页编辑
            openSinglePage(callback);
        }
    }

    /**
     * 打开单页编辑器
     */
    private void openSinglePage(Consumer<String> callback) {
        String[] lines = parseTextToLines(defaultText);

        try {
            SignGUI gui = SignGUI.builder()
                .setLines(lines[0], lines[1], lines[2], lines[3])
                .setHandler((p, result) -> {
                    // 获取玩家编辑的内容
                    String[] resultLines = result.getLinesWithoutColor();
                    String fullText = mergeLines(resultLines);

                    if (((FancyHelper) plugin).getConfigManager().isDebug()) {
                        plugin.getLogger().info("告示牌编辑完成: " + fullText);
                    }

                    // 调用回调
                    if (callback != null) {
                        callback.accept(fullText);
                    }

                    return Collections.emptyList();
                })
                .build();

            gui.open(player);

            if (((FancyHelper) plugin).getConfigManager().isDebug()) {
                plugin.getLogger().info("已打开虚拟告示牌编辑器");
            }

        } catch (SignGUIVersionException e) {
            plugin.getLogger().warning("当前服务器版本不支持 SignGUI: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "» " + ChatColor.WHITE + "当前服务器版本不支持告示牌编辑功能");
        }
    }

    /**
     * 打开多页编辑器
     */
    private void openMultiPageEditor(Consumer<String> callback) {
        EditSession session = new EditSession(defaultText, callback);
        editSessions.put(player.getUniqueId(), session);

        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "文本较长，将分页编辑");
        player.sendMessage(ChatColor.GRAY + "  共 " + ChatColor.WHITE + session.totalPages + ChatColor.GRAY + " 页，当前第 " + ChatColor.WHITE + "1" + ChatColor.GRAY + " 页");

        openPage(session.currentPage);
    }

    /**
     * 打开指定页的编辑器
     */
    private void openPage(int pageIndex) {
        UUID uuid = player.getUniqueId();
        EditSession session = editSessions.get(uuid);

        if (session == null) {
            return;
        }

        String pageText = session.getCurrentPageText();
        String[] lines = parseTextToLines(pageText);

        // 在第一行显示页码提示
        String pageHint = "第" + (pageIndex + 1) + "/" + session.totalPages + "页";
        if (lines[0].isEmpty()) {
            lines[0] = pageHint;
        }

        try {
            SignGUI gui = SignGUI.builder()
                .setLines(lines[0], lines[1], lines[2], lines[3])
                .setHandler((p, result) -> {
                    EditSession currentSession = editSessions.get(uuid);
                    if (currentSession == null) {
                        return Collections.emptyList();
                    }

                    // 获取玩家编辑的内容
                    String[] resultLines = result.getLinesWithoutColor();
                    String newText = mergeLines(resultLines);

                    // 更新当前页内容
                    currentSession.updateCurrentPage(newText);

                    if (((FancyHelper) plugin).getConfigManager().isDebug()) {
                        plugin.getLogger().info("第 " + (pageIndex + 1) + " 页编辑完成: " + newText);
                    }

                    // 显示操作选项
                    return buildActionList(currentSession);
                })
                .build();

            gui.open(player);

        } catch (SignGUIVersionException e) {
            plugin.getLogger().warning("当前服务器版本不支持 SignGUI: " + e.getMessage());
            editSessions.remove(uuid);
        }
    }

    /**
     * 构建操作列表（继续编辑下一页、完成、取消）
     */
    private List<SignGUIAction> buildActionList(EditSession session) {
        List<SignGUIAction> actions = new ArrayList<>();
        UUID uuid = player.getUniqueId();

        if (session.hasNextPage()) {
            // 还有下一页，打开下一页
            session.nextPage();
            actions.add(SignGUIAction.run(() -> {
                player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + 
                    "继续编辑第 " + ChatColor.WHITE + (session.currentPage + 1) + ChatColor.YELLOW + " 页");
                openPage(session.currentPage);
            }));
        } else {
            // 所有页面编辑完成
            String fullText = session.getFullText();
            editSessions.remove(uuid);

            if (session.callback != null) {
                session.callback.accept(fullText);
            }

            actions.add(SignGUIAction.run(() -> {
                player.sendMessage(ChatColor.GREEN + "» " + ChatColor.WHITE + "编辑完成");
            }));
        }

        return actions;
    }

    /**
     * 解析文本为4行（告示牌限制）
     * 按显示宽度智能分行：优先按换行符分割，超长行自动截断
     */
    private String[] parseTextToLines(String text) {
        String[] result = new String[4];

        if (text == null || text.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                result[i] = "";
            }
            return result;
        }

        // 按换行符分割
        String[] parts = text.split("\n", -1);

        int lineIndex = 0;
        for (int i = 0; i < parts.length && lineIndex < 4; i++) {
            String part = parts[i];
            
            // 按显示宽度分割超长行
            List<String> subLines = splitByWidth(part, MAX_LINE_WIDTH);
            
            for (String subLine : subLines) {
                if (lineIndex >= 4) break;
                result[lineIndex] = subLine;
                lineIndex++;
            }
        }

        // 填充空行
        while (lineIndex < 4) {
            result[lineIndex] = "";
            lineIndex++;
        }

        return result;
    }

    /**
     * 合并4行为完整文本
     */
    private String mergeLines(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 静态初始化 - SignGUI 库不需要额外初始化
     */
    public static void init(FancyHelper plugin) {
        // SignGUI 库不需要额外初始化
    }

    /**
     * 处理玩家输入（由ChatListener调用）
     * SignGUI 库自己处理输入，此方法保留以兼容旧的调用方式
     *
     * @param player 玩家
     * @param input  输入内容
     * @return 是否已处理
     */
    public static boolean handleInput(Player player, String input) {
        // SignGUI 库自己处理输入
        return false;
    }

    /**
     * 检查玩家是否在编辑文本
     * SignGUI 库内部管理状态，这里检查会话
     *
     * @param player 玩家
     * @return 是否在编辑
     */
    public static boolean isEditing(Player player) {
        return editSessions.containsKey(player.getUniqueId());
    }

    /**
     * 取消玩家的文本编辑
     *
     * @param player 玩家
     */
    public static void cancelEdit(Player player) {
        editSessions.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "» " + ChatColor.YELLOW + "已取消编辑");
    }

    /**
     * 清理所有编辑会话
     */
    public static void cleanup() {
        editSessions.clear();
    }
}
