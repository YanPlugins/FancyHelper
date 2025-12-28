package org.YanPl.listener;

import org.YanPl.MineAgent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 聊天监听器，负责拦截处于 CLI 模式玩家的消息
 */
public class ChatListener implements Listener {
    private final MineAgent plugin;

    public ChatListener(MineAgent plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 检查玩家是否处于 CLI 模式或正在等待协议
        if (plugin.getCliManager().handleChat(player, message)) {
            // 尝试通过清除收件人来减少 Secure Chat 警告
            event.getRecipients().clear();
            event.setCancelled(true);
        }
    }
}
