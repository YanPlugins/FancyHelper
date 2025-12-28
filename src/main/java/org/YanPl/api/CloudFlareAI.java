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
    private static final String API_BASE_URL = "https://api.cloudflare.com/client/v4/accounts/%s/ai/run/%s";
    private final MineAgent plugin;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public CloudFlareAI(MineAgent plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 发送对话请求
     */
    public String chat(DialogueSession session, String systemPrompt) throws IOException {
        String accountId = plugin.getConfigManager().getCloudflareAccountId();
        String model = plugin.getConfigManager().getCloudflareModel();
        String apiToken = plugin.getConfigManager().getCloudflareApiToken();

        if (accountId.isEmpty() || apiToken.isEmpty()) {
            return "错误: 请先在配置文件中设置 CloudFlare Account ID 和 API Token。";
        }

        String url = String.format(API_BASE_URL, accountId, model);

        JsonObject bodyJson = new JsonObject();
        JsonArray messagesArray = new JsonArray();

        // 添加系统提示词
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messagesArray.add(systemMsg);

        // 添加历史记录
        for (DialogueSession.Message msg : session.getHistory()) {
            JsonObject m = new JsonObject();
            m.addProperty("role", msg.getRole());
            m.addProperty("content", msg.getContent());
            messagesArray.add(m);
        }

        bodyJson.add("messages", messagesArray);

        RequestBody body = RequestBody.create(
                gson.toJson(bodyJson),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiToken)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API 请求失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonObject resultJson = gson.fromJson(responseBody, JsonObject.class);
            
            if (resultJson.has("result")) {
                return resultJson.getAsJsonObject("result").get("response").getAsString();
            } else {
                throw new IOException("API 返回结果异常: " + responseBody);
            }
        }
    }
}
