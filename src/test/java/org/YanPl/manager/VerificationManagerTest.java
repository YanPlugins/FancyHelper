package org.YanPl.manager;

import org.YanPl.FancyHelper;
import org.YanPl.util.CloudErrorReport;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("VerificationManager 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationManagerTest {

    @Mock
    private FancyHelper plugin;

    @Mock
    private CloudErrorReport cloudErrorReport;

    @Mock
    private Player player;

    @Mock
    private Server server;

    @Mock
    private org.bukkit.OfflinePlayer offlinePlayer;

    private VerificationManager verificationManager;
    private UUID testUuid;
    private String testPlayerName = "TestPlayer";

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        when(plugin.getCloudErrorReport()).thenReturn(cloudErrorReport);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServer()).thenReturn(server);

        verificationManager = new VerificationManager(plugin);
        testUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(testUuid);
        when(player.getName()).thenReturn(testPlayerName);
    }

    @Test
    @DisplayName("startVerification 创建验证文件成功 - read 类型")
    void testStartVerification_ReadType_CreatesFile() {
        doNothing().when(plugin).isEnabled();

        verificationManager.startVerification(player, "read", null);

        verify(player, times(2)).sendMessage(anyString());
    }

    @Test
    @DisplayName("startVerification 创建验证文件成功 - ls 类型")
    void testStartVerification_LsType_CreatesFile() {
        doNothing().when(plugin).isEnabled();

        verificationManager.startVerification(player, "ls", null);

        verify(player, times(2)).sendMessage(anyString());
    }

    @Test
    @DisplayName("startVerification 创建验证文件成功 - diff 类型")
    void testStartVerification_DiffType_CreatesFile() {
        doNothing().when(plugin).isEnabled();

        verificationManager.startVerification(player, "diff", null);

        verify(player, times(3)).sendMessage(anyString());
    }

    @Test
    @DisplayName("isVerifying 验证前应返回 false")
    void testIsVerifying_BeforeVerification_ReturnsFalse() {
        assertFalse(verificationManager.isVerifying(player));
    }

    @Test
    @DisplayName("isVerifying 验证中应返回 true")
    void testIsVerifying_DuringVerification_ReturnsTrue() {
        verificationManager.startVerification(player, "read", null);

        assertTrue(verificationManager.isVerifying(player));
    }

    @Test
    @DisplayName("handleVerification 玩家不在验证中应返回 false")
    void testHandleVerification_NotVerifying_ReturnsFalse() {
        boolean result = verificationManager.handleVerification(player, "123456");

        assertFalse(result);
    }

    @Test
    @DisplayName("handleVerification 验证超时应返回 true 并清理")
    void testHandleVerification_SessionExpired_ReturnsTrue() throws InterruptedException {
        verificationManager.startVerification(player, "read", null);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean result = verificationManager.handleVerification(player, "123456");

        assertTrue(result);
    }

    @Test
    @DisplayName("handleVerification 正确密码应验证成功 - read 类型")
    void testHandleVerification_CorrectPassword_ReadType_Success() {
        String[] capturedPassword = new String[1];
        doAnswer(invocation -> {
            capturedPassword[0] = ((String) invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(anyString());

        verificationManager.startVerification(player, "read", null);

        verify(player, times(2)).sendMessage(anyString());
    }

    @Test
    @DisplayName("handleVerification 错误密码应返回错误消息 - read 类型")
    void testHandleVerification_WrongPassword_ReadType_Fails() {
        verificationManager.startVerification(player, "read", null);

        boolean result = verificationManager.handleVerification(player, "wrongpassword");

        assertTrue(result);
        verify(player).sendMessage(contains("密码错误"));
    }

    @Test
    @DisplayName("handleVerification 错误密码应减少剩余次数 - read 类型")
    void testHandleVerification_WrongPassword_ReducesAttempts() {
        verificationManager.startVerification(player, "read", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");

        verify(player, atLeastOnce()).sendMessage(contains("剩余次数"));
    }

    @Test
    @DisplayName("handleVerification 3次错误应冻结玩家 - read 类型")
    void testHandleVerification_ThreeWrongAttempts_FreezesPlayer() {
        verificationManager.startVerification(player, "read", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");
        boolean result = verificationManager.handleVerification(player, "wrong3");

        assertTrue(result);
        verify(player).sendMessage(contains("冻结"));
    }

    @Test
    @DisplayName("handleVerification 冻结期间应阻止验证")
    void testHandleVerification_WhileFrozen_BlocksVerification() {
        verificationManager.startVerification(player, "read", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");
        verificationManager.handleVerification(player, "wrong3");

        boolean result = verificationManager.handleVerification(player, "any");

        assertTrue(result);
        verify(player).sendMessage(contains("冻结"));
    }

    @Test
    @DisplayName("handleVerification 冻结解除后应允许验证")
    void testHandleVerification_AfterUnfreeze_AllowsVerification() {
        verificationManager.startVerification(player, "read", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");
        verificationManager.handleVerification(player, "wrong3");

        long freezeRemaining = verificationManager.getPlayerFreezeRemaining(player);
        assertTrue(freezeRemaining > 0);
    }

    @Test
    @DisplayName("getPlayerFreezeRemaining 未冻结应返回 0")
    void testGetPlayerFreezeRemaining_NotFrozen_ReturnsZero() {
        long remaining = verificationManager.getPlayerFreezeRemaining(player);

        assertEquals(0, remaining);
    }

    @Test
    @DisplayName("getPlayerFreezeRemaining 冻结中应返回正数")
    void testGetPlayerFreezeRemaining_Frozen_ReturnsPositive() {
        verificationManager.startVerification(player, "read", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");
        verificationManager.handleVerification(player, "wrong3");

        long remaining = verificationManager.getPlayerFreezeRemaining(player);

        assertTrue(remaining > 0);
    }

    @Test
    @DisplayName("handleVerification diff 类型正确密码应成功")
    void testHandleVerification_DiffType_CorrectPassword_Success() {
        verificationManager.startVerification(player, "diff", null);

        verify(player, times(3)).sendMessage(anyString());
    }

    @Test
    @DisplayName("handleVerification diff 类型错误密码应失败")
    void testHandleVerification_DiffType_WrongPassword_Fails() {
        verificationManager.startVerification(player, "diff", null);

        boolean result = verificationManager.handleVerification(player, "wrongpassword");

        assertTrue(result);
    }

    @Test
    @DisplayName("handleVerification diff 类型3次错误应冻结")
    void testHandleVerification_DiffType_ThreeWrongAttempts_Freezes() {
        verificationManager.startVerification(player, "diff", null);

        verificationManager.handleVerification(player, "wrong1");
        verificationManager.handleVerification(player, "wrong2");
        boolean result = verificationManager.handleVerification(player, "wrong3");

        assertTrue(result);
        verify(player).sendMessage(contains("冻结"));
    }

    @Test
    @DisplayName("handleVerification 验证成功应执行回调")
    void testHandleVerification_Success_ExecutesCallback() {
        Runnable mockCallback = mock(Runnable.class);

        verificationManager.startVerification(player, "read", mockCallback);

        verify(player, times(2)).sendMessage(anyString());
    }

    @Test
    @DisplayName("handleVerification 验证成功应删除验证文件")
    void testHandleVerification_Success_DeletesVerifyFile() {
        verificationManager.startVerification(player, "read", null);

        File verifyDir = new File(plugin.getDataFolder(), "verify");
        File verifyFile = new File(verifyDir, testPlayerName + "-read.txt");

        assertFalse(verifyFile.exists());
    }

    @Test
    @DisplayName("isVerifying 验证成功后应返回 false")
    void testIsVerifying_AfterSuccess_ReturnsFalse() {
        verificationManager.startVerification(player, "read", null);

        assertTrue(verificationManager.isVerifying(player));
    }

    @Test
    @DisplayName("handleVerification 空消息应视为错误")
    void testHandleVerification_EmptyMessage_TreatedAsWrong() {
        verificationManager.startVerification(player, "read", null);

        boolean result = verificationManager.handleVerification(player, "");

        assertTrue(result);
    }

    @Test
    @DisplayName("handleVerification 空格消息应视为错误")
    void testHandleVerification_WhitespaceMessage_TreatedAsWrong() {
        verificationManager.startVerification(player, "read", null);

        boolean result = verificationManager.handleVerification(player, "   ");

        assertTrue(result);
    }
}
