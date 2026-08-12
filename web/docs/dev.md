# Mod 开发规范与教程

NEML 启动器引擎基于 Mod 架构，所有功能均由 Mod 提供。本指南将教你如何开发自己的 Mod，并将其集成到引擎中。

## 目录
1. [Mod 类型概述](#mod-类型概述)
2. [Mod 元数据文件 (neml-mod.json)](#mod-元数据文件-neml-modjson)
3. [入口点与接口](#入口点与接口)
4. [命令定义与帮助系统](#命令定义与帮助系统)
5. [依赖管理](#依赖管理)
6. [日志与配置](#日志与配置)
7. [示例：创建一个简单的模组](#示例创建一个简单的模组)
8. [内置 Mod 开发](#内置-mod-开发)
9. [外置 Mod 开发与安装](#外置-mod-开发与安装)
10. [最佳实践](#最佳实践)

---

## Mod 类型概述

- **内置 Mod**：源代码位于 `src/main/builtin-mods/` 目录下，随引擎编译到 JAR 中，引擎启动时自动发现。
- **外置 Mod**：打包为 JAR 文件，放置在运行目录的 `neml/mod/` 下，引擎启动时从该目录扫描加载。

两者的开发方式基本一致，区别仅在于部署位置和编译依赖。

---

## Mod 元数据文件 (neml-mod.json)

每个 Mod 必须在其 JAR 根目录（或源码对应目录）包含一个 `neml-mod.json` 文件，用于描述 Mod 的基本信息、依赖、入口点和命令。

### 完整字段说明

```json
{
  "id": "my-mod",
  "version": "1.0.0",
  "description": "我的第一个 Mod（可选，用于帮助系统）",
  "mainClass": "com.example.MyMod",
  "depends": {
    "core": ">=1.0.0"
  },
  "entrypoints": {
    "main": ["com.example.MyMod"],
    "command": ["com.example.MyCommand"]
  },
  "commands": {
    "hello": "输出问候语",
    "greet": {
      "description": "自定义问候",
      "usage": "neml my-mod greet <name>",
      "args": [
        {"name": "name", "description": "你的名字", "optional": false}
      ]
    }
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | Mod 唯一标识，只能包含小写字母、数字、连字符 |
| `version` | string | 是 | 语义化版本号 |
| `description` | string | 否 | 简短描述，用于 `core help` 顶层显示 |
| `mainClass` | string | 否 | 早期版本保留字段，目前可省略 |
| `depends` | object | 否 | 依赖的其他 Mod ID 及版本范围（如 `">=1.0.0 <2.0.0"`） |
| `entrypoints` | object | 是 | 定义入口类 |
| `entrypoints.main` | array | 是 | 实现 `ModInitializer` 接口的类列表，在 Mod 加载时调用 |
| `entrypoints.command` | array | 否 | 实现 `CommandProvider` 接口的类列表，处理用户命令 |
| `commands` | object | 否 | 注册的命令，键为命令名，值为描述字符串或详细帮助对象 |

### 命令帮助对象

- `description`：命令简介
- `usage`：用法示例
- `args`：参数列表，每项包含 `name`（参数名）、`description`（说明）、`optional`（是否可选）

---

## 入口点与接口

引擎会按依赖顺序加载 Mod，并依次调用 `main` 入口点的 `onInitialize()` 方法。当用户执行 `neml <modid> <命令>` 时，引擎会调用对应 Mod 的 `command` 入口点的 `execute(String[] args)` 方法。

### ModInitializer 接口

```java
public interface ModInitializer {
    void onInitialize();
}
```

实现该接口的类会收到初始化回调。例如：

```java
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("MyMod 已加载！");
    }
}
```

### CommandProvider 接口

```java
public interface CommandProvider {
    void execute(String[] args);
}
```

实现该接口的类负责处理用户命令。`args` 是用户输入的子命令及参数。

---

## 命令定义与帮助系统

在 `neml-mod.json` 的 `commands` 字段中注册命令后，引擎会自动将它们加入帮助系统。用户可通过 `core help <modid>` 查看命令列表，通过 `core help <modid> <命令>` 查看详细用法（如果提供了帮助对象）。

**示例**：注册了两个命令，其中 `greet` 提供了详细帮助。

---

## 依赖管理

Mod 可以通过 `depends` 字段声明对其他 Mod 的依赖。引擎会按拓扑顺序加载，并隔离类加载器。

- 依赖版本范围支持 `>=`, `<=`, `>`, `<` 以及精确版本。
- 如果依赖缺失或版本不匹配，引擎会报错并退出。

**示例**：`"core": ">=1.0.0"` 表示需要核心 Mod 1.0.0 及以上版本。

---

## 日志与配置

引擎提供了统一的日志系统，推荐使用以下方式获取日志实例：

```java
import com.github.yuno2233.neml.log.NemlLogger;
import java.util.logging.Logger;

public class MyCommand implements CommandProvider {
    private static final Logger log = NemlLogger.getModLogger("my-mod");

    @Override
    public void execute(String[] args) {
        log.info("你好，世界！");
    }
}
```

日志会自动添加 `[my-mod]` 前缀，并遵循全局日志配置（级别、颜色、文件输出）。

Mod 的专有配置应存放在 `neml/config/<modid>/` 目录下，使用 JSON 文件存储，引擎不限制格式。

---

## 示例：创建一个简单的模组

我们将创建一个外置 Mod，提供 `hello` 命令，输出问候语。

### 1. 创建项目结构

```
my-mod/
  src/
    com/example/MyMod.java
    com/example/MyCommand.java
  neml-mod.json
```

### 2. 编写 `MyMod.java`

```java
package com.example;

import com.github.yuno2233.neml.api.ModInitializer;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("外置 MyMod 初始化完成");
    }
}
```

### 3. 编写 `MyCommand.java`

```java
package com.example;

import com.github.yuno2233.neml.api.CommandProvider;

public class MyCommand implements CommandProvider {
    @Override
    public void execute(String[] args) {
        if (args.length > 0 && args[0].equals("hello")) {
            System.out.println("你好，NEML 用户！");
        } else {
            System.out.println("未知命令，请使用 'hello'");
        }
    }
}
```

### 4. 编写 `neml-mod.json`

```json
{
  "id": "my-mod",
  "version": "1.0.0",
  "description": "一个简单的示例 Mod",
  "depends": {},
  "entrypoints": {
    "main": ["com.example.MyMod"],
    "command": ["com.example.MyCommand"]
  },
  "commands": {
    "hello": "输出问候语"
  }
}
```

### 5. 编译并打包

编译时需要将 `neml.jar` 添加到 classpath 中，以便访问 API 接口。

```bash
javac -cp neml.jar -d out src/com/example/*.java
cp neml-mod.json out/
cd out && jar cf my-mod.jar . && cd ..
```

### 6. 安装

将 `my-mod.jar` 复制到 NEML 工作目录下的 `neml/mod/` 目录中。

### 7. 使用

```bash
java -jar neml.jar my-mod hello
```

将会输出 `你好，NEML 用户！`。

---

## 内置 Mod 开发

内置 Mod 的源码放在 `src/main/builtin-mods/<modid>/` 目录下，目录内需包含 `neml-mod.json` 和 Java 源代码。编译时，Maven 插件会自动将这些目录添加到源码路径，并将 `neml-mod.json` 复制到 classpath 的 `META-INF/neml/builtin/<modid>/` 下。

**与外部 Mod 的区别**：
- 不需要手动打包成 JAR，直接作为源码编译。
- 引擎扫描内置 Mod 时会读取 `META-INF/neml/builtin/` 下的配置文件。
- 可以直接使用引擎内部的工具类（但建议仅依赖公开 API）。

**示例**：参考项目中已有的 `core`、`versions` 等内置 Mod。

---

## 外置 Mod 开发与安装

开发外置 Mod 时，只需依赖 `neml.jar` 中的 API 接口（`ModInitializer`、`CommandProvider`），然后打包为 JAR。将最终 JAR 放入运行目录的 `neml/mod/` 下，引擎会自动发现并加载。

**注意**：
- 外置 Mod 的类加载器是独立的，不能直接访问其他 Mod 的类，除非声明依赖。
- 可通过在 `depends` 中声明需要的 Mod ID，引擎会保证加载顺序并建立类加载器关联。

---

## 最佳实践

1. **唯一 ID**：确保 Mod ID 不与内置 Mod 或其他流行 Mod 冲突，建议使用小写字母和连字符。
2. **版本管理**：遵循语义化版本，依赖声明使用明确的版本范围。
3. **日志规范**：使用 `NemlLogger.getModLogger("id")` 获取日志器，避免直接 `System.out.println`。
4. **帮助文档**：为命令提供 `usage` 和 `args` 说明，提升用户体验。
5. **配置隔离**：将自己的配置放在 `neml/config/<modid>/` 下，不要污染全局配置目录。
6. **依赖最小化**：尽量只依赖 `core`，减少对其他 Mod 的强耦合。

---

现在你可以开始开发自己的 Mod 了！如果需要更多帮助，请参考项目源码中的内置 Mod 示例。