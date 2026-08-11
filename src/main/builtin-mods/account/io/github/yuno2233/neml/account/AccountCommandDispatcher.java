package io.github.yuno2233.neml.account;

import com.github.yuno2233.neml.api.CommandProvider;
import com.github.yuno2233.neml.log.NemlLogger;
import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

public class AccountCommandDispatcher implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("account");
    private static final Path CONFIG_FILE = Paths.get("neml", "config", "account", "config.json");
    private static final Gson gson = new Gson();

    // Microsoft 认证常量
    private static final String DEFAULT_CLIENT_ID = "e9cadaf0-6e4d-4daa-bb6c-1e5807c55c60";
    private static final String CLIENT_ID = System.getenv("NEML_CLIENT_ID") != null ?
            System.getenv("NEML_CLIENT_ID") : DEFAULT_CLIENT_ID;
    private static final String DEVICE_AUTH_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            System.out.println("用法: neml account <list|add|switch|login|logout|status> [参数]");
            return;
        }

        switch (args[0]) {
            case "login3rd":
                if (args.length < 4) {
                    System.out.println("用法: neml account login3rd <服务器URL> <用户名> <密码>");
                } else {
                    addThirdPartyAccount(args[1], args[2], args[3]);
                }
                break;
            case "add":
                if (args.length < 2) {
                    System.out.println("用法: neml account add <用户名>");
                } else {
                    addAccount(args[1]);
                }
                break;
            case "list":
                listAccounts();
                break;
            case "switch":
                if (args.length < 2) {
                    System.out.println("用法: neml account switch <索引/用户名>");
                } else {
                    switchAccount(args[1]);
                }
                break;
            case "login":
                microsoftLogin();
                break;
            case "logout":
                microsoftLogout();
                break;
            case "status":
                showStatus();
                break;
            default:
                System.out.println("未知命令: " + args[0]);
        }
    }

    // ========== 配置管理 ==========
    private JsonObject loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            JsonObject def = new JsonObject();
            def.add("accounts", new JsonArray());
            def.addProperty("currentAccount", "");
            saveConfig(def);
            return def;
        }
        try {
            return gson.fromJson(Files.readString(CONFIG_FILE), JsonObject.class);
        } catch (Exception e) {
            log.warning("读取配置失败，使用默认");
            JsonObject def = new JsonObject();
            def.add("accounts", new JsonArray());
            def.addProperty("currentAccount", "");
            return def;
        }
    }

    private void saveConfig(JsonObject config) {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, gson.toJson(config));
        } catch (Exception e) {
            log.warning("保存配置失败: " + e.getMessage());
        }
    }

    // 获取当前选中的账号信息（供 launcher 等使用）
    public static JsonObject getCurrentAccount() {
        Path configFile = Paths.get("neml", "config", "account", "config.json");
        if (!Files.exists(configFile)) return null;
        try {
            JsonObject config = new Gson().fromJson(Files.readString(configFile), JsonObject.class);
            String current = config.has("currentAccount") ? config.get("currentAccount").getAsString() : "";
            if (current.isEmpty()) return null;
            JsonArray accounts = config.getAsJsonArray("accounts");
            for (JsonElement e : accounts) {
                JsonObject acc = e.getAsJsonObject();
                String type = acc.has("type") ? acc.get("type").getAsString() : "offline";
                String key;
                if (type.equals("microsoft")) {
                    key = acc.get("uuid").getAsString();
                } else if (type.equals("thirdparty")) {
                    key = acc.get("name").getAsString() + "@" + acc.get("server").getAsString();
                } else {
                    key = acc.get("name").getAsString();
                }
                if (key.equals(current)) {
                    return acc;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ========== 离线账号添加 ==========
    private void addAccount(String username) {
        JsonObject config = loadConfig();
        JsonArray accounts = config.getAsJsonArray("accounts");

        for (JsonElement e : accounts) {
            JsonObject acc = e.getAsJsonObject();
            if (acc.has("type") && acc.get("type").getAsString().equals("microsoft")) continue;
            if (acc.get("name").getAsString().equals(username)) {
                System.out.println("离线账号已存在: " + username);
                if (config.get("currentAccount").getAsString().isEmpty()) {
                    config.addProperty("currentAccount", username);
                    saveConfig(config);
                    System.out.println("已切换到该账号。");
                }
                return;
            }
        }

        JsonObject newAccount = new JsonObject();
        newAccount.addProperty("name", username);
        newAccount.addProperty("uuid", generateOfflineUUID(username));
        newAccount.addProperty("type", "offline");
        accounts.add(newAccount);

        if (config.get("currentAccount").getAsString().isEmpty()) {
            config.addProperty("currentAccount", username);
        }

        saveConfig(config);
        System.out.println("已添加离线账号: " + username);
        if (username.equals(config.get("currentAccount").getAsString())) {
            System.out.println("当前账号已切换为: " + username);
        }
    }
    
    private String generateOfflineUUID(String username) {
        String input = "OfflinePlayer:" + username;
        UUID uuid = UUID.nameUUIDFromBytes(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }
    
    // ---------- 第三方服务器 ----------
    private void addThirdPartyAccount(String serverUrl, String username, String password) {
        if (!serverUrl.startsWith("http")) {
            System.out.println("错误: 服务器 URL 必须以 http 开头");
            return;
        }

        // 尝试认证获取角色列表
        System.out.println("正在验证账号...");
        List<String[]> characters = null;
        try {
            characters = fetchCharacters(serverUrl, username, password);
        } catch (Exception e) {
            System.out.println("无法获取角色列表: " + e.getMessage());
            System.out.println("将添加为未选择角色的账号。");
        }

        String characterName = username; // 默认使用用户名
        String characterUuid = "0";
        if (characters != null && !characters.isEmpty()) {
            if (characters.size() == 1) {
                // 只有一个角色，自动选择
                String[] ch = characters.get(0);
                characterName = ch[0];
                characterUuid = ch[1];
                System.out.println("自动选择角色: " + characterName + " (" + characterUuid + ")");
            } else {
                // 多个角色，让用户选择
                System.out.println("检测到多个角色，请选择:");
                for (int i = 0; i < characters.size(); i++) {
                    String[] ch = characters.get(i);
                    System.out.printf("  [%d] %s (%s)\n", i, ch[0], ch[1]);
                }
                System.out.print("请输入序号 (0-" + (characters.size()-1) + "): ");
                try {
                    Scanner scanner = new Scanner(System.in);
                    int choice = Integer.parseInt(scanner.nextLine().trim());
                    if (choice >= 0 && choice < characters.size()) {
                        String[] ch = characters.get(choice);
                        characterName = ch[0];
                        characterUuid = ch[1];
                        System.out.println("已选择角色: " + characterName + " (" + characterUuid + ")");
                    } else {
                        System.out.println("无效选择，将使用用户名存储。");
                        characterName = username;
                        characterUuid = "0";
                    }
                } catch (NumberFormatException e) {
                    System.out.println("输入无效，将使用用户名存储。");
                }
            }
        }

        JsonObject config = loadConfig();
        JsonArray accounts = config.getAsJsonArray("accounts");

        // 检查是否已存在相同服务器、用户名和角色的账号
        // 检查是否已存在相同服务器、用户名和角色的账号
        for (JsonElement e : accounts) {
            JsonObject acc = e.getAsJsonObject();
            if (acc.has("type") && "thirdparty".equals(acc.get("type").getAsString())
                    && acc.has("server") && acc.get("server").getAsString().equals(serverUrl)
                    && acc.has("name") && acc.get("name").getAsString().equals(username)) {
                String existingCharUuid = acc.has("characterUuid") ? acc.get("characterUuid").getAsString() : "0";
                if (existingCharUuid.equals(characterUuid)) {
                    System.out.println("第三方账号已存在: " + username + " (" + serverUrl + ") 角色: " + characterName);
                    return;
                }
            }
        }

        JsonObject account = new JsonObject();
        account.addProperty("type", "thirdparty");
        account.addProperty("name", username);            // 原始用户名
        account.addProperty("server", serverUrl);
        account.addProperty("password", password);
        account.addProperty("characterName", characterName); // 选定角色名
        account.addProperty("characterUuid", characterUuid); // 选定角色UUID
        account.addProperty("uuid", "0");                 // 保持占位
        accounts.add(account);

        if (config.get("currentAccount").getAsString().isEmpty()) {
            // 标识符使用 原始用户名@服务器
            config.addProperty("currentAccount", username + "@" + serverUrl);
        }
        saveConfig(config);
        System.out.println("已添加第三方账号: " + username + " (" + serverUrl + ") 角色: " + characterName);
    }
    
    // ========== 账号列表 ==========
    private void listAccounts() {
        JsonObject config = loadConfig();
        JsonArray accounts = config.getAsJsonArray("accounts");
        String current = config.has("currentAccount") ? config.get("currentAccount").getAsString() : "";

        if (accounts.size() == 0) {
            System.out.println("没有已添加的账号，请使用 'neml account add <用户名>' 或 'neml account login'。");
            return;
        }

        System.out.println("已添加的账号:");
        for (int i = 0; i < accounts.size(); i++) {
            JsonObject acc = accounts.get(i).getAsJsonObject();
            String type = acc.has("type") ? acc.get("type").getAsString() : "offline";
            String name = acc.get("name").getAsString();
            String uuid = acc.has("uuid") ? acc.get("uuid").getAsString() : "-";

            // 计算标识符（用于判断“当前”标记）
            String identifier;
            if (type.equals("microsoft")) {
                identifier = uuid;
            } else if (type.equals("thirdparty")) {
                // 第三方账号的标识符为 用户名@服务器
                String server = acc.has("server") ? acc.get("server").getAsString() : "";
                identifier = name + "@" + server;
            } else {
                identifier = name;
            }
            String mark = identifier.equals(current) ? " * 当前" : "";

            // 准备显示的名称和额外信息
            String displayName = name;
            String extra = "";
            if (type.equals("thirdparty")) {
                // 显示角色名（如果有）
                String characterName = acc.has("characterName") ? acc.get("characterName").getAsString() : name;
                displayName = name + " [" + characterName + "]";
                String server = acc.has("server") ? acc.get("server").getAsString() : "";
                extra = " 服务器: " + server;
            }

            System.out.printf("  [%d] %-30s %-10s %s%s%s\n", i, displayName, type, uuid, mark, extra);
        }
    }

    private void switchAccount(String identifier) {
        JsonObject config = loadConfig();
        JsonArray accounts = config.getAsJsonArray("accounts");
        if (accounts.size() == 0) {
            System.out.println("没有可切换的账号，请先添加。");
            return;
        }

        // 尝试按索引
        try {
            int index = Integer.parseInt(identifier);
            if (index < 0 || index >= accounts.size()) {
                System.out.println("错误: 索引超出范围 (0-" + (accounts.size()-1) + ")");
                return;
            }
            JsonObject acc = accounts.get(index).getAsJsonObject();
            String type = acc.has("type") ? acc.get("type").getAsString() : "offline";
            String newCurrent;
            if (type.equals("microsoft")) {
                newCurrent = acc.get("uuid").getAsString();
            } else if (type.equals("thirdparty")) {
                newCurrent = acc.get("name").getAsString() + "@" + acc.get("server").getAsString();
            } else {
                newCurrent = acc.get("name").getAsString();
            }
            config.addProperty("currentAccount", newCurrent);
            saveConfig(config);
            System.out.println("已切换到账号: " + acc.get("name").getAsString());
            return;
        } catch (NumberFormatException ignored) {}

        // 按用户名、UUID 或 用户名@服务器 匹配
        for (JsonElement e : accounts) {
            JsonObject acc = e.getAsJsonObject();
            String name = acc.get("name").getAsString();
            String uuid = acc.has("uuid") ? acc.get("uuid").getAsString() : "";
            String type = acc.has("type") ? acc.get("type").getAsString() : "offline";
            boolean matched = false;
            String newCurrent = "";

            // 匹配用户名或 UUID
            if (name.equals(identifier) || uuid.equals(identifier)) {
                matched = true;
            } else if (type.equals("thirdparty")) {
                // 匹配 用户名@服务器
                String server = acc.get("server").getAsString();
                if ((name + "@" + server).equals(identifier)) {
                    matched = true;
                }
            }

            if (matched) {
                if (type.equals("microsoft")) {
                    newCurrent = uuid;
                } else if (type.equals("thirdparty")) {
                    newCurrent = name + "@" + acc.get("server").getAsString();
                } else {
                    newCurrent = name;
                }
                config.addProperty("currentAccount", newCurrent);
                saveConfig(config);
                System.out.println("已切换到账号: " + name);
                return;
            }
        }
        System.out.println("错误: 找不到账号 " + identifier);
    }

    // ========== 微软登录 ==========
    private void microsoftLogin() {
        try {
            // 1. 请求设备代码
            System.out.println("正在请求设备代码...");
            Map<String, String> deviceParams = new HashMap<>();
            deviceParams.put("client_id", CLIENT_ID);
            deviceParams.put("scope", "XboxLive.signin offline_access");
            JsonObject deviceResp = postForm(DEVICE_AUTH_URL, deviceParams);

            if (!deviceResp.has("user_code")) {
                System.out.println("设备代码请求失败，服务器返回: " + deviceResp.toString());
                return;
            }

            String userCode = deviceResp.get("user_code").getAsString();
            String deviceCode = deviceResp.get("device_code").getAsString();
            String message = deviceResp.get("message").getAsString();
            int interval = deviceResp.get("interval").getAsInt();
            int expiresIn = deviceResp.get("expires_in").getAsInt();

            System.out.println("\n" + message);
            System.out.println("打开以下链接并输入代码: " + userCode);
            System.out.println("等待授权...");

            // 2. 轮询令牌
            Map<String, String> tokenParams = new HashMap<>();
            tokenParams.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            tokenParams.put("client_id", CLIENT_ID);
            tokenParams.put("device_code", deviceCode);
            JsonObject tokenResp = null;

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < expiresIn * 1000L) {
                Thread.sleep(interval * 1000L);
                try {
                    tokenResp = postForm(TOKEN_URL, tokenParams);
                    if (tokenResp.has("access_token")) break;
                    // 输出等待信息，但不要刷屏
                } catch (Exception e) {
                    // 继续等待，可能网络错误
                }
            }

            if (tokenResp == null || !tokenResp.has("access_token")) {
                System.out.println("登录超时或失败");
                if (tokenResp != null) System.out.println("最后响应: " + tokenResp);
                return;
            }

            String msAccessToken = tokenResp.get("access_token").getAsString();
            String refreshToken = tokenResp.has("refresh_token") ? tokenResp.get("refresh_token").getAsString() : "";

            // 3. Xbox Live 认证
            System.out.println("正在验证 Xbox Live...");
            JsonObject xblReq = new JsonObject();
            xblReq.add("Properties", new JsonObject());
            xblReq.getAsJsonObject("Properties").addProperty("AuthMethod", "RPS");
            xblReq.getAsJsonObject("Properties").addProperty("SiteName", "user.auth.xboxlive.com");
            xblReq.getAsJsonObject("Properties").addProperty("RpsTicket", "d=" + msAccessToken);
            JsonObject xblResp = postJson(XBOX_AUTH_URL, xblReq);
            String xblToken = xblResp.get("Token").getAsString();
            String uhs = xblResp.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                    .get(0).getAsJsonObject().get("uhs").getAsString();

            // 4. XSTS
            System.out.println("正在获取 XSTS token...");
            JsonObject xstsReq = new JsonObject();
            xstsReq.add("Properties", new JsonObject());
            xstsReq.getAsJsonObject("Properties").addProperty("SandboxId", "RETAIL");
            JsonArray tokens = new JsonArray();
            tokens.add(xblToken);
            xstsReq.getAsJsonObject("Properties").add("UserTokens", tokens);
            JsonObject xstsResp = postJson(XSTS_AUTH_URL, xstsReq);
            String xstsToken = xstsResp.get("Token").getAsString();

            // 5. Minecraft 认证
            System.out.println("正在获取 Minecraft 令牌...");
            JsonObject mcAuthReq = new JsonObject();
            mcAuthReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
            JsonObject mcAuthResp = postJson(MC_AUTH_URL, mcAuthReq);
            String mcAccessToken = mcAuthResp.get("access_token").getAsString();

            // 6. 获取玩家信息
            System.out.println("正在获取玩家信息...");
            JsonObject profileResp = getJson(MC_PROFILE_URL, mcAccessToken);
            String uuid = profileResp.get("id").getAsString();
            String name = profileResp.get("name").getAsString();

            // 7. 存储账号
            JsonObject account = new JsonObject();
            account.addProperty("type", "microsoft");
            account.addProperty("uuid", uuid);
            account.addProperty("name", name);
            account.addProperty("accessToken", mcAccessToken);
            account.addProperty("refreshToken", refreshToken);
            account.addProperty("xuid", uhs);
            account.addProperty("addedTime", System.currentTimeMillis());

            JsonObject config = loadConfig();
            JsonArray accounts = config.getAsJsonArray("accounts");
            for (int i = accounts.size() - 1; i >= 0; i--) {
                JsonObject a = accounts.get(i).getAsJsonObject();
                if (a.has("type") && a.get("type").getAsString().equals("microsoft") &&
                        a.get("uuid").getAsString().equals(uuid)) {
                    accounts.remove(i);
                }
            }
            accounts.add(account);
            config.add("accounts", accounts);
            config.addProperty("currentAccount", uuid);
            saveConfig(config);

            System.out.println("登录成功! 欢迎，" + name);
            System.out.println("UUID: " + uuid);

        } catch (Exception e) {
            System.out.println("登录失败: " + e.getMessage());
            log.severe("登录异常: " + e.getMessage());
        }
    }

    private void microsoftLogout() {
        JsonObject config = loadConfig();
        String current = config.has("currentAccount") ? config.get("currentAccount").getAsString() : "";
        if (current.isEmpty()) {
            System.out.println("当前没有选中的账号");
            return;
        }
        JsonArray accounts = config.getAsJsonArray("accounts");
        for (int i = 0; i < accounts.size(); i++) {
            JsonObject acc = accounts.get(i).getAsJsonObject();
            String id = acc.has("type") && acc.get("type").getAsString().equals("microsoft") ?
                    acc.get("uuid").getAsString() : acc.get("name").getAsString();
            if (id.equals(current)) {
                if (acc.has("type") && acc.get("type").getAsString().equals("microsoft")) {
                    accounts.remove(i);
                    System.out.println("已移除正版账号: " + acc.get("name").getAsString());
                } else {
                    System.out.println("离线账号无法注销，请使用 'account switch' 切换。");
                    return;
                }
                break;
            }
        }
        config.addProperty("currentAccount", "");
        saveConfig(config);
    }

    private void showStatus() {
        JsonObject account = getCurrentAccount();
        if (account == null) {
            System.out.println("当前未选择任何账号");
            return;
        }
        String type = account.has("type") ? account.get("type").getAsString() : "offline";
        String name = account.get("name").getAsString();
        String uuid = account.has("uuid") ? account.get("uuid").getAsString() : "-";
        System.out.println("当前账号: " + name);
        System.out.println("类型: " + type);
        System.out.println("UUID: " + uuid);
        if (type.equals("microsoft")) {
            long added = account.get("addedTime").getAsLong();
            System.out.println("登录时间: " + new Date(added));
        }
    }

    // ========== HTTP 工具 ==========
    private JsonObject postForm(String urlStr, Map<String, String> params) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        byte[] postData = sb.toString().getBytes("UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData);
        }

        InputStream is;
        try {
            is = conn.getInputStream();
        } catch (IOException e) {
            is = conn.getErrorStream();
        }
        if (is == null) throw new IOException("无响应流");
        try (Reader reader = new InputStreamReader(is)) {
            return gson.fromJson(reader, JsonObject.class);
        }
    }

    private JsonObject postJson(String urlStr, JsonObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes());
        }

        try (InputStream is = conn.getInputStream()) {
            return gson.fromJson(new InputStreamReader(is), JsonObject.class);
        }
    }

    private JsonObject getJson(String urlStr, String bearerToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try (InputStream is = conn.getInputStream()) {
            return gson.fromJson(new InputStreamReader(is), JsonObject.class);
        }
    }
    
    /**
     * 调用第三方验证服务器的 /authserver/authenticate 接口，返回角色列表。
     * 每个角色为一个 String[]{角色名, UUID}。
     */
    private List<String[]> fetchCharacters(String serverUrl, String username, String password) throws Exception {
        String authUrl = serverUrl.endsWith("/") ? serverUrl + "authserver/authenticate" : serverUrl + "/authserver/authenticate";
        JsonObject req = new JsonObject();
        // 添加 agent 字段（标准 Yggdrasil 认证要求）
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        req.add("agent", agent);
        req.addProperty("username", username);
        req.addProperty("password", password);
        req.addProperty("requestUser", true); // 请求返回角色信息

        JsonObject resp = postJson(authUrl, req);

        List<String[]> characters = new ArrayList<>();
        if (resp.has("availableProfiles")) {
            JsonArray profiles = resp.getAsJsonArray("availableProfiles");
            for (JsonElement e : profiles) {
                JsonObject profile = e.getAsJsonObject();
                String name = profile.get("name").getAsString();
                String id = profile.get("id").getAsString();
                characters.add(new String[]{name, id});
            }
        } else if (resp.has("selectedProfile")) {
            JsonObject profile = resp.getAsJsonObject("selectedProfile");
            if (profile != null && profile.has("name")) {
                characters.add(new String[]{profile.get("name").getAsString(), profile.get("id").getAsString()});
            }
        }
        return characters;
    }
}