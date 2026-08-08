package com.badminton.bot.telegram;

/**
 * Компактная кодировка состояния мастера записи в {@code callback_data} (лимит Telegram — 64 байта).
 * Формат: {@code action|arg1|arg2|...}
 */
public record CallbackData(CallbackAction action, String[] args) {

    public static String build(CallbackAction action, Object... args) {
        StringBuilder sb = new StringBuilder(action.code());
        for (Object arg : args) {
            sb.append('|').append(arg);
        }
        return sb.toString();
    }

    public static CallbackData parse(String raw) {
        String[] parts = raw.split("\\|");
        CallbackAction action = CallbackAction.fromCode(parts[0]);
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return new CallbackData(action, args);
    }

    public long argLong(int index) {
        return Long.parseLong(args[index]);
    }

    public int argInt(int index) {
        return Integer.parseInt(args[index]);
    }
}
