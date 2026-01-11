package org.YanPl.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.YanPl.MineAgent;
import org.YanPl.model.DialogueSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * CloudFlare Workers AI API 集成
 */
public class CloudFlareAI {
    private static final String API_RESPONSES_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/v1/responses";
    private static final String ACCOUNTS_URL = "https://api.cloudflare.com/client/v4/accounts";
    private final MineAgent plugin;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private String cachedAccountId = null;

    public CloudFlareAI(MineAgent plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 关闭 HTTP 客户端，释放资源
     */
    public void shutdown() {
        try {
            // 关闭dispatcher中的所有待执行任务
            httpClient.dispatcher().executorService().shutdown();
            // 等待最多5秒让任务完成
            if (!httpClient.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS)) {
                httpClient.dispatcher().executorService().shutdownNow();
                plugin.getLogger().warning("[CloudFlareAI] Executor service did not terminate in time, forcing shutdown.");
            }
            
            // 清空连接池
            httpClient.connectionPool().evictAll();
            
            // 关闭缓存
            if (httpClient.cache() != null) {
                try {
                    httpClient.cache().close();
                } catch (IOException ignored) {}
            }
            
            plugin.getLogger().info("[CloudFlareAI] HTTP client shutdown completed.");
        } catch (InterruptedException e) {
            httpClient.dispatcher().executorService().shutdownNow();
            plugin.getLogger().warning("[CloudFlareAI] Shutdown interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 自动获取 CloudFlare Account ID
     */
    private String fetchAccountId() throws IOException {
        if (cachedAccountId != null) return cachedAccountId;

        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        if (cfKey.isEmpty()) {
            throw new IOException("错误: 请先在配置文件中设置 cloudflare.cf_key。");
        }

        Request request = new Request.Builder()
                .url(ACCOUNTS_URL)
                .addHeader("Authorization", "Bearer " + cfKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取 Account ID 失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject resultJson = gson.fromJson(responseBody, JsonObject.class);
            
            if (resultJson.has("result") && resultJson.getAsJsonArray("result").size() > 0) {
                cachedAccountId = resultJson.getAsJsonArray("result").get(0).getAsJsonObject().get("id").getAsString();
                return cachedAccountId;
            } else {
                throw new IOException("未找到关联的 CloudFlare 账户，请检查 cf_key 权限。");
            }
        }
    }

    /**
     * 发送对话请求
     */
    public String chat(DialogueSession session, String systemPrompt) throws IOException {
        String cfKey = plugin.getConfigManager().getCloudflareCfKey();
        String model = plugin.getConfigManager().getCloudflareModel();

        if (cfKey == null || cfKey.isEmpty()) {
            return "错误: 请先在配置文件中设置 CloudFlare cf_key。";
        }

        if (model == null || model.isEmpty()) {
            model = "@cf/openai/gpt-oss-120b";
            plugin.getLogger().warning("[AI] 模型名称为空，已回退到默认值: " + model);
        }

        // 自动获取 Account ID
        String accountId;
        try {
            accountId = fetchAccountId();
        } catch (IOException e) {
            plugin.getLogger().severe("[AI Error] Failed to fetch Account ID: " + e.getMessage());
            throw e;
        }

        // 使用 /ai/v1/responses 接口，这是 gpt-oss-120b 推荐的接口
        String url = String.format(API_RESPONSES_URL, accountId);
        plugin.getLogger().info("[AI Request] URL: " + url);

        JsonArray messagesArray = new JsonArray();

        // 1. 添加系统提示词 (作为 system 角色消息加入 input 数组)
        // 注意：某些模型可能不支持 instructions 字段，或者该字段导致了 token 错误
        // 我们尝试标准的 system message 方式
        String safeSystemPrompt = (systemPrompt != null && !systemPrompt.isEmpty()) ? systemPrompt : "You are a helpful assistant.";
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", safeSystemPrompt);
        messagesArray.add(systemMsg);
        plugin.getLogger().info("[AI Request] System prompt added (length: " + safeSystemPrompt.length() + ")");

        // 2. 添加历史记录 (role: user/assistant)
        for (DialogueSession.Message msg : session.getHistory()) {
            String content = msg.getContent();
            String role = msg.getRole();
            
            // 严格检查：跳过任何null或空的内容
            if (content == null || content.trim().isEmpty() || role == null || role.trim().isEmpty()) {
                plugin.getLogger().warning("[AI Request] Skipping message with null/empty content or role");
                continue;
            }
            
            // 之前的逻辑跳过了 system 消息，现在我们需要确保不重复添加
            if ("system".equalsIgnoreCase(role)) continue;
            
            JsonObject m = new JsonObject();
            m.addProperty("role", role.trim());
            m.addProperty("content", content.trim());
            messagesArray.add(m);
            plugin.getLogger().info("[AI Request] Added message - Role: " + role + ", Content length: " + content.length());
        }

        // 如果没有任何消息，至少添加一条占位符消息
        if (messagesArray.size() == 0) {
            JsonObject m = new JsonObject();
            m.addProperty("role", "user");
            m.addProperty("content", "Hello");
            messagesArray.add(m);
        }

        // 构建符合 /ai/v1/responses 接口要求的请求体
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("model", model);
        bodyJson.add("input", messagesArray);
        
        // 移除 instructions 字段，改用 system message
        // if (systemPrompt != null && !systemPrompt.isEmpty()) {
        //    bodyJson.addProperty("instructions", systemPrompt);
        // }
        
        // 如果是 gpt-oss 模型，添加推理参数
        if (model.contains("gpt-oss")) {
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "medium");
            bodyJson.add("reasoning", reasoning);
        }
        
        String bodyString = gson.toJson(bodyJson);

        plugin.getLogger().info("[AI Request] Model: " + model);
        plugin.getLogger().info("[AI Request] Total messages in array: " + messagesArray.size());
        // 打印部分 Payload 以供调试
        if (bodyString.length() > 1000) {
            plugin.getLogger().info("[AI Request] Payload (Partial): " + bodyString.substring(0, 1000) + "...");
        } else {
            plugin.getLogger().info("[AI Request] Payload: " + bodyString);
        }
        
        // 验证payload中没有null values
        if (bodyString.contains("\"content\":null")) {
            plugin.getLogger().severe("[AI Error] Payload contains null content! This will cause API error.");
            throw new IOException("Payload validation failed: null content detected");
        }

        RequestBody body = RequestBody.create(
                bodyString,
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + cfKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            plugin.getLogger().info("[AI Response] Code: " + response.code());

            if (!response.isSuccessful()) {
                plugin.getLogger().warning("[AI Error] Response Body: " + responseBody);
                throw new IOException("AI 调用失败: " + response.code() + " - " + responseBody);
            }

            JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);
            
            // 1. 处理新的 /ai/v1/responses (Responses API) 格式
            // 格式: { "output": [ { "type": "message", "content": [ { "type": "output_text", "text": "..." } ] } ] }
            if (responseJson.has("output") && responseJson.get("output").isJsonArray()) {
                JsonArray outputArray = responseJson.getAsJsonArray("output");
                for (int i = 0; i < outputArray.size(); i++) {
                    JsonObject item = outputArray.get(i).getAsJsonObject();
                    if (item.has("type") && "message".equals(item.get("type").getAsString())) {
                        if (item.has("content") && item.get("content").isJsonArray()) {
                            JsonArray contents = item.getAsJsonArray("content");
                            for (int j = 0; j < contents.size(); j++) {
                                JsonObject contentObj = contents.get(j).getAsJsonObject();
                                if (contentObj.has("type") && "output_text".equals(contentObj.get("type").getAsString())) {
                                    String text = contentObj.get("text").getAsString();
                                    if (text != null) {
                                        return text;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. 处理标准 /run 接口返回格式 (备选)
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("response")) {
                    String responseText = result.get("response").getAsString();
                    if (responseText != null) {
                        return responseText;
                    }
                }
            }

            // 备选格式处理 (某些模型可能返回不同的 key)
            if (responseJson.has("result")) {
                JsonObject result = responseJson.getAsJsonObject("result");
                if (result.has("text")) {
                    String text = result.get("text").getAsString();
                    if (text != null) {
                        return text;
                    }
                }
            }

            throw new IOException("无法解析 AI 响应结果: " + responseBody);
        }
    }
}
