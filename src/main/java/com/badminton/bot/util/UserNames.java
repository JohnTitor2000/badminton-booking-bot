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

    /** HTML-ссылка на профиль: имя кликабельно в канале/личке. */
    public static String mention(String displayName, Long userId, String username) {
        String label = escapeHtml(displayName == null || displayName.isBlank() ? "Игрок" : displayName);
        String href;
        if (username != null && !username.isBlank()) {
            href = "https://t.me/" + username.replace("@", "");
        } else if (userId != null) {
            href = "tg://user?id=" + userId;
        } else {
            return label;
        }
        return "<a href=\"" + href + "\">" + label + "</a>";
    }

    public static String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
