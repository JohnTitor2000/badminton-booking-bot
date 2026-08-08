package com.badminton.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String botToken,
        String botUsername,
        String adminIds,
        Long channelId,
        Long discussionChatId
) {

    public List<Long> adminIdList() {
        if (adminIds == null || adminIds.isBlank()) {
            return List.of();
        }
        return List.of(adminIds.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    public boolean isAdmin(Long userId) {
        return userId != null && adminIdList().contains(userId);
    }
}
