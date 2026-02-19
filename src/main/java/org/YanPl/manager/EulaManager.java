package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EULA 管理器：负责管理和实时监控 eula.txt 文件。
 */
public class EulaManager {
    private final FancyHelper plugin;
    private final File eulaFile;
    private final File licenseFile;
    private final List<String> eulaContent;
    private final List<String> licenseContent;
    private final AtomicBoolean isEulaValid = new AtomicBoolean(true);
    private final AtomicBoolean isLicenseValid = new AtomicBoolean(true);
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = true;

    /**
     * 初始化 EULA 管理器。
     * 
     * @param plugin 插件实例
     */
    public EulaManager(FancyHelper plugin) {
        this.plugin = plugin;
        File readmeDir = new File(plugin.getDataFolder(), "README");
        if (!readmeDir.exists()) {
            readmeDir.mkdirs();
        }
        this.eulaFile = new File(readmeDir, "eula.txt");
        this.licenseFile = new File(readmeDir, "license.txt");
        
        this.eulaContent = Arrays.asList(
            "=================",
            "  FancyHelper EULA",
            "=================",
            "当您继续使用本插件时，即表示您已阅读、理解并同意：",
            "",
            "1. 【AI 生成内容免责】",
            "   本插件集成第三方 AI 模型。AI 生成内容均由算法自动产出，",
            "   不代表开发者观点，可能存在错误、误导或不准确之处。",
            "   开发者不对 AI 生成内容承担任何责任。",
            "",
            "2. 【数据传输与隐私】",
            "   对话内容将传输至第三方服务（Cloudflare/OpenAI 等）。",
            "   请勿输入个人敏感信息、密码或商业秘密。",
            "   开发者不对第三方数据处理或网络传输安全负责。",
            "",
            "3. 【无担保声明】",
            "   本插件按\"原样 (AS IS)\"提供，不附带任何明示或暗示担保，",
            "   包括但不限于适销性、特定用途适用性或不侵权担保。",
            "   开发者不保证插件无错误、不中断或符合您的需求。",
            "",
            "4. 【责任限制】",
            "   在法律允许的最大范围内，开发者不对任何直接、间接、",
            "   偶然、特殊或后果性损害承担责任，包括但不限于：",
            "   数据丢失、服务器损坏、利润损失、业务中断等。",
            "   上述限制适用于合同、侵权（含过失）或其他法律依据。",
            "",
            "5. 【第三方服务】",
            "   本插件依赖第三方 AI 服务，其可用性、政策及内容审核",
            "   均不受开发者控制。第三方服务的任何问题开发者概不负责。",
            "",
            "6. 【使用合规】",
            "   用户须遵守当地法律法规，禁止利用本插件从事违法活动",
            "   或生成违法内容。违规行为的法律责任由用户独自承担。",
            "",
            "7. 【协议变更与终止】",
            "   开发者保留随时修改本协议的权利，修改后立即生效。",
            "   如您不同意修改后的条款，应立即停止使用本插件。",
            "",
            "8. 【管辖法律】",
            "   本协议受中华人民共和国法律管辖（不含冲突法规则）。",
            "   如发生争议，双方应友好协商；协商不成的，由开发者",
            "   所在地人民法院管辖。",
            "",
            "9. 【条款可分割性】",
            "   如本协议任一条款被认定为无效或不可执行，该条款应在",
            "   最小必要范围内修改，其余条款继续有效。",
            "================="
        );

        this.licenseContent = Arrays.asList(
            "本项目使用 GPL-3.0 开源。",
            "协议文本见 https://github.com/baicaizhale/FancyHelper?tab=GPL-3.0-1-ov-file"
        );

        ensureFiles();
        startRealtimeMonitoring();
    }

    /**
     * 确保 EULA 和 License 文件存在且内容正确。
     */
    private synchronized void ensureFiles() {
        ensureEulaFile();
        ensureLicenseFile();
    }

    private void ensureEulaFile() {
        try {
            boolean needsUpdate = false;
            if (!eulaFile.exists()) {
                needsUpdate = true;
            } else {
                List<String> currentContent = Files.readAllLines(eulaFile.toPath(), StandardCharsets.UTF_8);
                if (!eulaContent.equals(currentContent)) {
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                Files.write(eulaFile.toPath(), eulaContent, StandardCharsets.UTF_8);
                plugin.getLogger().info("EULA 文件已创建或还原: " + eulaFile.getPath());
            }
            isEulaValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("无法读写 EULA 文件 (可能由于权限不足): " + e.getMessage());
            isEulaValid.set(false);
        }
    }

    private void ensureLicenseFile() {
        try {
            boolean needsUpdate = false;
            if (!licenseFile.exists()) {
                needsUpdate = true;
            } else {
                List<String> currentContent = Files.readAllLines(licenseFile.toPath(), StandardCharsets.UTF_8);
                if (!licenseContent.equals(currentContent)) {
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                Files.write(licenseFile.toPath(), licenseContent, StandardCharsets.UTF_8);
                plugin.getLogger().info("License 文件已创建或还原: " + licenseFile.getPath());
            }
            isLicenseValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("无法读写 License 文件 (可能由于权限不足): " + e.getMessage());
            isLicenseValid.set(false);
        }
    }

    /**
     * 使用 Java WatchService 开始实时监控文件变动。
     */
    private void startRealtimeMonitoring() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path path = eulaFile.getParentFile().toPath();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE);

            watchThread = new Thread(() -> {
                while (running) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        break;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                        Path eventPath = (Path) event.context();
                        String fileName = eventPath.getFileName().toString();
                        if (fileName.equals(eulaFile.getName()) || fileName.equals(licenseFile.getName())) {
                            // 文件变动，立即还原
                            // 稍微延迟一下以防某些编辑器锁定文件
                            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                            ensureFiles();
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            }, "FancyHelper-EULA-Monitor");
            watchThread.setDaemon(true);
            watchThread.start();
            plugin.getLogger().info("EULA 实时监控已启动。");
        } catch (IOException e) {
            plugin.getLogger().severe("无法启动 EULA 实时监控: " + e.getMessage());
            // 回退到每分钟检查一次的模式
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::ensureEulaFile, 1200L, 1200L);
        }
    }

    /**
     * 检查 EULA 是否有效。
     * 
     * @return 如果有效则返回 true，否则返回 false。
     */
    public boolean isEulaValid() {
        return isEulaValid.get();
    }

    /**
     * 获取 EULA 文本内容。
     * 
     * @return EULA 文本列表
     */
    public List<String> getEulaContent() {
        return eulaContent;
    }

    /**
     * 创建一个包含 EULA 内容的虚拟书本。
     * 
     * @return 包含 EULA 的 ItemStack (WRITTEN_BOOK)
     */
    public ItemStack getEulaBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("FancyHelper EULA");
            meta.setAuthor("FancyHelper");
            
            // 按字符数分页，Minecraft 书本每页约 256 字符限制，使用 128 作为安全边距
            final int MAX_PAGE_CHARS = 128;
            StringBuilder pageBuilder = new StringBuilder();
            
            for (String line : eulaContent) {
                String lineWithNewline = line + "\n";
                
                // 如果当前行加入后会超出页面限制，先添加当前页面
                if (pageBuilder.length() + lineWithNewline.length() > MAX_PAGE_CHARS) {
                    if (pageBuilder.length() > 0) {
                        meta.addPage(pageBuilder.toString());
                        pageBuilder = new StringBuilder();
                    }
                }
                
                // 如果单行就超过页面限制，需要智能换行
                String remainingLine = lineWithNewline;
                while (remainingLine.length() > MAX_PAGE_CHARS) {
                    int splitIndex = findSmartSplitIndex(remainingLine, MAX_PAGE_CHARS);
                    pageBuilder.append(remainingLine, 0, splitIndex).append("\n");
                    meta.addPage(pageBuilder.toString());
                    pageBuilder = new StringBuilder();
                    remainingLine = remainingLine.substring(splitIndex).trim();
                    if (remainingLine.isEmpty()) break;
                }
                
                pageBuilder.append(remainingLine);
            }
            
            // 添加最后一页
            if (pageBuilder.length() > 0) {
                meta.addPage(pageBuilder.toString());
            }
            
            book.setItemMeta(meta);
        }
        
        return book;
    }
    
    /**
     * 智能换行：按空格或中文字符边界分割
     * 
     * @param text 要分割的文本
     * @param maxLen 最大长度
     * @return 建议的分割位置
     */
    private int findSmartSplitIndex(String text, int maxLen) {
        if (text.length() <= maxLen) return text.length();
        
        // 先尝试在空格处分割
        int lastSpace = text.lastIndexOf(' ', maxLen);
        if (lastSpace > maxLen / 2) {
            return lastSpace;
        }
        
        // 中文字符边界检查（中文在 Unicode 中位于 4E00-9FA5 范围）
        for (int i = maxLen - 1; i >= maxLen / 2; i--) {
            char c = text.charAt(i);
            if (c < 0x4E00 || c > 0x9FA5) { // 非中文字符
                return i + 1;
            }
        }
        
        return maxLen;
    }

    /**
     * 重新加载 EULA 和 License 文件并检查。
     */
    public void reload() {
        ensureFiles();
    }

    /**
     * 强制替换 EULA 和 License 文件为当前最新内容。
     * 通常用于版本更新时。
     */
    public void forceReplaceFiles() {
        try {
            Files.write(eulaFile.toPath(), eulaContent, StandardCharsets.UTF_8);
            Files.write(licenseFile.toPath(), licenseContent, StandardCharsets.UTF_8);
            plugin.getLogger().info("由于版本更新，EULA 和 License 文件已强制更新。");
            isEulaValid.set(true);
            isLicenseValid.set(true);
        } catch (IOException e) {
            plugin.getLogger().severe("强制更新文件失败: " + e.getMessage());
            isEulaValid.set(false);
            isLicenseValid.set(false);
        }
    }

    /**
     * 停止监控并释放资源。
     */
    public void shutdown() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }
}
