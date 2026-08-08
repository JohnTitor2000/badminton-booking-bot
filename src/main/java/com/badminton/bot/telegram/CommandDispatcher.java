package com.badminton.bot.telegram;

import com.badminton.bot.config.TelegramProperties;
import com.badminton.bot.domain.Booking;
import com.badminton.bot.domain.BookingStatus;
import com.badminton.bot.domain.CreatedBy;
import com.badminton.bot.domain.Event;
import com.badminton.bot.service.BookingService;
import com.badminton.bot.service.EventService;
import com.badminton.bot.service.ExportService;
import com.badminton.bot.service.PlayerVisitService;
import com.badminton.bot.service.SlotCalculator;
import com.badminton.bot.service.TableRenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private final PlayerVisitService playerVisitService;
    private final TelegramSender sender;
    private final TelegramProperties telegramProperties;

    public CommandDispatcher(EventService eventService,
                              BookingService bookingService,
                              SlotCalculator slotCalculator,
                              TableRenderService tableRenderService,
                              ExportService exportService,
                              PlayerVisitService playerVisitService,
                              TelegramSender sender,
                              TelegramProperties telegramProperties) {
        this.eventService = eventService;
        this.bookingService = bookingService;
        this.slotCalculator = slotCalculator;
        this.tableRenderService = tableRenderService;
        this.exportService = exportService;
        this.playerVisitService = playerVisitService;
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

    public void executeAdminDateAction(Long chatId, CallbackAction action, LocalDate date) {
        switch (action) {
            case ADMIN_PUBLISH -> {
                Event event = eventService.createAndPublish(date, CreatedBy.ADMIN);
                String hint = event.getChannelMessageId() != null
                        ? " Пост опубликован в канал."
                        : " Проверьте CHANNEL_ID и права бота.";
                sender.send(chatId, "✅ Событие на " + date.format(DATE_FORMAT) + " создано." + hint, null);
            }
            case ADMIN_CANCEL -> {
                eventService.cancelDate(date);
                sender.send(chatId, "🚫 День " + date.format(DATE_FORMAT) + " отменён.", null);
            }
            case ADMIN_CLOSE -> eventService.findByDate(date).ifPresentOrElse(
                    event -> {
                        eventService.closeEvent(event.getId());
                        sender.send(chatId, "🔒 Запись на " + date.format(DATE_FORMAT) + " закрыта.", null);
                    },
                    () -> sender.send(chatId, "Событие на " + date.format(DATE_FORMAT) + " не найдено.", null));
            case ADMIN_TABLE -> handleTable(chatId, date);
            case ADMIN_BOOKINGS -> handleBookings(chatId, date);
            case ADMIN_EXPORT -> handleExport(chatId, date);
            default -> sender.send(chatId, "Неизвестное действие.", null);
        }
    }

    private void handleStart(Long chatId, boolean admin) {
        String text = admin
                ? "🏸 Привет! Меню админа снизу.\n\nВ канале — анонс и таблица, запись игроков идёт в личке."
                : "🏸 Привет! Я бот записи на бадминтон.\n\n"
                + "Анонсы публикуются в канале. Нажмите «Записаться» под постом — продолжим здесь.\n"
                + "Кнопка «Мои записи» покажет ваши брони.";
        sender.send(chatId, text, KeyboardFactory.mainMenu(admin));
    }

    public void handleClosedEventHint(Long chatId) {
        sender.send(chatId, "🚫 Запись на это событие уже закрыта.", null);
    }

    private void handleHowTo(Long chatId, boolean admin) {
        sender.send(chatId, "ℹ️ <b>Как записаться</b>\n\n"
                + "1. Откройте пост в канале\n"
                + "2. Нажмите «Записаться»\n"
                + "3. В личке выберите длительность, время и число человек\n"
                + "4. Если мест нет — попадёте в лист ожидания\n"
                + "5. Можно сохранить вариант как пресет — в следующий раз запись в один клик\n\n"
                + "Изменить или отменить запись — кнопка «Мои записи».",
                KeyboardFactory.mainMenu(admin));
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
            sender.send(chatId, "📋 Пока нет активных записей.", null);
            return;
        }
        sb.append("\n✏️ изменить · ❌ отменить");
        sender.send(chatId, sb.toString(), KeyboardFactory.myBookingsKeyboard(buttons, slotCalculator));
    }

    private void handleEvents(Long chatId) {
        List<Event> events = eventService.findOpenEvents();
        if (events.isEmpty()) {
            sender.send(chatId, "Сейчас нет открытых событий.", null);
            return;
        }
        StringBuilder sb = new StringBuilder("📅 <b>Открытые события:</b>\n\n");
        for (Event event : events) {
            sb.append(event.getEventDate().format(DATE_FORMAT))
                    .append(event.getChannelMessageId() == null ? " — пост ещё не опубликован" : " — в канале")
                    .append("\n");
        }
        sender.send(chatId, sb.toString(), null);
    }

    private void askOpenDate(Long chatId, CallbackAction action, String emoji, String prompt) {
        List<LocalDate> dates = eventService.findOpenEvents().stream()
                .map(Event::getEventDate)
                .toList();
        askDate(chatId, action, emoji, prompt, dates);
    }

    private void askDate(Long chatId, CallbackAction action, String emoji, String prompt, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            sender.send(chatId, "Нет подходящих дат.", null);
            return;
        }
        sender.send(chatId, prompt, KeyboardFactory.adminDatesKeyboard(action, dates, emoji));
    }

    private void handleTable(Long chatId, LocalDate date) {
        eventService.findByDate(date).ifPresentOrElse(
                event -> sender.send(chatId, tableRenderService.render(event, bookingService.activeBookings(event.getId())), null),
                () -> sender.send(chatId, "Событие на " + date.format(DATE_FORMAT) + " не найдено.", null));
    }

    private void handleBookings(Long chatId, LocalDate date) {
        eventService.findByDate(date).ifPresentOrElse(
                event -> {
                    List<Booking> bookings = bookingService.activeBookings(event.getId()).stream()
                            .sorted(Comparator.comparing(Booking::getStartSlot).thenComparing(Booking::getCreatedAt))
                            .toList();
                    if (bookings.isEmpty()) {
                        sender.send(chatId, "На " + date.format(DATE_FORMAT) + " пока никто не записался.", null);
                        return;
                    }
                    var visits = playerVisitService.visitCountsBefore(
                            bookings.stream().map(Booking::getTelegramUserId).toList(),
                            event.getEventDate());
                    StringBuilder sb = new StringBuilder("👥 <b>Записи на " + date.format(DATE_FORMAT) + ":</b>\n\n");
                    for (Booking b : bookings) {
                        int v = visits.getOrDefault(b.getTelegramUserId(), 0);
                        sb.append(slotCalculator.formatSlotRange(b.getStartSlot(), b.getDurationMinutes()))
                                .append(" — ").append(b.getDisplayName())
                                .append(b.getUsername() != null ? " (@" + b.getUsername() + ")" : "")
                                .append(" ·").append(v)
                                .append(", ").append(b.getPartySize()).append(" чел., ")
                                .append(statusLabel(b.getStatus())).append("\n");
                    }
                    sb.append("\n<code>·N</code> — сколько раз уже был(а) до этого дня");
                    sender.send(chatId, sb.toString(), null);
                },
                () -> sender.send(chatId, "Событие на " + date.format(DATE_FORMAT) + " не найдено.", null));
    }

    private void handleExport(Long chatId, LocalDate date) {
        eventService.findByDate(date).ifPresentOrElse(
                event -> {
                    byte[] csv = exportService.toCsv(event, bookingService.activeBookings(event.getId()));
                    sender.sendDocument(chatId, "badminton_" + date.format(DATE_FORMAT).replace('.', '_') + ".csv",
                            csv, "Экспорт записей на " + date.format(DATE_FORMAT));
                },
                () -> sender.send(chatId, "Событие на " + date.format(DATE_FORMAT) + " не найдено.", null));
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
            sender.send(chatId, "🚫 Это только для администратора.", null);
            return;
        }
        action.run();
    }

    private String statusLabel(BookingStatus status) {
        return status == BookingStatus.CONFIRMED ? "подтверждено ✅" : "лист ожидания ⏳";
    }
}
