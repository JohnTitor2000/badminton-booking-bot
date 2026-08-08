package com.badminton.bot.util;

import org.telegram.telegrambots.meta.api.objects.User;

public class UserNames {

    private UserNames() {
    }

    public static String displayName(User user) {
        StringBuilder sb = new StringBuilder();
        if (user.getFirstName() != null) {
            sb.append(user.getFirstName());
        }
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(user.getLastName());
        }
        if (sb.length() == 0 && user.getUserName() != null) {
            sb.append('@').append(user.getUserName());
        }
        if (sb.length() == 0) {
            sb.append("Игрок ").append(user.getId());
        }
        return sb.toString();
    }

    public static String mention(String displayName, Long userId, String username) {
        if (username != null && !username.isBlank()) {
            return "@" + username;
        }
        return "<a href=\"tg://user?id=" + userId + "\">" + displayName + "</a>";
    }
}
