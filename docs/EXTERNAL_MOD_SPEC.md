## NEML 外部 Mod 开发规范

本规范指导开发者创建 NEML 引擎的外部 Mod，使启动器获得额外功能。

### 1. 前置条件
- 安装 Java 11 或更高版本
- 获取 NEML 引擎 API jar（从引擎项目编译获得，包含 `com.github.yuno2233.neml.api` 包）

### 2. Mod 目录结构
一个 Mod 需打包为一个 jar 文件，内部结构如下：
```
example-mod.jar
├── neml-mod.json          # 元数据
├── io/github/yuno2233/example/
│   ├── ExampleMain.java   # 主入口类（编译后为 .class）
│   └── ExampleCommand.java
└── ...其他类或资源
```

### 3. 元数据文件 `neml-mod.json`
放置在 jar 根目录，采用 JSON 格式，字段如下：

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `id` | true | string | Mod 的唯一标识，建议小写字母+连字符 |
| `version` | true | string | 语义化版本号，如 `1.0.0` |
| `mainClass` | false | string | 主类全限定名，若未定义入口点则必须实现 `ModInitializer` |
| `depends` | false | object | 依赖关系，键为 mod id，值为版本范围表达式 |
| `entrypoints` | true | object | 入口点声明，包含 `main` 和 `command` 列表 |
| `commands` | true | object | 对外命令的描述，键为命令名，值为描述文本 |

示例：
```json
{
  "id": "hello-mod",
  "version": "1.0.0",
  "mainClass": "com.example.HelloMain",
  "depends": {
    "core": ">=1.0.0"
  },
  "entrypoints": {
    "main": ["com.example.HelloMain"],
    "command": ["com.example.HelloCommand"]
  },
  "commands": {
    "hello": "输出问候信息",
    "version": "显示 Mod 版本"
  }
}
```

### 4. 入口点接口
引擎调用 Mod 时通过 `entrypoints` 中声明的类，需实现对应接口。

#### 4.1 `main` 入口
实现 `com.github.yuno2233.neml.api.ModInitializer` 接口，定义初始化逻辑：
```java
public interface ModInitializer {
    void onInitialize();
}
```
该方法在 Mod 被加载时调用，可进行资源初始化、注册监听等。

#### 4.2 `command` 入口
实现 `com.github.yuno2233.neml.api.CommandProvider` 接口，处理用户命令：
```java
public interface CommandProvider {
    void execute(String[] args);
}
```
当用户执行 `neml <modId> <command> [args...]` 时，引擎会加载目标 Mod 及其依赖，然后调用 `command` 入口类的 `execute(args)` 方法，args 不包含 modId 和子命令名（若使用分发模式需自行解析，见下文）。

### 5. 命令分发模式
通常 Mod 提供多个命令，推荐使用**单一 `command` 入口 + 内部分发**，以避免引擎调用多个入口类造成混乱。在 `execute` 方法中根据参数分发：
```java
public void execute(String[] args) {
    if (args.length == 0) { /* 帮助信息 */ }
    switch (args[0]) {
        case "hello": ... break;
        case "version": ... break;
    }
}
```
并在 `neml-mod.json` 中仅声明一个 `command` 入口。

### 6. 依赖管理
- 在 `depends` 中声明所需的其他 Mod 及其版本范围。
- 引擎会递归解析传递依赖，并按拓扑排序加载。
- 版本范围支持 `>=1.0.0`、`<2.0.0`、`1.0.0` 等，空格分隔多个条件（“与”关系）。
- 依赖缺失或版本不满足时，引擎立即报错退出，不会加载目标 Mod。
- 如果依赖的 Mod 提供了类，你的 Mod 可以直接使用其公共类（由类加载器层次实现可见性）。

### 7. 使用 NEML 日志
引擎提供统一的日志系统，Mod 内可获取带前缀的 Logger：
```java
import com.github.yuno2233.neml.log.NemlLogger;
import java.util.logging.Logger;

Logger log = NemlLogger.getModLogger("你的ModId");
log.info("这是一条日志");
```
日志会自动添加 `[modId]` 前缀，同时写入控制台和 `neml/logs/` 下的文件。  
支持级别：`TRACE`、`DEBUG`、`INFO`、`WARN`、`ERROR`（通过引擎环境变量 `NEML_LOG_LEVEL` 设置）。

### 8. 访问引擎 API
Mod 可通过 `ModLoader.getCurrentInstance()` 获取引擎实例，进而访问已加载的 Mod 列表、元数据等：
```java
ModLoader loader = ModLoader.getCurrentInstance();
Map<String, ModCandidate> mods = loader.getCandidateMap();
```
（此 API 可能会在后续版本中扩展为更稳定的公开接口）

### 9. 打包与安装
1. 编译你的 Mod 类，确保依赖引擎 API（及依赖的其他 Mod）在 classpath 中。
2. 将 `neml-mod.json` 放置在输出目录根。
3. 打包为 jar 文件：
   ```bash
   jar cf my-mod.jar * 
   ```
4. 将 jar 文件放入 NEML 运行目录的 `./neml/mod/` 下。
5. 运行 `neml core reload` 或重启引擎，新 Mod 即可被发现。

### 10. 开发建议
- 尽量保持 Mod 轻量，遵守单一职责。
- 充分利用依赖机制复用其他 Mod 的功能（例如一个负责下载的 Mod 可被其他 Mod 依赖）。
- 如果 Mod 需要访问网络，注意处理异常并给出友好提示。
- 命名约定：Mod id 小写字母+连字符；Java 包名使用 `io.github.yuno2233.neml.modname`。

---

**示例项目**：参见引擎源码 `src/main/builtin-mods/core`，以及外部示例 Mod `example-mod.jar` 的构建过程。遵循以上规范，你即可为 NEML 启动器扩展任意功能。