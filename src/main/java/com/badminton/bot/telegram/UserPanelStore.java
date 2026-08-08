package com.badminton.bot.telegram;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Последнее «экранное» сообщение бота в личке пользователя — его редактируем вместо спама. */
@Component
public class UserPanelStore {

    private final Map<Long, Integer> messageByUser = new ConcurrentHashMap<>();

    public Integer get(Long userId) {
        return messageByUser.get(userId);
    }

    public void put(Long userId, Integer messageId) {
        if (userId != null && messageId != null) {
            messageByUser.put(userId, messageId);
        }
    }

    public void clear(Long userId) {
        if (userId != null) {
            messageByUser.remove(userId);
        }
    }
}
