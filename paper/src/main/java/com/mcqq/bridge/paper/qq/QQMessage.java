package com.mcqq.bridge.qq;

/**
 * 从 QQ 官方机器人网关事件中解析出的消息模型。
 */
public final class QQMessage {
	public enum Type {
		/** QQ 群聊消息（GROUP_AT_MESSAGE_CREATE / GROUP_MESSAGE_CREATE）。 */
		GROUP,
		/** QQ 单聊（私聊）消息（C2C_MESSAGE_CREATE）。 */
		PRIVATE
	}

	/** 消息类型。 */
	public final Type type;

	/** 消息 ID，可用于被动回复（msg_id）。 */
	public final String id;

	/** 群 OpenID（群聊时非空）。 */
	public final String groupOpenId;

	/** 用户/成员 OpenID（单聊时是 user_openid，群聊时是 member_openid）。 */
	public final String userOpenId;

	/** 发送者显示名（昵称或 openid 截断）。 */
	public final String senderName;

	/** 清洗后的文本内容。 */
	public final String content;

	/** 消息时间戳（epoch 秒）。 */
	public final long timestamp;

	public QQMessage(Type type, String id, String groupOpenId, String userOpenId,
			String senderName, String content, long timestamp) {
		this.type = type;
		this.id = id;
		this.groupOpenId = groupOpenId;
		this.userOpenId = userOpenId;
		this.senderName = senderName;
		this.content = content;
		this.timestamp = timestamp;
	}
}
