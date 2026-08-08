package com.badminton.bot.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * Тонкая обёртка над {@link TelegramClient}: логирует и гасит ошибки Telegram API,
 * чтобы бизнес-логика не падала из-за временных сетевых проблем или 403/400 от Telegram.
 */
@Slf4j
@Component
public class TelegramSender {

    private final TelegramClient telegramClient;
    private final UserPanelStore panelStore;

    public TelegramSender(TelegramClient telegramClient, UserPanelStore panelStore) {
        this.telegramClient = telegramClient;
        this.panelStore = panelStore;
    }

    /**
     * Отправляет сообщение в чат, опционально как ответ на другое сообщение (чтобы попасть
     * в нужный тред комментариев канала) и опционально с inline-клавиатурой.
     */
    public Optional<Message> send(Long chatId, Integer replyToMessageId, String text, ReplyKeyboard keyboard) {
        SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(ParseMode.HTML)
                .disableWebPagePreview(true);
        if (replyToMessageId != null) {
            builder.replyToMessageId(replyToMessageId);
        }
        if (keyboard != null) {
            builder.replyMarkup(keyboard);
        }
        try {
            return Optional.of(telegramClient.execute(builder.build()));
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение в chat={} replyTo={}: {}", chatId, replyToMessageId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Message> send(Long chatId, String text, ReplyKeyboard keyboard) {
        return send(chatId, null, text, keyboard);
    }

    public Optional<Message> sendPhoto(Long chatId, byte[] jpegBytes, String filename,
                                        String caption, InlineKeyboardMarkup keyboard) {
        try {
            SendPhoto.SendPhotoBuilder<?, ?> builder = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(new ByteArrayInputStream(jpegBytes), filename))
                    .caption(caption)
                    .parseMode(ParseMode.HTML);
            if (keyboard != null) {
                builder.replyMarkup(keyboard);
            }
            return Optional.of(telegramClient.execute(builder.build()));
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить фото в chat={}: {}", chatId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return true если подпись актуальна (успех или «не изменилась»);
     * false только если сообщение реально недоступно для правки.
     */
    public boolean editCaption(Long chatId, Integer messageId, String caption, InlineKeyboardMarkup keyboard) {
        return editCaptionResult(chatId, messageId, caption, keyboard) != EditCaptionResult.MISSING;
    }

    public EditCaptionResult editCaptionResult(Long chatId, Integer messageId, String caption,
                                               InlineKeyboardMarkup keyboard) {
        try {
            var builder = EditMessageCaption.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .caption(caption)
                    .parseMode(ParseMode.HTML);
            if (keyboard != null) {
                builder.replyMarkup(keyboard);
            }
            telegramClient.execute(builder.build());
            return EditCaptionResult.OK;
        } catch (TelegramApiException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("message is not modified")) {
                // контент тот же — пробуем хотя бы обновить кнопки
                if (keyboard != null) {
                    tryEditReplyMarkup(chatId, messageId, keyboard);
                }
                return EditCaptionResult.OK;
            }
            if (msg.contains("message to edit not found")
                    || msg.contains("message can't be edited")
                    || msg.contains("message identifier is not specified")) {
                log.warn("Пост {} в chat={} недоступен для правки: {}", messageId, chatId, e.getMessage());
                return EditCaptionResult.MISSING;
            }
            log.warn("Не удалось обновить подпись сообщения {} в chat={}: {}", messageId, chatId, e.getMessage());
            return EditCaptionResult.FAILED;
        }
    }

    public boolean tryEditReplyMarkup(Long chatId, Integer messageId, InlineKeyboardMarkup keyboard) {
        try {
            telegramClient.execute(EditMessageReplyMarkup.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .replyMarkup(keyboard)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("message is not modified")) {
                return true;
            }
            log.warn("Не удалось обновить кнопки сообщения {} в chat={}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    public boolean deleteMessage(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось удалить сообщение {} в chat={}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    public enum EditCaptionResult {
        OK,
        /** Сообщение удалено / недоступно — можно переопубликовать. */
        MISSING,
        /** Временная/парсинговая ошибка — старый пост оставляем, новый не шлём. */
        FAILED
    }

    public boolean editText(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            // replyMarkup всегда задаём: иначе Telegram может оставить старые/пустые кнопки
            InlineKeyboardMarkup markup = keyboard != null
                    ? keyboard
                    : InlineKeyboardMarkup.builder().build();
            EditMessageText.EditMessageTextBuilder<?, ?> builder = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode(ParseMode.HTML)
                    .disableWebPagePreview(true)
                    .replyMarkup(markup);
            telegramClient.execute(builder.build());
            panelStore.put(chatId, messageId);
            return true;
        } catch (TelegramApiException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("message is not modified")) {
                if (keyboard != null) {
                    tryEditReplyMarkup(chatId, messageId, keyboard);
                }
                panelStore.put(chatId, messageId);
                return true;
            }
            log.warn("Не удалось отредактировать сообщение {} в chat={}: {}", messageId, chatId, e.getMessage());
            return false;
        }
    }

    /**
     * Один экран в личке: правит сохранённое сообщение или создаёт новое.
     * Inline-кнопки под сообщением важнее reply-меню (меню и так остаётся внизу чата).
     */
    public boolean showPanel(Long userId, String text, InlineKeyboardMarkup inline, ReplyKeyboard menuIfNew) {
        Integer messageId = panelStore.get(userId);
        if (messageId != null && editText(userId, messageId, text, inline)) {
            return true;
        }
        if (messageId != null) {
            panelStore.clear(userId);
        }

        // Сразу шлём с inline — иначе кнопки под текстом пропадают
        ReplyKeyboard firstMarkup = inline != null ? inline : menuIfNew;
        Optional<Message> sent = send(userId, text, firstMarkup);
        if (sent.isEmpty()) {
            return false;
        }
        Integer newId = sent.get().getMessageId();
        panelStore.put(userId, newId);

        // Reply-меню снизу: короткое служебное сообщение + удаление, не трогая панель
        if (menuIfNew != null && inline != null) {
            send(userId, "\u2060", menuIfNew).ifPresent(menuMsg ->
                    deleteMessage(userId, menuMsg.getMessageId()));
        }
        return true;
    }

    public boolean showPanel(Long userId, String text, InlineKeyboardMarkup inline) {
        return showPanel(userId, text, inline, null);
    }

    public void clearKeyboard(Long chatId, Integer messageId) {
        try {
            telegramClient.execute(EditMessageReplyMarkup.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .replyMarkup(InlineKeyboardMarkup.builder().build())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось снять клавиатуру с сообщения {} в chat={}: {}", messageId, chatId, e.getMessage());
        }
    }

    public void answerCallback(String callbackQueryId, String text) {
        answerCallback(callbackQueryId, text, false);
    }

    public void answerCallback(String callbackQueryId, String text, boolean showAlert) {
        try {
            AnswerCallbackQuery.AnswerCallbackQueryBuilder<?, ?> builder = AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .showAlert(showAlert);
            if (text != null) {
                builder.text(text);
            }
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на callback {}: {}", callbackQueryId, e.getMessage());
        }
    }

    public void sendDocument(Long chatId, String filename, byte[] content, String caption) {
        try {
            telegramClient.execute(SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(new ByteArrayInputStream(content), filename))
                    .caption(caption)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить файл {} в chat={}: {}", filename, chatId, e.getMessage());
        }
    }

    /** Пытается написать пользователю в личку; возвращает false, если бот не может (пользователь не жал /start). */
    public boolean sendPrivate(Long userId, String text) {
        return sendPrivate(userId, text, null);
    }

    public boolean sendPrivate(Long userId, String text, InlineKeyboardMarkup keyboard) {
        return showPanel(userId, text, keyboard, null);
    }
}
