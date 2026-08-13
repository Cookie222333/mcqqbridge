# MCQQBridge

**Minecraft ↔ QQ 官方机器人消息互通**

让 Minecraft 游戏内的聊天与 QQ 群聊 / 单聊（私聊）消息互相转发，支持从 QQ 远程查询服务器状态、执行自定义 MC 命令，并转发玩家进出 / 死亡等状态通知。

## 📦 支持版本

本项目提供两种服务端支持：

| 版本 | 说明 | 目录 |
|------|------|------|
| **Fabric 模组** | Minecraft 26.2 + Fabric Loader 0.19.3+ | 仓库根目录 |
| **Paper 插件** | Minecraft 26.2 + Paper 26.2 服务端 | [`paper/`](paper/) |

- **Fabric 版**：`build/libs/mcqqbridge-1.0.0.jar`（放入 `mods/`）
- **Paper 版**：[`paper/mcqqbridge-paper-1.0.0.jar`](paper/mcqqbridge-paper-1.0.0.jar)（放入 `plugins/`）

> QQ 官方机器人（QQ 开放平台）接入：通过 OAuth 获取 Access Token，使用 WebSocket Gateway 长连接接收消息事件，使用 REST API 发送消息。

---

## ✨ 功能特性

| 方向 | 说明 |
|------|------|
| **MC → QQ** | 游戏内玩家聊天转发到指定 QQ 群（可配置格式） |
| **MC → QQ** | 玩家进服 / 退服通知（`xxx 加入了游戏` / `xxx 离开了游戏`） |
| **MC → QQ** | 玩家死亡通知（含死亡原因，来自服务端死亡消息） |
| **MC → QQ** | （可选）游戏系统消息、`/say` `/me` 命令广播 |
| **QQ → MC** | QQ 群 @机器人 / 群消息 / 私聊消息转发到游戏内广播 |
| **QQ 远程指令** | 在 QQ 群发送 `/mc list`、`/mc seed` 等，服务端执行命令并返回结果 |
| **自定义指令** | 通过配置 `qq_commands` 自定义任意 MC 命令 |
| **游戏内指令** | `/mcqq status`、`/mcqq reload`、`/mcqq help` |
| **自动重连** | Access Token 自动刷新；Gateway 断线自动退避重连（支持 Resume） |
| **消息去重** | 被动回复携带 `msg_id` + `msg_seq`，符合官方频控规则 |

---

## 📋 环境要求

- Windows / Linux / macOS 均可（以下以 Windows 为例）
- **JDK 25**（下载：[Eclipse Temurin JDK 25](https://adoptium.net/)）
- Minecraft **26.2**（Java 版）
- [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3+
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.157.0+26.2

---

## 🚀 快速开始

### 1. QQ 开放平台准备

1. 前往 [QQ 开放平台](https://q.qq.com/) 注册并创建 **QQ 机器人**。
2. 在「开发设置」中记录 **AppID** 与 **AppSecret**。
3. 将机器人添加到你的 QQ 群；按需开启机器人「群聊消息」「单聊消息」等权限。
4. 在「事件订阅」中订阅群聊 / 单聊消息事件（本项目使用 `GROUP_AND_C2C_EVENT` Intent，自动订阅 `GROUP_AT_MESSAGE_CREATE`、`GROUP_MESSAGE_CREATE`、`C2C_MESSAGE_CREATE`）。

> 💡 **提示**：若需要在 QQ 中显示**群内昵称**（群名片）而非 QQ 账户名，需在开放平台开通「读取群成员信息」权限；未开通时自动显示 QQ 账户名。

### 2. 构建模组

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot
gradlew.bat build
```

构建产物位于 `build/libs/`（使用 `mcqqbridge-1.0.0.jar`，注意带 `-sources` 的是源码包，请勿放入 mods）。

### 3. 安装

1. 将 `mcqqbridge-*.jar`、`fabric-api-*.jar` 放入游戏目录的 `mods/` 文件夹。
2. 启动游戏（单机世界 / 专用服务器均可）。
3. 首次运行会在 `config/` 目录生成 `mcqqbridge.json`，编辑它填写 QQ 凭证（见下文配置示例）。

4. 在游戏内执行 `/mcqq reload` 应用配置。

> **如何获取 group_openid？**
> 机器人加入群后，可通过官方接口 `GET /v2/groups` 查询机器人所在群列表，或在 QQ 开放平台机器人管理后台的「群管理」中查看群 OpenID。
> 若 `target_groups` 留空，MC → QQ 方向不会发送（QQ → MC 方向不受影响）。

---

## ⚙️ 完整配置示例

```json
{
  "enabled": true,
  "app_id": "你的 AppID",
  "client_secret": "你的 AppSecret",

  "qq_to_mc": {
    "enabled": true,
    "groups": [],
    "accept_group_at": true,
    "accept_group_message": true,
    "accept_private_message": true,
    "format": "{sender}: {message}"
  },

  "mc_to_qq": {
    "enabled": true,
    "target_groups": ["目标群的 group_openid"],
    "forward_chat": true,
    "forward_game_message": false,
    "forward_join_leave": true,
    "forward_death": true,
    "forward_command_message": false,
    "format": "[MC] {player}: {message}"
  },

  "mc_broadcast_prefix": "[QQ]",
  "qq_command_prefix": "/mc",

  "qq_commands": [
    { "command": "list", "mc_command": "/list", "description": "查看 MC 在线玩家列表", "timeout_ms": 3000 },
    { "command": "seed", "mc_command": "/seed", "description": "查看世界种子", "timeout_ms": 3000 }
  ]
}
```

---

## 📖 配置项说明

### 顶层
| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | bool | 总开关 |
| `app_id` | string | QQ 机器人 AppID |
| `client_secret` | string | QQ 机器人 AppSecret |
| `mc_broadcast_prefix` | string | 游戏内消息前缀（默认 `[QQ]`） |
| `qq_command_prefix` | string | QQ 端指令前缀（默认 `/mc`） |

### `qq_to_mc`（QQ → MC）
| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | bool | 允许 QQ → MC 转发 |
| `groups` | string[] | 允许转发的群 OpenID，空 = 不限制 |
| `accept_group_at` | bool | 接收群内 @机器人 消息 |
| `accept_group_message` | bool | 接收群内普通消息（需相应权限） |
| `accept_private_message` | bool | 接收单聊消息 |
| `format` | string | 游戏内显示格式，支持 `{sender}` `{message}` |

### `mc_to_qq`（MC → QQ）
| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | bool | 允许 MC → QQ 转发 |
| `target_groups` | string[] | 转发到的目标群 OpenID |
| `forward_chat` | bool | 转发玩家聊天 |
| `forward_game_message` | bool | 转发游戏系统消息（进服/退服/死亡/成就等） |
| `forward_join_leave` | bool | 转发玩家进出游戏通知 |
| `forward_death` | bool | 转发玩家死亡通知（含死亡原因） |
| `forward_command_message` | bool | 转发 `/say` `/me` |
| `format` | string | QQ 里显示格式，支持 `{player}` `{message}` |

### `qq_commands`（QQ 端自定义指令）
| 字段 | 类型 | 说明 |
|------|------|------|
| `command` | string | QQ 里发送的触发词（发送 `/mc ` + 该词） |
| `mc_command` | string | 要在 MC 服务端执行的命令（如 `/list`） |
| `description` | string | 帮助说明（显示在 `/mc help`） |
| `timeout_ms` | int | 命令执行超时（毫秒，默认 3000） |

> ⚠️ 注意：`mc_command` 必须是服务端**实际存在**的命令。原版 Minecraft 没有 `/tps`（那是 Paper/Spigot 才有），请勿配置不存在的命令。

---

## 🎮 常用指令

### 游戏内（管理员）
| 指令 | 作用 |
|------|------|
| `/mcqq status` | 查看互通状态 |
| `/mcqq reload` | 重新加载配置 |
| `/mcqq help` | 查看帮助 |

### QQ 群
| 指令 | 作用 |
|------|------|
| `/mc list` | 查看 MC 在线玩家列表 |
| `/mc seed` | 查看世界种子 |
| `/mc online` | 查看 MC 在线玩家（内置格式） |
| `/mc status` | 查看互通状态 |
| `/mc help` | 查看所有 QQ 端指令 |

---

## 🧱 技术实现

```
src/main/java/com/mcqq/bridge/
├── MCQQBridge.java         主类（入口、服务器生命周期）
├── config/
│   └── ModConfig.java      配置读写（Gson）
├── qq/
│   ├── QQMessage.java      消息模型
│   ├── QQApi.java          REST：Access Token 刷新 / 发消息 / 查昵称
│   ├── QQGateway.java      WebSocket Gateway：Identify/Heartbeat/Resume/重连
│   └── QQBot.java          协调器：事件分发 / 昵称解析 / QQ 端指令
└── mc/
    ├── ChatBridge.java     MC↔QQ 双向桥接（聊天 / 进出 / 死亡 / 系统消息）
    ├── CommandCapture.java 捕获服务端命令输出
    └── ModCommands.java    /mcqq 命令
```

- **聊天监听**：`net.fabricmc.fabric.api.message.v1.ServerMessageEvents`
- **进出 / 死亡**：`net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents`、`ServerLivingEntityEvents`
- **WebSocket**：`org.java-websocket:Java-WebSocket:1.5.6`（自带独立线程，不依赖 JDK 的 `ForkJoinPool.commonPool`——避免被 Minecraft 服务器任务占满导致 READY 饿死）
- **HTTP / JSON**：`java.net.http.HttpClient` + Gson

---

## 🔒 安全说明

- 你的 **AppID / AppSecret / group_openid** 属于敏感信息，请勿提交到公开仓库。
- 本项目仓库通过 `.gitignore` 排除了 `config/mcqqbridge.json` 等真实配置文件，仅保留 `*.example.json`（占位符）供参考。
- 若你 fork / clone 本仓库，请勿在配置文件中填入真实密钥后提交。

---

## 📄 许可证

[MIT](LICENSE)

---

## ⚠️ 免责声明

- 本模组调用的是 QQ 官方开放平台机器人接口，请遵守 [QQ 机器人运营规范](https://bot.q.qq.com/wiki/business/)。
- 消息发送受官方频控限制（被动回复：群聊 5 分钟 5 次；主动消息：群 60/QPS 等）。
- 请勿将 AppSecret 泄漏给他人。
