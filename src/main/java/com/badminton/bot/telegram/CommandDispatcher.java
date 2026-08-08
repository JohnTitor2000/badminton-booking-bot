package com.badminton.bot.telegram;

import com.badminton.bot.config.TelegramProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.domain.CreatedBy;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.BookingService;
import com.badminton.bot.service.EventService;
import com.badminton.bot.service.ExportService;
import com.badminton.bot.service.PlayerSkillService;
import com.badminton.bot.service.SlotSkillModel;
import com.badminton.bot.service.SlotCalculator;
import com.badminton.bot.service.TableRenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Команды и кнопки меню в личке: игроки и админы.
 */
@Slf4j
@Component
public class CommandDispatcher {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final EventService eventService;
    private final BookingService bookingService;
    private final SlotCalculator slotCalculator;
    private final TableRenderService tableRenderService;
    private final ExportService exportService;
    private final PlayerSkillService playerSkillService;
    private final TelegramSender sender;
    private final TelegramProperties telegramProperties;

    public CommandDispatcher(EventService eventService,
                              BookingService bookingService,
                              SlotCalculator slotCalculator,
                              TableRenderService tableRenderService,
                              ExportService exportService,
                              PlayerSkillService playerSkillService,
                              TelegramSender sender,
                              TelegramProperties telegramProperties) {
        this.eventService = eventService;
        this.bookingService = bookingService;
        this.slotCalculator = slotCalculator;
        this.tableRenderService = tableRenderService;
        this.exportService = exportService;
        this.playerSkillService = playerSkillService;
        this.sender = sender;
        this.telegramProperties = telegramProperties;
    }

    public void handle(Message message) {
        if (message.getFrom() == null || !message.hasText()) {
            return;
        }
        // меню только в личке
        if (!message.isUserMessage()) {
            return;
        }

        String text = message.getText().trim();
        Long chatId = message.getChatId();
        Long userId = message.getFrom().getId();
        boolean admin = telegramProperties.isAdmin(userId);

        String command = text.split("\\s+")[0].split("@")[0].toLowerCase();
        if (command.startsWith("/")) {
            switch (command) {
                case "/start" -> handleStart(chatId, admin);
                case "/my" -> handleMy(chatId, userId);
                case "/menu" -> handleStart(chatId, admin);
                default -> {
                    if (admin) {
                        // старые текстовые команды оставляем как скрытый fallback
                        handleLegacyAdminCommand(chatId, text);
                    }
                }
            }
            return;
        }

        switch (text) {
            case MenuLabels.MY_BOOKINGS -> handleMy(chatId, userId);
            case MenuLabels.HOW_TO -> handleHowTo(chatId, admin);
            case MenuLabels.ADMIN_PUBLISH -> requireAdmin(chatId, userId,
                    () -> askDate(chatId, CallbackAction.ADMIN_PUBLISH, "📢",
                            "Выберите дату — пост будет опубликован в канал:",
                            eventService.upcomingPublishableDates(8)));
            case MenuLabels.ADMIN_EVENTS -> requireAdmin(chatId, userId, () -> handleEvents(chatId));
            case MenuLabels.ADMIN_CANCEL -> requireAdmin(chatId, userId,
                    () -> askOpenDate(chatId, CallbackAction.ADMIN_CANCEL, "🚫",
                            "Какой день отменить?"));
            case MenuLabels.ADMIN_CLOSE -> requireAdmin(chatId, userId,
                    () -> askOpenDate(chatId, CallbackAction.ADMIN_CLOSE, "🔒",
                            "Какой день закрыть для записи?"));
            case MenuLabels.ADMIN_TABLE -> requireAdmin(chatId, userId,
                    () -> askOpenDate(chatId, CallbackAction.ADMIN_TABLE, "📊",
                            "Таблицу за какой день показать?"));
            case MenuLabels.ADMIN_BOOKINGS -> requireAdmin(chatId, userId,
                    () -> askOpenDate(chatId, CallbackAction.ADMIN_BOOKINGS, "👥",
                            "Список записей за какой день?"));
            case MenuLabels.ADMIN_EXPORT -> requireAdmin(chatId, userId,
                    () -> askOpenDate(chatId, CallbackAction.ADMIN_EXPORT, "⬇️",
                            "Экспорт за какой день?"));
            default -> {
                // обычный текст игнорируем
            }
        }
    }

    /** @return текст результата для показа в единой панели */
    public String executeAdminDateAction(Long chatId, CallbackAction action, LocalDate date) {
        return switch (action) {
            case ADMIN_PUBLISH -> {
                Event event = eventService.createAndPublish(date, CreatedBy.ADMIN);
                String hint = event.getChannelMessageId() != null
                        ? " Пост опубликован в канал."
                        : " Проверьте CHANNEL_ID и права бота.";
                yield "✅ Событие на " + date.format(DATE_FORMAT) + " создано." + hint;
            }
            case ADMIN_CANCEL -> {
                eventService.cancelDate(date);
                yield "🚫 День " + date.format(DATE_FORMAT) + " отменён.";
            }
            case ADMIN_CLOSE -> eventService.findByDate(date)
                    .map(event -> {
                        eventService.closeEvent(event.getId());
                        return "🔒 Запись на " + date.format(DATE_FORMAT) + " закрыта.";
                    })
                    .orElse("Событие на " + date.format(DATE_FORMAT) + " не найдено.");
            case ADMIN_TABLE -> tableText(date);
            case ADMIN_BOOKINGS -> bookingsText(date);
            case ADMIN_EXPORT -> exportAndStatus(chatId, date);
            default -> "Неизвестное действие.";
        };
    }

    private void handleStart(Long chatId, boolean admin) {
        String text = admin
                ? "🏸 Привет! Меню админа снизу.\n\nВ канале — анонс и таблица, запись игроков идёт в личке."
                : "🏸 Привет! Я бот записи на бадминтон.\n\n"
                + "Анонсы в канале: «Записаться» и «Кто записан» — продолжим здесь.\n"
                + "Кнопка «Мои записи» покажет ваши брони.";
        panel(chatId, text, null);
    }

    public void handleClosedEventHint(Long chatId) {
        panel(chatId, "🚫 Запись на это событие уже закрыта.", null);
    }

    /** Deep-link / callback: показать записанных на событие. */
    public boolean sendPlayersList(Long chatId, long eventId) {
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            return panel(chatId, "Событие не найдено.", null);
        }
        Event event = eventOpt.get();
        List<Booking> bookings = bookingService.activeBookings(eventId);
        return panel(chatId, tableRenderService.renderPlayersList(event, bookings), null);
    }

    private void handleHowTo(Long chatId, boolean admin) {
        panel(chatId, "ℹ️ <b>Как записаться</b>\n\n"
                + "1. Откройте пост в канале\n"
                + "2. Нажмите «Записаться»\n"
                + "3. В личке выберите длительность, время и число человек\n"
                + "4. Если мест нет — попадёте в лист ожидания\n"
                + "5. Можно сохранить вариант как пресет — в следующий раз запись в один клик\n\n"
                + "Изменить или отменить запись — кнопка «Мои записи».", null);
    }

    private void handleMy(Long chatId, Long userId) {
        List<Event> openEvents = eventService.findOpenEvents();
        List<KeyboardFactory.BookingButton> buttons = new ArrayList<>();
        StringBuilder sb = new StringBuilder("📋 <b>Ваши записи:</b>\n\n");
        for (Event event : openEvents) {
            for (Booking booking : bookingService.myActiveBookings(event.getId(), userId)) {
                buttons.add(new KeyboardFactory.BookingButton(event, booking));
                sb.append(event.getEventDate().format(DATE_FORMAT)).append(" ")
                        .append(slotCalculator.formatSlotRange(booking.getStartSlot(), booking.getDurationMinutes()))
                        .append(" — ").append(booking.getPartySize()).append(" чел., ")
                        .append(statusLabel(booking.getStatus())).append("\n");
            }
        }
        if (buttons.isEmpty()) {
            panel(chatId, "📋 Пока нет активных записей.", null);
            return;
        }
        sb.append("\n✏️ изменить · ❌ отменить");
        panel(chatId, sb.toString(), KeyboardFactory.myBookingsKeyboard(buttons, slotCalculator));
    }

    private void handleEvents(Long chatId) {
        List<Event> events = eventService.findOpenEvents();
        if (events.isEmpty()) {
            panel(chatId, "Сейчас нет открытых событий.", null);
            return;
        }
        StringBuilder sb = new StringBuilder("📅 <b>Открытые события:</b>\n\n");
        for (Event event : events) {
            sb.append(event.getEventDate().format(DATE_FORMAT))
                    .append(event.getChannelMessageId() == null ? " — пост ещё не опубликован" : " — в канале")
                    .append("\n");
        }
        panel(chatId, sb.toString(), null);
    }

    private void askOpenDate(Long chatId, CallbackAction action, String emoji, String prompt) {
        List<LocalDate> dates = eventService.findOpenEvents().stream()
                .map(Event::getEventDate)
                .toList();
        askDate(chatId, action, emoji, prompt, dates);
    }

    private void askDate(Long chatId, CallbackAction action, String emoji, String prompt, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            panel(chatId, "Нет подходящих дат.", null);
            return;
        }
        panel(chatId, prompt, KeyboardFactory.adminDatesKeyboard(action, dates, emoji));
    }

    private String tableText(LocalDate date) {
        return eventService.findByDate(date)
                .map(event -> tableRenderService.render(event, bookingService.activeBookings(event.getId())))
                .orElse("Событие на " + date.format(DATE_FORMAT) + " не найдено.");
    }

    private String bookingsText(LocalDate date) {
        return eventService.findByDate(date).map(event -> {
            List<Booking> bookings = bookingService.activeBookings(event.getId()).stream()
                    .sorted(Comparator.comparing(Booking::getStartSlot).thenComparing(Booking::getCreatedAt))
                    .toList();
            if (bookings.isEmpty()) {
                return "На " + date.format(DATE_FORMAT) + " пока никто не записался.";
            }
            List<Long> userIds = bookings.stream().map(Booking::getTelegramUserId).toList();
            var minutes = playerSkillService.minutesPlayedBefore(userIds, event.getEventDate());
            var skills = playerSkillService.skillsBefore(userIds, event.getEventDate());
            StringBuilder sb = new StringBuilder("👥 <b>Записи на " + date.format(DATE_FORMAT) + ":</b>\n\n");
            for (Booking b : bookings) {
                long mins = minutes.getOrDefault(b.getTelegramUserId(), 0L);
                double skill = skills.getOrDefault(b.getTelegramUserId(), 0.0);
                sb.append(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))
                        .append(" — ").append(b.getDisplayName())
                        .append(b.getUsername() != null ? " (@" + b.getUsername() + ")" : "")
                        .append("\n   ").append(b.getPartySize()).append(" чел. · ")
                        .append(SlotSkillModel.formatPlayerStats(mins, skill))
                        .append(" · ").append(statusLabel(b.getStatus())).append("\n");
            }
            sb.append("\n⏱ наиграно до этого дня · скилл 0–10");
            return sb.toString();
        }).orElse("Событие на " + date.format(DATE_FORMAT) + " не найдено.");
    }

    private String exportAndStatus(Long chatId, LocalDate date) {
        return eventService.findByDate(date).map(event -> {
            byte[] csv = exportService.toCsv(event, bookingService.activeBookings(event.getId()));
            sender.sendDocument(chatId, "badminton_" + date.format(DATE_FORMAT).replace('.', '_') + ".csv",
                    csv, "Экспорт записей на " + date.format(DATE_FORMAT));
            return "⬇️ CSV для " + date.format(DATE_FORMAT) + " отправлен отдельным файлом.";
        }).orElse("Событие на " + date.format(DATE_FORMAT) + " не найдено.");
    }

    private boolean panel(Long chatId, String text, InlineKeyboardMarkup inline) {
        boolean admin = telegramProperties.isAdmin(chatId);
        return sender.showPanel(chatId, text, inline, KeyboardFactory.mainMenu(admin));
    }

    private void handleLegacyAdminCommand(Long chatId, String text) {
        // минимальный fallback, если кто-то всё ещё шлёт /new_event
        if (text.toLowerCase().startsWith("/new_event")) {
            askDate(chatId, CallbackAction.ADMIN_PUBLISH, "📢",
                    "Выберите дату — пост будет опубликован в канал:",
                    eventService.upcomingPublishableDates(8));
        }
    }

    private void requireAdmin(Long chatId, Long userId, Runnable action) {
        if (!telegramProperties.isAdmin(userId)) {
            panel(chatId, "🚫 Это только для администратора.", null);
            return;
        }
        action.run();
    }

    private String statusLabel(BookingStatus status) {
        return status == BookingStatus.CONFIRMED ? "подтверждено ✅" : "лист ожидания ⏳";
    }
}
