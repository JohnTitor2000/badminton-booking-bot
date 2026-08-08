package com.badminton.bot.telegram;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.config.TelegramProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.BookingOutcome;
import com.badminton.bot.service.BookingResult;
import com.badminton.bot.service.BookingService;
import com.badminton.bot.service.EventService;
import com.badminton.bot.service.SlotCalculator;
import com.badminton.bot.util.UserNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Обрабатывает все шаги мастера записи (длительность → слот → количество человек)
 * и отмену записи. Каждый шаг после {@code START} редактирует одно и то же
 * служебное сообщение мастера, а не общую таблицу события.
 */
@Slf4j
@Component
public class BookingWizardHandler {

    private static final Set<CallbackAction> ADMIN_DATE_ACTIONS = EnumSet.of(
            CallbackAction.ADMIN_PUBLISH,
            CallbackAction.ADMIN_CANCEL,
            CallbackAction.ADMIN_CLOSE,
            CallbackAction.ADMIN_TABLE,
            CallbackAction.ADMIN_BOOKINGS,
            CallbackAction.ADMIN_EXPORT);

    private final EventService eventService;
    private final BookingService bookingService;
    private final SlotCalculator slotCalculator;
    private final BadmintonProperties badmintonProperties;
    private final TelegramProperties telegramProperties;
    private final TelegramSender sender;
    private final CommandDispatcher commandDispatcher;

    public BookingWizardHandler(EventService eventService,
                                 BookingService bookingService,
                                 SlotCalculator slotCalculator,
                                 BadmintonProperties badmintonProperties,
                                 TelegramProperties telegramProperties,
                                 TelegramSender sender,
                                 CommandDispatcher commandDispatcher) {
        this.eventService = eventService;
        this.bookingService = bookingService;
        this.slotCalculator = slotCalculator;
        this.badmintonProperties = badmintonProperties;
        this.telegramProperties = telegramProperties;
        this.sender = sender;
        this.commandDispatcher = commandDispatcher;
    }

    public void handle(CallbackQuery callbackQuery) {
        CallbackData data = CallbackData.parse(callbackQuery.getData());
        try {
            if (ADMIN_DATE_ACTIONS.contains(data.action())) {
                handleAdminDateAction(callbackQuery, data);
                return;
            }
            switch (data.action()) {
                case START -> handleStart(callbackQuery, data);
                case DURATION -> handleDuration(callbackQuery, data);
                case SLOT -> handleSlot(callbackQuery, data);
                case CONFIRM -> handleConfirm(callbackQuery, data);
                case BACK_DURATION -> handleBackToDuration(callbackQuery, data);
                case BACK_SLOT -> handleBackToSlot(callbackQuery, data);
                case CANCEL -> handleCancel(callbackQuery, data);
                case NOOP -> sender.answerCallback(callbackQuery.getId(), null);
                default -> sender.answerCallback(callbackQuery.getId(), null);
            }
        } catch (Exception e) {
            log.error("Ошибка обработки callback {}: {}", callbackQuery.getData(), e.getMessage(), e);
            sender.answerCallback(callbackQuery.getId(), "Произошла ошибка, попробуйте ещё раз");
        }
    }

    private void handleStart(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта", true);
            return;
        }
        Event event = eventOpt.get();
        Long userId = cq.getFrom().getId();

        String text = "🏸 Записываемся на " + eventDateLabel(event) + "\n\nВыберите длительность:";
        InlineKeyboardMarkup keyboard = KeyboardFactory.durationKeyboard(
                eventId, slotCalculator.durationOptionsMinutes(), slotCalculator);

        boolean sent = sender.sendPrivate(userId, text, keyboard);
        if (!sent) {
            String botMention = telegramProperties.botUsername() == null || telegramProperties.botUsername().isBlank()
                    ? "боту"
                    : "@" + telegramProperties.botUsername();
            sender.answerCallback(cq.getId(),
                    "Чтобы записаться, сначала напишите " + botMention + " в личку команду /start",
                    true);
            return;
        }
        sender.answerCallback(cq.getId(), "Продолжите запись в личке с ботом");
    }

    private void handleDuration(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        renderSlotMenu(cq, eventOpt.get(), duration);
    }

    private void handleSlot(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        int startSlot = data.argInt(2);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта");
            return;
        }
        sender.answerCallback(cq.getId(), null);

        String text = "🏸 " + eventDateLabel(eventOpt.get()) + "\nВремя: " + slotCalculator.formatSlotRange(startSlot, duration)
                + "\n\nСколько человек (включая вас)?";
        InlineKeyboardMarkup keyboard = KeyboardFactory.sizeKeyboard(eventId, duration, startSlot, badmintonProperties.slotCapacity());
        editWizardMessage(cq, text, keyboard);
    }

    private void handleConfirm(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        int startSlot = data.argInt(2);
        int partySize = data.argInt(3);

        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        Event event = eventOpt.get();
        User from = cq.getFrom();
        String displayName = UserNames.displayName(from);

        BookingResult result = bookingService.book(eventId, from.getId(), displayName, from.getUserName(),
                startSlot, duration, partySize);

        String slotLabel = slotCalculator.formatSlotRange(startSlot, duration);
        String text;
        InlineKeyboardMarkup keyboard = null;

        switch (result.outcome()) {
            case CONFIRMED -> {
                text = "✅ Записал(а) вас на " + slotLabel + " (" + partySize + " чел.)\n" + eventDateLabel(event);
                keyboard = KeyboardFactory.cancelKeyboard(result.booking().getId());
                sender.answerCallback(cq.getId(), "Подтверждено!");
            }
            case WAITLISTED -> {
                text = "⏳ Свободных мест уже нет, вы в листе ожидания на " + slotLabel + " (" + partySize + " чел.)\n"
                        + eventDateLabel(event) + "\nМы напишем, если освободится место.";
                keyboard = KeyboardFactory.cancelKeyboard(result.booking().getId());
                sender.answerCallback(cq.getId(), "В листе ожидания");
            }
            case OVERLAPS_OWN_BOOKING -> {
                text = "⚠️ У вас уже есть запись на пересекающееся время в этом событии.";
                sender.answerCallback(cq.getId(), "У вас уже есть запись на это время");
            }
            case PARTY_TOO_BIG -> {
                text = "⚠️ Слишком большая компания — максимум " + badmintonProperties.slotCapacity() + " человек на слот.";
                sender.answerCallback(cq.getId(), "Слишком большая компания");
            }
            case EVENT_NOT_OPEN -> {
                text = "🚫 Запись на это событие уже закрыта.";
                sender.answerCallback(cq.getId(), "Запись закрыта");
            }
            default -> {
                text = "Не удалось обработать запись, попробуйте ещё раз.";
                sender.answerCallback(cq.getId(), null);
            }
        }

        editWizardMessage(cq, text, keyboard);

        if (result.outcome() == BookingOutcome.CONFIRMED || result.outcome() == BookingOutcome.WAITLISTED) {
            eventService.refreshBookingMessage(eventId);
        }
    }

    private void handleBackToDuration(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        String text = "🏸 Записываемся на " + eventDateLabel(eventOpt.get()) + "\n\nВыберите длительность:";
        InlineKeyboardMarkup keyboard = KeyboardFactory.durationKeyboard(eventId, slotCalculator.durationOptionsMinutes(), slotCalculator);
        editWizardMessage(cq, text, keyboard);
    }

    private void handleBackToSlot(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        renderSlotMenu(cq, eventOpt.get(), duration);
    }

    private void handleCancel(CallbackQuery cq, CallbackData data) {
        long bookingId = data.argLong(0);
        Long requesterId = cq.getFrom().getId();
        boolean isAdmin = telegramProperties.isAdmin(requesterId);

        Optional<Booking> bookingOpt = bookingService.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Запись не найдена");
            return;
        }
        Long eventId = bookingOpt.get().getEvent().getId();

        List<Booking> promoted;
        try {
            promoted = bookingService.cancel(bookingId, requesterId, isAdmin);
        } catch (SecurityException e) {
            sender.answerCallback(cq.getId(), "Это не ваша запись");
            return;
        }

        sender.answerCallback(cq.getId(), "Запись отменена");
        editWizardMessage(cq, "❌ Запись отменена.", null);
        eventService.refreshBookingMessage(eventId);

        notifyPromoted(promoted);
    }

    private void handleAdminDateAction(CallbackQuery cq, CallbackData data) {
        Long userId = cq.getFrom().getId();
        if (!telegramProperties.isAdmin(userId)) {
            sender.answerCallback(cq.getId(), "Только для администратора", true);
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(data.args()[0], DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            sender.answerCallback(cq.getId(), "Некорректная дата", true);
            return;
        }
        sender.answerCallback(cq.getId(), "Ок");
        editWizardMessage(cq, "⏳ Выполняю…", null);
        commandDispatcher.executeAdminDateAction(cq.getMessage().getChatId(), data.action(), date);
        editWizardMessage(cq, "✅ Готово для " + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), null);
    }

    private void notifyPromoted(List<Booking> promoted) {
        for (Booking booking : promoted) {
            String text = "✅ Для вас нашлось место! Ваша запись на "
                    + slotCalculator.formatSlotRange(booking.getStartSlot(), booking.getDurationMinutes())
                    + " теперь подтверждена.";
            InlineKeyboardMarkup keyboard = KeyboardFactory.cancelKeyboard(booking.getId());
            sender.sendPrivate(booking.getTelegramUserId(), text, keyboard);
        }
    }

    private void renderSlotMenu(CallbackQuery cq, Event event, int duration) {
        List<Booking> active = bookingService.activeBookings(event.getId());
        int[] remaining = slotCalculator.remainingCapacityPerSlot(active);

        List<KeyboardFactory.SlotOption> options = new ArrayList<>();
        for (int start : slotCalculator.possibleStartSlots(duration)) {
            int minRemaining = slotCalculator.minRemainingForRange(remaining, start, duration);
            String label = slotCalculator.formatSlotRange(start, duration)
                    + (minRemaining > 0 ? " (" + minRemaining + " своб.)" : " (лист ожидания)");
            options.add(new KeyboardFactory.SlotOption(start, label));
        }

        String text = "🏸 " + eventDateLabel(event) + "\nДлительность: " + slotCalculator.formatDuration(duration)
                + "\n\nВыберите время начала:";
        InlineKeyboardMarkup keyboard = KeyboardFactory.slotKeyboard(event.getId(), duration, options);
        editWizardMessage(cq, text, keyboard);
    }

    private void editWizardMessage(CallbackQuery cq, String text, InlineKeyboardMarkup keyboard) {
        Long chatId = cq.getMessage().getChatId();
        Integer messageId = cq.getMessage().getMessageId();
        sender.editText(chatId, messageId, text, keyboard);
    }

    private String eventDateLabel(Event event) {
        return event.getEventDate().toString();
    }
}
