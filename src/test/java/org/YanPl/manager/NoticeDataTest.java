package org.YanPl.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoticeManager.NoticeData 测试")
class NoticeDataTest {

    @Test
    @DisplayName("构造函数应该正确设置属性")
    void testConstructor() {
        NoticeManager.NoticeData data = new NoticeManager.NoticeData(true, "公告内容");
        
        assertTrue(data.enabled);
        assertEquals("公告内容", data.text);
    }

    @Test
    @DisplayName("公告禁用时 enabled 为 false")
    void testDisabledNotice() {
        NoticeManager.NoticeData data = new NoticeManager.NoticeData(false, "内容");
        
        assertFalse(data.enabled);
    }

    @Test
    @DisplayName("公告内容可以为空")
    void testEmptyText() {
        NoticeManager.NoticeData data = new NoticeManager.NoticeData(true, "");
        
        assertTrue(data.enabled);
        assertEquals("", data.text);
    }

    @Test
    @DisplayName("公告内容可以为 null")
    void testNullText() {
        NoticeManager.NoticeData data = new NoticeManager.NoticeData(true, null);
        
        assertTrue(data.enabled);
        assertNull(data.text);
    }
}
