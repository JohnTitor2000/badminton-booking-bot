package com.badminton.bot.telegram;

import com.badminton.bot.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Точка входа Telegram-бота (long polling). Вся логика делегируется в {@link UpdateDispatcher}.
 */
@Slf4j
@Component
public class BadmintonBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramProperties properties;
    private final UpdateDispatcher dispatcher;

    public BadmintonBot(TelegramProperties properties, UpdateDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    @Override
    public String getBotToken() {
        return properties.botToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        dispatcher.dispatch(update);
    }
}
