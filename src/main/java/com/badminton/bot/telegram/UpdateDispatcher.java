package com.badminton.bot.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Единая точка входа для обновлений: команды в личке и callback-кнопки
 * (запись из поста канала + шаги мастера в личке).
 */
@Slf4j
@Component
public class UpdateDispatcher {

    private final BookingWizardHandler bookingWizardHandler;
    private final CommandDispatcher commandDispatcher;

    public UpdateDispatcher(BookingWizardHandler bookingWizardHandler,
                             CommandDispatcher commandDispatcher) {
        this.bookingWizardHandler = bookingWizardHandler;
        this.commandDispatcher = commandDispatcher;
    }

    public void dispatch(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                bookingWizardHandler.handle(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки обновления {}: {}", update.getUpdateId(), e.getMessage(), e);
        }
    }

    private void handleMessage(Message message) {
        if (!message.hasText() || message.getFrom() == null) {
            return;
        }
        // deep-link из канала: /start book_<eventId> — сразу открывает мастера записи
        String text = message.getText().trim();
        String[] parts = text.split("\\s+", 2);
        String command = parts[0].split("@")[0].toLowerCase();
        if ("/start".equals(command) && parts.length > 1) {
            String payload = parts[1].trim();
            if (payload.startsWith("book_")) {
                try {
                    long eventId = Long.parseLong(payload.substring("book_".length()));
                    // меню (reply-клавиатура) + мастер записи
                    commandDispatcher.handle(message);
                    if (!bookingWizardHandler.startBookingWizard(message.getFrom().getId(), eventId)) {
                        commandDispatcher.handleClosedEventHint(message.getChatId());
                    }
                    return;
                } catch (NumberFormatException ignored) {
                    // обычный /start ниже
                }
            }
        }
        commandDispatcher.handle(message);
    }
}
