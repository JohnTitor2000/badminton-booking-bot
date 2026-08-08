package com.badminton.bot.telegram;

/**
 * Коды callback_data. Админские действия с датой: {@code code|yyyyMMdd}.
 */
public enum CallbackAction {
    START("st"),
    DURATION("du"),
    SLOT("sl"),
    CONFIRM("cf"),
    BACK_DURATION("bd"),
    BACK_SLOT("bs"),
    CANCEL("cn"),
    /** Начать изменение своей записи: args = bookingId */
    CHANGE("ch"),
    /** Сохранить запись как пресет: args = bookingId */
    SAVE_PRESET("sp"),
    /** Записаться по пресету: args = eventId */
    USE_PRESET("up"),
    /** Выбрать время вручную: args = eventId */
    MANUAL("mn"),
    /** Удалить пресет: args = eventId (чтобы вернуться к выбору) */
    CLEAR_PRESET("cp"),
    /** Экран выбора пресет/вручную (редактирование сообщения мастера): args = eventId */
    ENTRY("en"),
    ADMIN_PUBLISH("ap"),
    ADMIN_CANCEL("ac"),
    ADMIN_CLOSE("ax"),
    ADMIN_TABLE("at"),
    ADMIN_BOOKINGS("ab"),
    ADMIN_EXPORT("ae"),
    NOOP("no");

    private final String code;

    CallbackAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static CallbackAction fromCode(String code) {
        for (CallbackAction action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Неизвестное действие callback: " + code);
    }
}
