package com.badminton.bot.telegram;

import com.badminton.bot.config.BadmintonProperties;
import com.badminton.bot.config.TelegramProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingPreset;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.BookingChangeResult;
import com.badminton.bot.service.BookingOutcome;
import com.badminton.bot.service.BookingPresetService;
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
    private final BookingPresetService presetService;
    private final SlotCalculator slotCalculator;
    private final BadmintonProperties badmintonProperties;
    private final TelegramProperties telegramProperties;
    private final TelegramSender sender;
    private final CommandDispatcher commandDispatcher;

    public BookingWizardHandler(EventService eventService,
                                 BookingService bookingService,
                                 BookingPresetService presetService,
                                 SlotCalculator slotCalculator,
                                 BadmintonProperties badmintonProperties,
                                 TelegramProperties telegramProperties,
                                 TelegramSender sender,
                                 CommandDispatcher commandDispatcher) {
        this.eventService = eventService;
        this.bookingService = bookingService;
        this.presetService = presetService;
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
                case WHO -> handleWho(callbackQuery, data);
                case CHANGE -> handleChange(callbackQuery, data);
                case SAVE_PRESET -> handleSavePreset(callbackQuery, data);
                case USE_PRESET -> handleUsePreset(callbackQuery, data);
                case MANUAL -> handleManual(callbackQuery, data);
                case CLEAR_PRESET -> handleClearPreset(callbackQuery, data);
                case ENTRY -> handleEntry(callbackQuery, data);
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
        boolean sent = startBookingWizard(cq.getFrom().getId(), eventId);
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

    private void handleWho(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Long userId = cq.getFrom().getId();
        if (!commandDispatcher.sendPlayersList(userId, eventId)) {
            String botMention = telegramProperties.botUsername() == null || telegramProperties.botUsername().isBlank()
                    ? "боту"
                    : "@" + telegramProperties.botUsername();
            sender.answerCallback(cq.getId(),
                    "Сначала напишите " + botMention + " в личку /start",
                    true);
            return;
        }
        sender.answerCallback(cq.getId(), "Список в личке с ботом");
    }

    /**
     * Старт мастера из deep-link {@code /start book_<eventId>} или старого callback.
     *
     * @return false, если событие закрыто/не найдено или не удалось отправить сообщение
     */
    public boolean startBookingWizard(Long userId, long eventId) {
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            return false;
        }
        Event event = eventOpt.get();
        var menu = KeyboardFactory.mainMenu(telegramProperties.isAdmin(userId));
        Optional<BookingPreset> preset = presetService.findValid(userId);
        if (preset.isPresent()) {
            String label = presetLabel(preset.get());
            String text = "🏸 Записываемся на " + eventDateLabel(event) + "\n\n"
                    + "Сохранённый вариант: <b>" + label + "</b>\n"
                    + "Использовать его или выбрать время вручную?";
            return sender.showPanel(userId, text, KeyboardFactory.entryChoiceKeyboard(eventId, label), menu);
        }
        return sender.showPanel(userId, manualStartText(event),
                KeyboardFactory.durationKeyboard(eventId, slotCalculator.durationOptionsMinutes(), slotCalculator, null),
                menu);
    }

    private void handleSavePreset(CallbackQuery cq, CallbackData data) {
        long bookingId = data.argLong(0);
        Optional<Booking> bookingOpt = bookingService.findById(bookingId);
        if (bookingOpt.isEmpty() || !bookingOpt.get().isActive()) {
            sender.answerCallback(cq.getId(), "Запись не найдена", true);
            return;
        }
        Booking booking = bookingOpt.get();
        if (!booking.getTelegramUserId().equals(cq.getFrom().getId())) {
            sender.answerCallback(cq.getId(), "Это не ваша запись", true);
            return;
        }
        presetService.saveFromBooking(booking);
        sender.answerCallback(cq.getId(), "Пресет сохранён");
        String slotLabel = slotCalculator.formatSlotRange(booking.getStartSlot(), booking.getDurationMinutes());
        String text = "✅ Запись: " + slotLabel + " (" + booking.getPartySize() + " чел.)\n"
                + eventDateLabel(booking.getEvent()) + "\n\n"
                + "💾 Пресет сохранён — в следующий раз можно выбрать его сразу.";
        editWizardMessage(cq, text, KeyboardFactory.bookingActionsKeyboardAfterPresetSaved(bookingId));
    }

    private void handleEntry(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта", true);
            return;
        }
        sender.answerCallback(cq.getId(), null);
        editEntryScreen(cq, eventOpt.get(), cq.getFrom().getId());
    }

    private void handleUsePreset(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта", true);
            return;
        }
        Event event = eventOpt.get();
        User from = cq.getFrom();
        Optional<BookingPreset> presetOpt = presetService.findValid(from.getId());
        if (presetOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Пресет не найден", true);
            editWizardMessage(cq, manualStartText(event),
                    KeyboardFactory.durationKeyboard(eventId, slotCalculator.durationOptionsMinutes(), slotCalculator, null));
            return;
        }
        BookingPreset preset = presetOpt.get();
        BookingResult result = bookingService.book(
                eventId, from.getId(), UserNames.displayName(from), from.getUserName(),
                preset.getStartSlot(), preset.getDurationMinutes(), preset.getPartySize());
        renderBookOutcome(cq, event, result.outcome(), result.booking(),
                preset.getStartSlot(), preset.getDurationMinutes(), preset.getPartySize(), false, List.of());
        if (result.outcome() == BookingOutcome.CONFIRMED || result.outcome() == BookingOutcome.WAITLISTED) {
            eventService.refreshBookingMessage(eventId);
        }
    }

    private void handleManual(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта", true);
            return;
        }
        sender.answerCallback(cq.getId(), null);
        showManualDuration(cq, eventOpt.get(), true);
    }

    private void handleClearPreset(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        presetService.delete(cq.getFrom().getId());
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Пресет удалён", true);
            editWizardMessage(cq, "🗑 Пресет удалён.", null);
            return;
        }
        sender.answerCallback(cq.getId(), "Пресет удалён");
        editWizardMessage(cq, "🗑 Пресет удалён.\n\n" + manualStartText(eventOpt.get()),
                KeyboardFactory.durationKeyboard(eventId, slotCalculator.durationOptionsMinutes(), slotCalculator, null));
    }

    private void handleChange(CallbackQuery cq, CallbackData data) {
        long bookingId = data.argLong(0);
        Optional<Booking> bookingOpt = bookingService.findById(bookingId);
        if (bookingOpt.isEmpty() || !bookingOpt.get().isActive()) {
            sender.answerCallback(cq.getId(), "Запись не найдена", true);
            return;
        }
        Booking booking = bookingOpt.get();
        if (!booking.getTelegramUserId().equals(cq.getFrom().getId())
                && !telegramProperties.isAdmin(cq.getFrom().getId())) {
            sender.answerCallback(cq.getId(), "Это не ваша запись", true);
            return;
        }
        Event event = booking.getEvent();
        if (!event.isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта", true);
            return;
        }

        String text = "✏️ Меняем запись на " + eventDateLabel(event) + "\n"
                + "Сейчас: " + slotCalculator.formatSlotRange(booking.getStartSlot(), booking.getDurationMinutes())
                + ", " + booking.getPartySize() + " чел.\n\nВыберите новую длительность:";
        InlineKeyboardMarkup keyboard = KeyboardFactory.durationKeyboard(
                event.getId(), slotCalculator.durationOptionsMinutes(), slotCalculator, bookingId);

        // из «Мои записи» редактируем то же сообщение; иначе шлём в личку
        if (cq.getMessage() != null && cq.getMessage().isUserMessage()) {
            sender.answerCallback(cq.getId(), null);
            editWizardMessage(cq, text, keyboard);
        } else {
            boolean sent = sender.sendPrivate(cq.getFrom().getId(), text, keyboard);
            if (!sent) {
                sender.answerCallback(cq.getId(), "Сначала напишите боту /start в личку", true);
                return;
            }
            sender.answerCallback(cq.getId(), "Продолжите в личке с ботом");
        }
    }

    private void handleDuration(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        Long replaceBookingId = data.optionalArgLong(2);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        renderSlotMenu(cq, eventOpt.get(), duration, replaceBookingId);
    }

    private void handleSlot(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        int startSlot = data.argInt(2);
        Long replaceBookingId = data.optionalArgLong(3);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty() || !eventOpt.get().isOpen()) {
            sender.answerCallback(cq.getId(), "Запись на это событие уже закрыта");
            return;
        }
        sender.answerCallback(cq.getId(), null);

        String text = "🏸 " + eventDateLabel(eventOpt.get()) + "\nВремя: " + slotCalculator.formatSlotRange(startSlot, duration)
                + "\n\nСколько человек (включая вас)?";
        InlineKeyboardMarkup keyboard = KeyboardFactory.sizeKeyboard(
                eventId, duration, startSlot, badmintonProperties.slotCapacity(), replaceBookingId);
        editWizardMessage(cq, text, keyboard);
    }

    private void handleConfirm(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        int startSlot = data.argInt(2);
        int partySize = data.argInt(3);
        Long replaceBookingId = data.optionalArgLong(4);

        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        Event event = eventOpt.get();
        User from = cq.getFrom();
        String displayName = UserNames.displayName(from);

        BookingOutcome outcome;
        Booking booking;
        List<Booking> promoted = List.of();

        if (replaceBookingId != null) {
            try {
                BookingChangeResult changed = bookingService.change(
                        replaceBookingId, from.getId(), displayName, from.getUserName(),
                        startSlot, duration, partySize);
                outcome = changed.outcome();
                booking = changed.booking();
                promoted = changed.promoted();
            } catch (SecurityException e) {
                sender.answerCallback(cq.getId(), "Это не ваша запись", true);
                return;
            } catch (IllegalArgumentException e) {
                sender.answerCallback(cq.getId(), "Запись не найдена", true);
                return;
            }
        } else {
            BookingResult result = bookingService.book(eventId, from.getId(), displayName, from.getUserName(),
                    startSlot, duration, partySize);
            outcome = result.outcome();
            booking = result.booking();
        }

        renderBookOutcome(cq, event, outcome, booking, startSlot, duration, partySize, replaceBookingId != null, promoted);

        if (outcome == BookingOutcome.CONFIRMED || outcome == BookingOutcome.WAITLISTED) {
            eventService.refreshBookingMessage(eventId);
            notifyPromoted(promoted);
        }
    }

    private void renderBookOutcome(CallbackQuery cq, Event event, BookingOutcome outcome, Booking booking,
                                   int startSlot, int duration, int partySize, boolean editing,
                                   List<Booking> promotedIgnored) {
        String slotLabel = slotCalculator.formatSlotRange(startSlot, duration);
        String text;
        InlineKeyboardMarkup keyboard = null;

        switch (outcome) {
            case CONFIRMED -> {
                text = (editing ? "✅ Запись изменена: " : "✅ Записал(а) вас на ")
                        + slotLabel + " (" + partySize + " чел.)\n" + eventDateLabel(event);
                keyboard = KeyboardFactory.bookingActionsKeyboard(booking.getId());
                sender.answerCallback(cq.getId(), editing ? "Изменено!" : "Подтверждено!");
            }
            case WAITLISTED -> {
                text = "⏳ Свободных мест уже нет, вы в листе ожидания на " + slotLabel + " (" + partySize + " чел.)\n"
                        + eventDateLabel(event) + "\nМы напишем, если освободится место.";
                keyboard = KeyboardFactory.bookingActionsKeyboard(booking.getId());
                sender.answerCallback(cq.getId(), "В листе ожидания");
            }
            case OVERLAPS_OWN_BOOKING -> {
                text = "⚠️ У вас уже есть запись на пересекающееся время в этом событии.";
                Optional<BookingPreset> preset = presetService.findValid(cq.getFrom().getId());
                if (!editing && preset.isPresent()) {
                    text += "\n\nМожно выбрать другое время вручную:";
                    keyboard = KeyboardFactory.durationKeyboard(
                            event.getId(), slotCalculator.durationOptionsMinutes(), slotCalculator, null);
                }
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
    }

    private void handleBackToDuration(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        Long replaceBookingId = data.optionalArgLong(1);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        Event event = eventOpt.get();
        if (replaceBookingId == null) {
            editEntryScreen(cq, event, cq.getFrom().getId());
            return;
        }
        String text = "✏️ Меняем запись на " + eventDateLabel(event) + "\n\nВыберите длительность:";
        InlineKeyboardMarkup keyboard = KeyboardFactory.durationKeyboard(
                eventId, slotCalculator.durationOptionsMinutes(), slotCalculator, replaceBookingId);
        editWizardMessage(cq, text, keyboard);
    }

    private void handleBackToSlot(CallbackQuery cq, CallbackData data) {
        long eventId = data.argLong(0);
        int duration = data.argInt(1);
        Long replaceBookingId = data.optionalArgLong(2);
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            sender.answerCallback(cq.getId(), "Событие не найдено");
            return;
        }
        sender.answerCallback(cq.getId(), null);
        renderSlotMenu(cq, eventOpt.get(), duration, replaceBookingId);
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
        String result = commandDispatcher.executeAdminDateAction(cq.getMessage().getChatId(), data.action(), date);
        editWizardMessage(cq, result, null);
    }

    private void notifyPromoted(List<Booking> promoted) {
        for (Booking booking : promoted) {
            String text = "✅ Для вас нашлось место! Ваша запись на "
                    + slotCalculator.formatSlotRange(booking.getStartSlot(), booking.getDurationMinutes())
                    + " теперь подтверждена.";
            InlineKeyboardMarkup keyboard = KeyboardFactory.bookingActionsKeyboard(booking.getId());
            Long uid = booking.getTelegramUserId();
            sender.showPanel(uid, text, keyboard, KeyboardFactory.mainMenu(telegramProperties.isAdmin(uid)));
        }
    }

    private void renderSlotMenu(CallbackQuery cq, Event event, int duration, Long replaceBookingId) {
        List<Booking> active = bookingService.activeBookings(event.getId()).stream()
                .filter(b -> replaceBookingId == null || !b.getId().equals(replaceBookingId))
                .toList();
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
        InlineKeyboardMarkup keyboard = KeyboardFactory.slotKeyboard(event.getId(), duration, options, replaceBookingId);
        editWizardMessage(cq, text, keyboard);
    }

    private void editWizardMessage(CallbackQuery cq, String text, InlineKeyboardMarkup keyboard) {
        Long chatId = cq.getMessage().getChatId();
        Integer messageId = cq.getMessage().getMessageId();
        if (!sender.editText(chatId, messageId, text, keyboard)) {
            sender.showPanel(chatId, text, keyboard,
                    KeyboardFactory.mainMenu(telegramProperties.isAdmin(cq.getFrom().getId())));
        }
    }

    private String eventDateLabel(Event event) {
        return event.getEventDate().toString();
    }

    private String manualStartText(Event event) {
        return "🏸 Записываемся на " + eventDateLabel(event) + "\n\nВыберите длительность:";
    }

    private String presetLabel(BookingPreset preset) {
        return slotCalculator.formatSlotRange(preset.getStartSlot(), preset.getDurationMinutes())
                + ", " + preset.getPartySize() + " чел.";
    }

    private void editEntryScreen(CallbackQuery cq, Event event, Long userId) {
        Optional<BookingPreset> preset = presetService.findValid(userId);
        if (preset.isPresent()) {
            String label = presetLabel(preset.get());
            String text = "🏸 Записываемся на " + eventDateLabel(event) + "\n\n"
                    + "Сохранённый вариант: <b>" + label + "</b>\n"
                    + "Использовать его или выбрать время вручную?";
            editWizardMessage(cq, text, KeyboardFactory.entryChoiceKeyboard(event.getId(), label));
            return;
        }
        showManualDuration(cq, event, false);
    }

    private void showManualDuration(CallbackQuery cq, Event event, boolean backToPresetChoice) {
        InlineKeyboardMarkup keyboard = backToPresetChoice
                ? KeyboardFactory.durationKeyboardWithBack(
                        event.getId(), slotCalculator.durationOptionsMinutes(), slotCalculator, true)
                : KeyboardFactory.durationKeyboard(
                        event.getId(), slotCalculator.durationOptionsMinutes(), slotCalculator, null);
        editWizardMessage(cq, manualStartText(event), keyboard);
    }
}
