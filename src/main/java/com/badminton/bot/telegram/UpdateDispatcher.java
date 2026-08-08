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
        if (message.hasText() && message.getFrom() != null) {
            commandDispatcher.handle(message);
        }
    }
}
