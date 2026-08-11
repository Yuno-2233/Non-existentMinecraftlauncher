# 内置 Mod 命令
引擎预置名为 `core` `installer` `java` `versions` `account` `launcher` 的内置 Mod，提供基础管理命令：

| 命令 | 说明 |
|------|------|
| `core list` | 列出所有已发现的 Mod（内置与外部） |
| `core reload` | 重新扫描 Mod 目录 |
| `core help` | 显示 core mod 的帮助信息 |
|  |  |
| `installer install <版本ID>` | 安装指定 Minecraft 版本（下载所有文件） |
| ... | ... |

示例：
```
java -jar neml.jar core list
java -jar neml.jar core reload
java -jar neml.jar core help
```
其余内置mod的指令可通过 `core list` 命令查看