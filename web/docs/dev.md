# 开发 Mod
请详细阅读

---

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

---

## NEML 内置 Mod 创建规范

### 1. 概述
NEML 引擎本身不提供任何 Minecraft 启动功能，所有能力均由 **Mod** 提供。  
引擎将 Mod 分为两类：
- **内置 Mod**：随引擎 Jar 一起发布，存放在 `META-INF/neml/builtin/` 下，源码位于 `src/main/builtin-mods/<modid>/`。
- **外部 Mod**：用户自行放入 `./neml/mod/` 目录的独立 Jar 文件。

内置 Mod 与引擎共享同一个 ClassLoader（系统类加载器），可以互相访问类，但必须通过依赖声明来控制**加载顺序**和**版本校验**。

---

### 2. 内置 Mod 目录结构
在项目源码中，内置 Mod 必须放在：  
`src/main/builtin-mods/<modid>/`

一个完整的内置 Mod 示例（`core`）：
```
src/main/builtin-mods/
└── core/                              ← 以 mod id 为文件夹名
    ├── neml-mod.json                  ← 元数据
    └── io/github/yuno2233/neml/core/  ← 源码包路径
        ├── CoreMainInit.java          ← main 入口实现（可选）
        └── CoreCommandDispatcher.java ← command 入口实现（至少一个）
```

- `neml-mod.json` 必须直接放在 mod 根目录。
- Java 源码遵循正常包结构，由 `build-helper-maven-plugin` 添加到编译源集。
- 构建后，Maven 资源插件会将整个 `core/` 目录复制到引擎 Jar 的 `META-INF/neml/builtin/core/` 下（包括 json 和所有 `.class` 文件，因为编译产物会输出到 `target/classes/META-INF/neml/builtin/...`，但我们的资源复制是针对 `src/main/builtin-mods` 中的非 Java 文件，而编译后的 class 已由插件处理）。

**注意**：由于内置 Mod 的 Java 源码被当作引擎的一部分编译，它们的 `.class` 文件会出现在 `target/classes` 中，同时 `neml-mod.json` 等资源则复制到 `META-INF/neml/builtin/core/`。引擎在运行时扫描 `META-INF/neml/builtin/*/neml-mod.json` 来发现 Mod，无需关心 `.class` 位置，因为类加载器可以直接从类路径找到这些类。

---

### 3. neml-mod.json 格式
```json
{
  "id": "mod-unique-id",
  "version": "1.0.0",
  "mainClass": "可选的旧版兼容字段，可省略",
  "depends": {
    "core": ">=1.0.0",
    "other-mod": ">=2.0.0 <3.0.0"
  },
  "entrypoints": {
    "main": [
      "com.example.MyModInitializer"
    ],
    "command": [
      "com.example.MyCommandProvider"
    ]
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | true | Mod 唯一标识，建议使用小写字母和连字符，例如 `minecraft-installer` |
| `version` | string | true | 语义化版本号，如 `1.0.0`，用于依赖匹配 |
| `mainClass` | string | false | 保留字段，可不填；如果填写也不会被使用 |
| `depends` | object | false | 键为依赖的 mod id，值为版本范围表达式（见下） |
| `entrypoints` | object | true | 必须包含 `main` 和/或 `command` 数组 |
| `commands` | object | true | 对外命令的描述，键为命令名，值为描述文本 |
| `entrypoints.main` | string[] | false | 实现 `ModInitializer` 接口的类全限定名列表，在加载时按依赖顺序执行 |
| `entrypoints.command` | string[] | false | 实现 `CommandProvider` 接口的类全限定名列表，当用户执行 `neml <modid>` 时依次调用 |

**版本范围表达式**：  
用空格分隔多个条件，支持：
- `>=1.0.0`  大于等于
- `<=2.0.0`  小于等于
- `>1.0.0`   大于
- `<2.0.0`   小于
- `1.0.0`    精确匹配

示例：`">=1.0.0 <2.0.0"` 表示 1.x 系列。

---

### 4. 入口点接口

所有接口位于 `com.github.yuno2233.neml.api` 包中，内置 Mod 可直接依赖（引擎已提供）。

#### ModInitializer
```java
public interface ModInitializer {
    void onInitialize();
}
```
在引擎启动、依赖解析完成后，按拓扑顺序调用所有需要加载的 Mod 的 `main` 入口。  
用于执行一次性初始化，如注册服务、设置数据库连接等。

#### CommandProvider
```java
public interface CommandProvider {
    void execute(String[] args);
}
```
当用户在 CLI 执行 `neml <modid> [args...]` 时，引擎会加载该 mod 及其依赖，然后实例化所有 `command` 类并依次调用 `execute(args)`（`args` 不包含 mod id 本身）。  
如果 mod 声明了多个 command 类，它们会被全部调用，因此通常**只注册一个 command 类作为分发器**。

---

### 5. 引擎加载流程

1. 用户执行 `neml <targetModId> [args]`
2. 引擎扫描内置和外部 mod，收集所有 `ModCandidate`
3. 根据 `targetModId` 递归解析依赖树，检查版本，若有缺失或冲突立即终止
4. 对所有需要的 mod 按拓扑排序
5. 构建类加载器（内置 mod 共享系统类加载器，外部 mod 创建独立 ClassLoader 并设置父子关系）
6. 依次调用各 mod 的 `main` 入口
7. 调用目标 mod 的 `command` 入口，传入 `[args]`
8. 命令执行完毕后程序退出

---

### 6. 内置 Mod 间依赖关系

- **同一 classpath**：所有内置 Mod 的类互相可见，引擎不做隔离。因此你可以直接 `new` 另一个 Mod 的类。
- **顺序保证**：通过 `depends` 声明的依赖，引擎保证在 `main` 初始化阶段，被依赖的 Mod 先于当前 Mod 初始化。但 `command` 执行时，所有依赖的 Mod 已初始化完毕。
- **版本校验**：`depends` 中的版本范围会在加载时强制检查，不符合则报错退出。
- **传递依赖**：引擎自动解析所有传递依赖，并全部纳入加载列表。

**注意**：虽然类可以互相访问，但仍建议通过依赖声明来让引擎管理初始化顺序，避免隐性依赖导致难以排查的问题。

---

### 7. 完整示例

#### 示例 1：最简单的 echo 命令 Mod
创建一个可以回显参数的 Mod，id 为 `echoer`。

**目录结构**：
```
src/main/builtin-mods/
└── echoer/
    ├── neml-mod.json
    └── io/github/yuno2233/neml/echoer/
        └── EchoCommand.java
```

**neml-mod.json**：
```json
{
  "id": "echoer",
  "version": "1.0.0",
  "entrypoints": {
    "command": ["io.github.yuno2233.neml.echoer.EchoCommand"]
  }
}
```

**EchoCommand.java**：
```java
package io.github.yuno2233.neml.echoer;

import com.github.yuno2233.neml.api.CommandProvider;

public class EchoCommand implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            System.out.println("请输入要回显的内容");
        } else {
            System.out.println("回显: " + String.join(" ", args));
        }
    }
}
```

**使用**：  
`java -jar neml.jar echoer Hello World`  
输出：`回显: Hello World`

---

#### 示例 2：依赖 core mod 的计算器 Mod
假设我们要写一个 `calculator` 命令，它依赖 `core` mod（需要在 core 初始化之后才能正常工作，例如 core 提供了一些数学工具类）。

**目录结构**：
```
src/main/builtin-mods/
└── calculator/
    ├── neml-mod.json
    └── io/github/yuno2233/neml/calculator/
        ├── CalcMainInit.java
        └── CalcCommand.java
```

**neml-mod.json**：
```json
{
  "id": "calculator",
  "version": "1.0.0",
  "depends": {
    "core": ">=1.0.0"
  },
  "entrypoints": {
    "main": ["io.github.yuno2233.neml.calculator.CalcMainInit"],
    "command": ["io.github.yuno2233.neml.calculator.CalcCommand"]
  }
}
```

**CalcMainInit.java**（初始化示例）：
```java
package mysql;
import com.github.yuno2233.neml.api.ModInitializer;

public class CalcMainInit implements ModInitializer {
    @Override
    public void onInitialize() {
        // 假设 core 提供了一些计算辅助类，可以在这里进行验证或预加载
        System.out.println("[calculator] 初始化完成");
    }
}
```

**CalcCommand.java**：
```java
package io.github.yuno2233.neml.calculator;

import com.github.yuno2233.neml.api.CommandProvider;

public class CalcCommand implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length < 3) {
            System.out.println("用法: neml calculator <数字> <操作符> <数字>");
            return;
        }
        try {
            double a = Double.parseDouble(args[0]);
            double b = Double.parseDouble(args[2]);
            String op = args[1];
            double result;
            switch (op) {
                case "+": result = a + b; break;
                case "-": result = a - b; break;
                case "*": result = a * b; break;
                case "/": result = a / b; break;
                default:
                    System.out.println("不支持的操作符: " + op);
                    return;
            }
            System.out.printf("%.2f %s %.2f = %.2f%n", a, op, b, result);
        } catch (NumberFormatException e) {
            System.out.println("无效的数字");
        }
    }
}
```

**使用**：  
`java -jar neml.jar calculator 3 + 5`  
输出：`3.00 + 5.00 = 8.00`

---

### 8. 注意事项

- **id 唯一性**：全引擎范围内（内置+外部）不允许有重复 id，否则后者会覆盖前者，可能导致加载错误。
- **不重复初始化**：每个 mod 的 `main` 入口在单次引擎运行中只初始化一次，即使被多个 mod 依赖也不会重复调用。
- **command 调用规则**：引擎只会调用用户指定的目标 mod 的 `command` 入口，其依赖的 mod 不会被调用 command，除非用户在命令中指定它们。
- **版本号语义化**：遵循 `主版本.次版本.修订号` 格式，比较时按数值逐段进行。
- **文件命名**：`neml-mod.json` 必须严格大小写正确。
- **内置 Mod 编译**：你只需按规范放置源码和 json，重新执行 `mvn clean package` 即可自动集成，无需额外配置。

通过以上规范，你可以轻松创建各种功能的内置 Mod，逐步构建一个完整的 CLI 启动器。

---