# MCQQBridge (Paper)

**Minecraft Paper ↔ QQ 官方机器人消息互通插件**

基于 [MCQQBridge](https://github.com/Cookie222333/mcqqbridge)（Fabric 版）的 **Paper 服务端版本**。Paper 是 Bukkit/Spigot 系的高性能服务端，本插件实现游戏内聊天与 QQ 群聊/单聊消息互相转发。

- 目标版本：**Minecraft 26.2** + **Paper 26.2**
- 服务端：Paper / 兼容的 Bukkit 系服务端
- 语言：**Java 25**（JDK 25+）

---

## ✨ 功能

| 方向 | 说明 |
|------|------|
| **MC → QQ** | 玩家聊天转发到指定 QQ 群 |
| **MC → QQ** | 玩家进服/退服通知、死亡通知（含死亡原因） |
| **QQ → MC** | QQ 群/私聊消息转发到游戏内广播 |
| **QQ 远程指令** | 在 QQ 群发送 `/mc list` 等，查询在线玩家、执行命令 |
| **游戏内指令** | `/mcqq status`、`/mcqq reload`、`/mcqq help` |
| **自动重连** | Access Token 自动刷新；Gateway 断线自动重连 |

---

## 📦 安装

1. 准备 **Paper 26.2** 服务器（下载 [paper-26.2-*.jar](https://papermc.io/downloads/paper)，放入服务器目录，`java -jar paper-26.2-*.jar` 运行）
2. 将 `mcqqbridge-paper-1.0.0.jar` 放入服务器的 `plugins/` 文件夹
3. 启动服务器，插件会自动在 `plugins/MCQQBridge/mcqqbridge.json` 生成配置
4. 编辑配置填写 QQ 凭证后，服务器内执行 `/mcqq reload` 生效

---

## ⚙️ 配置

配置文件位于 `plugins/MCQQBridge/mcqqbridge.json`（与 Fabric 版配置格式一致）：

```json
{
  "enabled": true,
  "app_id": "你的 QQ 机器人 AppID",
  "client_secret": "你的 QQ 机器人 AppSecret",
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
    "forward_join_leave": true,
    "forward_death": true,
    "format": "[MC] {player}: {message}"
  },
  "mc_broadcast_prefix": "[QQ]",
  "qq_command_prefix": "/mc",
  "qq_commands": [
    { "command": "list", "mc_command": "/list", "description": "查看 MC 在线玩家", "timeout_ms": 3000 }
  ]
}
```

## 🎮 指令

| 指令 | 位置 | 作用 |
|------|------|------|
| `/mcqq status` | 游戏内 | 查看互通状态 |
| `/mcqq reload` | 游戏内 | 重新加载配置（需要 op） |
| `/mcqq help` | 游戏内 | 查看帮助 |
| `/mc list` | QQ 群 | 查看 MC 在线玩家 |

---

## 🛠 构建

```bat
gradlew.bat build
```

产物位于 `build/libs/mcqqbridge-paper-1.0.0.jar`（含 Java-WebSocket 依赖，开箱即用）。

## ⚠️ 说明

- QQ 机器人需在 [QQ 开放平台](https://q.qq.com) 创建并获取 AppID / AppSecret
- 群内昵称显示需机器人开通「读取群成员信息」权限，否则显示 QQ 账户名
- 请遵守 QQ 机器人运营规范
