package com.badminton.bot.telegram;

import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.SlotCalculator;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class KeyboardFactory {

    private KeyboardFactory() {
    }

    public static ReplyKeyboardMarkup mainMenu(boolean admin) {
        ReplyKeyboardMarkup.ReplyKeyboardMarkupBuilder<?, ?> builder = ReplyKeyboardMarkup.builder()
                .resizeKeyboard(true)
                .isPersistent(true)
                .keyboardRow(new KeyboardRow(MenuLabels.MY_BOOKINGS, MenuLabels.HOW_TO));
        if (admin) {
            builder.keyboardRow(new KeyboardRow(MenuLabels.ADMIN_PUBLISH, MenuLabels.ADMIN_EVENTS))
                    .keyboardRow(new KeyboardRow(MenuLabels.ADMIN_CANCEL, MenuLabels.ADMIN_CLOSE))
                    .keyboardRow(new KeyboardRow(MenuLabels.ADMIN_TABLE, MenuLabels.ADMIN_BOOKINGS, MenuLabels.ADMIN_EXPORT));
        }
        return builder.build();
    }

    /**
     * Кнопка в канале: deep-link сразу открывает чат с ботом ({@code /start book_<eventId>}).
     * Если username бота не задан — fallback на callback.
     */
    public static InlineKeyboardMarkup entryKeyboard(Long eventId, String botUsername) {
        InlineKeyboardButton btn;
        if (botUsername != null && !botUsername.isBlank()) {
            String user = botUsername.startsWith("@") ? botUsername.substring(1) : botUsername;
            btn = InlineKeyboardButton.builder()
                    .text("🏸 Записаться")
                    .url("https://t.me/" + user + "?start=book_" + eventId)
                    .build();
        } else {
            btn = button("🏸 Записаться", CallbackData.build(CallbackAction.START, eventId));
        }
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(btn))
                .build();
    }

    public static InlineKeyboardMarkup durationKeyboard(Long eventId, List<Integer> durationOptions, SlotCalculator calc,
                                                        Long replaceBookingId) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        List<InlineKeyboardButton> row = new java.util.ArrayList<>();
        for (Integer duration : durationOptions) {
            row.add(button(calc.formatDuration(duration),
                    cb(CallbackAction.DURATION, replaceBookingId, eventId, duration)));
            if (row.size() == 3) {
                builder.keyboardRow(new InlineKeyboardRow(row));
                row = new java.util.ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            builder.keyboardRow(new InlineKeyboardRow(row));
        }
        return builder.build();
    }

    public static InlineKeyboardMarkup slotKeyboard(Long eventId, int durationMinutes, List<SlotOption> options,
                                                    Long replaceBookingId) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        for (SlotOption option : options) {
            builder.keyboardRow(new InlineKeyboardRow(button(option.label(),
                    cb(CallbackAction.SLOT, replaceBookingId, eventId, durationMinutes, option.startSlot()))));
        }
        builder.keyboardRow(new InlineKeyboardRow(button("◀️ Назад",
                cb(CallbackAction.BACK_DURATION, replaceBookingId, eventId))));
        return builder.build();
    }

    public static InlineKeyboardMarkup sizeKeyboard(Long eventId, int durationMinutes, int startSlot, int maxSize,
                                                    Long replaceBookingId) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        List<InlineKeyboardButton> row = new java.util.ArrayList<>();
        for (int size = 1; size <= maxSize; size++) {
            row.add(button(String.valueOf(size),
                    cb(CallbackAction.CONFIRM, replaceBookingId, eventId, durationMinutes, startSlot, size)));
            if (row.size() == 4) {
                builder.keyboardRow(new InlineKeyboardRow(row));
                row = new java.util.ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            builder.keyboardRow(new InlineKeyboardRow(row));
        }
        builder.keyboardRow(new InlineKeyboardRow(button("◀️ Назад",
                cb(CallbackAction.BACK_SLOT, replaceBookingId, eventId, durationMinutes))));
        return builder.build();
    }

    public static InlineKeyboardMarkup bookingActionsKeyboard(Long bookingId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("✏️ Изменить", CallbackData.build(CallbackAction.CHANGE, bookingId)),
                        button("❌ Отменить", CallbackData.build(CallbackAction.CANCEL, bookingId))))
                .build();
    }

    /** @deprecated используйте {@link #bookingActionsKeyboard} */
    public static InlineKeyboardMarkup cancelKeyboard(Long bookingId) {
        return bookingActionsKeyboard(bookingId);
    }

    public static InlineKeyboardMarkup myBookingsKeyboard(List<BookingButton> bookings, SlotCalculator calc) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        for (BookingButton item : bookings) {
            String when = item.event().getEventDate().format(fmt) + " "
                    + calc.formatSlotRange(item.booking().getStartSlot(), item.booking().getDurationMinutes());
            builder.keyboardRow(new InlineKeyboardRow(
                    button("✏️ " + when, CallbackData.build(CallbackAction.CHANGE, item.booking().getId())),
                    button("❌", CallbackData.build(CallbackAction.CANCEL, item.booking().getId()))));
        }
        return builder.build();
    }

    private static String cb(CallbackAction action, Long replaceBookingId, Object... args) {
        if (replaceBookingId == null) {
            return CallbackData.build(action, args);
        }
        Object[] withId = new Object[args.length + 1];
        System.arraycopy(args, 0, withId, 0, args.length);
        withId[args.length] = replaceBookingId;
        return CallbackData.build(action, withId);
    }

    public static InlineKeyboardMarkup adminDatesKeyboard(CallbackAction action, List<LocalDate> dates, String emoji) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");
        Locale ru = new Locale("ru");
        for (LocalDate date : dates) {
            String day = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ru);
            String label = emoji + " " + capitalize(day) + " " + date.format(fmt);
            String compact = date.format(DateTimeFormatter.BASIC_ISO_DATE);
            builder.keyboardRow(new InlineKeyboardRow(button(label, CallbackData.build(action, compact))));
        }
        return builder.build();
    }

    /** @deprecated используйте {@link #adminDatesKeyboard} */
    public static InlineKeyboardMarkup publishDatesKeyboard(List<LocalDate> dates) {
        return adminDatesKeyboard(CallbackAction.ADMIN_PUBLISH, dates, "📢");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    public record SlotOption(int startSlot, String label) {
    }

    public record BookingButton(Event event, Booking booking) {
    }
}
