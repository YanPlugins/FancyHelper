package org.YanPl.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.YanPl.FancyHelper;
import org.YanPl.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 更新管理器：负责从 GitHub 获取最新版本并提醒管理员。
 */
public class UpdateManager implements Listener {
    private final FancyHelper plugin;
    private final String repoUrl = "https://api.github.com/repos/baicaizhale/FancyHelper/releases/latest";
    private String latestVersion = null;
    private String downloadUrl = null;
    private String latestFileName = null;
    private String releaseOverview = null; // Release Overview (AI生成的版本概述)
    private boolean hasUpdate = false;

    public UpdateManager(FancyHelper plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 检查更新。
     */
    public void checkForUpdates() {
        checkForUpdates(null);
    }

    /**
     * 检查更新并通知特定玩家。
     * @param sender 通知对象（可为 null，仅输出到控制台）
     */
    public void checkForUpdates(Player sender) {
        if (!plugin.getConfigManager().isCheckUpdate() && sender == null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(repoUrl))
                        .header("User-Agent", "FancyHelper-Updater")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String jsonResponse = response.body();
                    JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

                    latestVersion = jsonObject.get("tag_name").getAsString().replace("v", "");

                    // 获取 Release Overview (body 字段的前半部分，AI 生成的内容)
                    if (jsonObject.has("body") && !jsonObject.get("body").isJsonNull()) {
                        String body = jsonObject.get("body").getAsString();
                        // 提取 Overview 部分（## 🚀 版本概述 到 ## What's Changed 之前）
                        releaseOverview = extractOverview(body);
                    }

                    // 获取第一个 .jar 文件的下载地址和文件名
                    JsonArray assets = jsonObject.getAsJsonArray("assets");
                    for (JsonElement assetElement : assets) {
                        JsonObject asset = assetElement.getAsJsonObject();
                        String name = asset.get("name").getAsString();
                        if (name.endsWith(".jar")) {
                            downloadUrl = asset.get("browser_download_url").getAsString();
                            latestFileName = name;
                            break;
                        }
                    }

                    if (downloadUrl == null) {
                        downloadUrl = jsonObject.get("html_url").getAsString();
                        latestFileName = "FancyHelper-v" + latestVersion + ".jar";
                    }

                    String currentVersion = plugin.getDescription().getVersion();

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        hasUpdate = true;
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: v" + latestVersion));
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f下载地址: " + downloadUrl));

                        // 显示 Release Overview（控制台）
                        if (releaseOverview != null && !releaseOverview.isEmpty()) {
                            Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                            for (String line : releaseOverview.split("\\r?\\n")) {
                                String trimmedLine = line.trim();
                                if (!trimmedLine.isEmpty()) {
                                    trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                                    Bukkit.getConsoleSender().sendMessage(" §b§l> §r" + trimmedLine);
                                }
                            }
                        }

                        // 自动升级逻辑
                        if (plugin.getConfigManager().isAutoUpgrade()) {
                            Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到自动升级已开启，正在后台下载更新..."));
                            downloadAndInstall(null, true, true);
                        } else {
                            Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f如需自动下载更新，请将 config.yml 中的 auto_upgrade 设置为 true"));
                        }

                        if (sender != null) {
                            sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: " + ChatColor.WHITE + "v" + latestVersion));
                            // 显示 Release Overview
                            if (releaseOverview != null && !releaseOverview.isEmpty()) {
                                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                                // 使用正则表达式分割，支持 \n 和 \r\n
                                for (String line : releaseOverview.split("\\r?\\n")) {
                                    // 移除每行开头和结尾的空白字符
                                    String trimmedLine = line.trim();
                                    if (!trimmedLine.isEmpty()) {
                                        // 移除 Markdown 列表符号 * - 等
                                        trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                                        sender.sendMessage(" §b§l> §r" + trimmedLine);
                                    }
                                }
                            }
                            if (plugin.getConfigManager().isAutoUpgrade()) {
                                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f正在执行自动更新..."));
                            } else {
                                sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f使用 " + ChatColor.AQUA + "/fancy upgrade" + ChatColor.WHITE + " 自动下载并更新。"));
                            }
                        }
                    } else {
                        hasUpdate = false;
                        // 无论 sender 是否为 null，都输出检查结果
                        String message = ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f当前已是最新版本 (v" + currentVersion + ")");
                        if (sender != null) {
                            sender.sendMessage(message);
                        } else {
                            Bukkit.getConsoleSender().sendMessage(message);
                        }
                    }
                } else {
                    Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新失败：服务器响应异常。"));
                    if (sender != null) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新失败：服务器响应异常。"));
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("检查更新失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新失败: " + e.getMessage()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("检查更新被中断: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检查更新被中断: " + e.getMessage()));
                }
            }
        });
    }

    /**
     * 下载并安装更新。
     * @param sender 发起更新的玩家（可为 null）
     */
    public void downloadAndInstall(Player sender) {
        downloadAndInstall(sender, false);
    }

    /**
     * 下载并安装更新。
     * @param sender 发起更新的玩家（可为 null）
     * @param autoReload 是否在下载完成后自动重载
     */
    public void downloadAndInstall(Player sender, boolean autoReload) {
        downloadAndInstall(sender, autoReload, false);
    }

    /**
     * 下载并安装更新。
     * @param sender 发起更新的玩家（可为 null）
     * @param autoReload 是否在下载完成后自动重载
     * @param alreadyAsync 是否已经在异步任务中执行
     */
    public void downloadAndInstall(Player sender, boolean autoReload, boolean alreadyAsync) {
        plugin.getLogger().info("下载并安装更新被调用 - 有可用更新: " + hasUpdate + ", 下载地址: " + downloadUrl);

        if (!hasUpdate || downloadUrl == null) {
            if (sender != null) sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f当前没有可用的更新。"));
            plugin.getLogger().warning("无法下载更新：有可用更新=" + hasUpdate + ", 下载地址=" + downloadUrl);
            return;
        }

        if (sender != null) sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f开始下载更新..."));

        Runnable downloadTask = () -> {
            String mirror = plugin.getConfigManager().getUpdateMirror();
            String finalUrl = mirror + downloadUrl;

            plugin.getLogger().info("开始下载更新，镜像源: " + mirror);
            plugin.getLogger().info("下载URL: " + finalUrl);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl))
                        .header("User-Agent", "FancyHelper-Updater")
                        .timeout(Duration.ofSeconds(60))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    plugin.getLogger().severe("下载失败: " + response.statusCode());
                    if (sender != null) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f下载失败: " + response.statusCode()));
                    }
                    throw new IOException("下载失败: " + response.statusCode());
                }

                // 准备保存新版本
                File pluginsDir = plugin.getDataFolder().getParentFile();
                String newJarName = latestFileName;
                File newJarFile = new File(pluginsDir, newJarName);

                plugin.getLogger().info("准备保存新版本到: " + newJarFile.getAbsolutePath());

                try (InputStream inputStream = response.body()) {
                    Files.copy(inputStream, newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                plugin.getLogger().info("文件下载完成，大小: " + newJarFile.length() + " 字节");

                // 尝试移动旧版本到 plugins/FancyHelper/old 目录
                File oldDir = new File(plugin.getDataFolder(), "old");
                if (!oldDir.exists()) oldDir.mkdirs();

                boolean moved = false;
                String moveError = "";

                // 直接遍历 plugins 目录寻找旧版文件
                File[] files = pluginsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                            String fileName = file.getName();
                            // 匹配逻辑：文件名包含 fancyhelper (不区分大小写) 且不是刚刚下载的新文件
                            if (fileName.toLowerCase().contains("fancyhelper") && !fileName.equals(newJarName)) {
                                try {
                                    File destOldJar = new File(oldDir, fileName);
                                    Files.move(file.toPath(), destOldJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    moved = true;
                                    plugin.getLogger().info("已将旧版文件 [" + fileName + "] 移动至 plugins/FancyHelper/old/");
                                } catch (IOException e) {
                                    moveError = e.getMessage();
                                    plugin.getLogger().warning("无法移动旧版文件 [" + fileName + "]: " + moveError);
                                }
                            }
                        }
                    }
                }

                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载完成！"));
                    if (moved) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f旧版本已成功移动至 plugins/FancyHelper/old/"));
                    } else if (!moveError.isEmpty()) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f提示：由于系统锁定，部分旧版 JAR 无法自动移动。"));
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f请在下次重启前手动处理。"));
                    }
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f新版本已就绪: " + newJarName));
                    if (!autoReload) {
                        sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f请重启服务器或使用 PlugMan 重载以完成更新。"));
                    }
                } else {
                    Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载完成！"));
                    if (moved) {
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f旧版本已成功移动至 plugins/FancyHelper/old/"));
                    } else if (!moveError.isEmpty()) {
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f提示：由于系统锁定，部分旧版 JAR 无法自动移动。"));
                        Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f请在下次重启前手动处理。"));
                    }
                    Bukkit.getConsoleSender().sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f新版本已就绪: " + newJarName));
                }

                if (autoReload) {
                    plugin.getLogger().info("准备执行自动重载...");
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // 强制使用控制台执行深度重载，避免权限或上下文问题
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fancy reload deeply");
                    });
                }
            } catch (IOException e) {
                plugin.getLogger().severe("更新下载失败: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载失败: " + e.getMessage()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                plugin.getLogger().severe("更新下载被中断: " + e.getMessage());
                if (sender != null) {
                    sender.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新下载被中断: " + e.getMessage()));
                }
            }
        };

        // 根据是否已经在异步任务中来决定如何执行
        if (alreadyAsync) {
            downloadTask.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, downloadTask);
        }
    }

    /**
     * 从 Release body 中提取 Overview 部分。
     * @param body Release body 内容
     * @return Overview 部分（AI 生成的版本概述）
     */
    private String extractOverview(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        // 查找 "## Overview" 的位置
        int overviewStart = body.indexOf("## Overview");
        if (overviewStart == -1) {
            overviewStart = body.indexOf("## 🚀 版本概述");
        }
        if (overviewStart == -1) {
            overviewStart = body.indexOf("## 版本概述");
        }
        if (overviewStart == -1) {
            overviewStart = body.indexOf("## 🚀");
        }
        
        if (overviewStart == -1) {
            // 如果找不到特定标记，尝试获取第一段内容
            overviewStart = 0;
        }
        
        // 查找 "## What's Changed" 或 "## **Full Changelog**" 的位置作为结束
        int overviewEnd = body.indexOf("## What's Changed");
        if (overviewEnd == -1) {
            overviewEnd = body.indexOf("## **Full Changelog**");
        }
        if (overviewEnd == -1) {
            overviewEnd = body.indexOf("## Full Changelog");
        }
        
        if (overviewEnd == -1) {
            // 如果找不到结束标记，取前500字符或全部内容
            overviewEnd = Math.min(body.length(), 500);
        }
        
        String overview = body.substring(overviewStart, overviewEnd).trim();
        
        // 清理 Markdown 格式，转换为 Minecraft 格式
        overview = overview.replace("## Overview", "")
                           .replace("## 🚀 版本概述", "")
                           .replace("## 版本概述", "")
                           .replace("## 🚀", "")
                           .trim();
        
        return overview;
    }

    /**
     * 比较版本号。
     * @param current 当前版本
     * @param latest 最新版本
     * @return 如果最新版本大于当前版本则返回 true
     */
    private boolean isNewerVersion(String current, String latest) {
        if (current == null || latest == null) return false;

        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");

        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int curr = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int late = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;

            if (late > curr) return true;
            if (late < curr) return false;
        }
        return false;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOpUpdateNotify()) {
            return;
        }
        Player player = event.getPlayer();
        if (hasUpdate && player.isOp()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f检测到新版本: §a" + latestVersion));
                // 显示 Release Overview
                if (releaseOverview != null && !releaseOverview.isEmpty()) {
                    player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f更新内容:"));
                    // 使用正则表达式分割，支持 \n 和 \r\n
                    for (String line : releaseOverview.split("\\r?\\n")) {
                        // 移除每行开头和结尾的空白字符
                        String trimmedLine = line.trim();
                        if (!trimmedLine.isEmpty()) {
                            // 移除 Markdown 列表符号 * - 等
                            trimmedLine = trimmedLine.replaceFirst("^[*\\-\\d.]+\\s+", "");
                            player.sendMessage(" §b§l- §r" + trimmedLine);
                        }
                    }
                }
                player.sendMessage(ColorUtil.translateCustomColors("§zFancyHelper§b§r §7> §f使用 §e/fancy upgrade §f自动下载并更新。"));
            }, 40L); // 延迟 2 秒提示
        }
    }
}