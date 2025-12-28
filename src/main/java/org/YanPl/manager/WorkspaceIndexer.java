package org.YanPl.manager;

import org.YanPl.MineAgent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作区索引器，负责索引可用命令和预设文件
 */
public class WorkspaceIndexer {
    private final MineAgent plugin;
    private List<String> indexedCommands = new ArrayList<>();
    private List<String> indexedPresets = new ArrayList<>();

    public WorkspaceIndexer(MineAgent plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行完整索引
     */
    public void indexAll() {
        indexCommands();
        indexPresets();
    }

    /**
     * 索引所有可用的 Bukkit 命令
     */
    @SuppressWarnings("unchecked")
    public void indexCommands() {
        indexedCommands.clear();
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());
            
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            
            indexedCommands.addAll(knownCommands.keySet().stream()
                    .filter(name -> !name.contains(":")) // 过滤掉带前缀的命令，保留基础命令
                    .collect(Collectors.toList()));
            
            plugin.getLogger().info("已索引 " + indexedCommands.size() + " 个命令。");
        } catch (Exception e) {
            plugin.getLogger().warning("索引命令时出错: " + e.getMessage());
        }
    }

    /**
     * 索引 /plugins/MineAgent/preset/ 目录下的所有文件名
     */
    public void indexPresets() {
        indexedPresets.clear();
        File presetDir = new File(plugin.getDataFolder(), "preset");
        if (!presetDir.exists()) {
            presetDir.mkdirs();
            // 保存默认预设文件
            plugin.saveResource("preset/default.txt", false);
            plugin.saveResource("preset/coreprotect.txt", false);
            plugin.saveResource("preset/vault.txt", false);
        }
        
        File[] files = presetDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    indexedPresets.add(file.getName());
                }
            }
        }
        plugin.getLogger().info("已索引 " + indexedPresets.size() + " 个预设文件。");
    }

    public List<String> getIndexedCommands() {
        return indexedCommands;
    }

    public List<String> getIndexedPresets() {
        return indexedPresets;
    }
}
