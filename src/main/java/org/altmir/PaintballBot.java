package org.altmir;

import lombok.RequiredArgsConstructor;
import org.altmir.Service.AdminService;
import org.altmir.Service.UserService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PaintballBot extends TelegramLongPollingBot {
    private final UserService userService;
    private final AdminService adminService;
    private final String botUsername;
    private final String botToken;

    private final Map<String, String> adminCommands = new HashMap<>();
    private final Map<Long, Long> pendingAdminActions = new HashMap<>();

    public PaintballBot(String botUsername, String botToken) {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.userService = new UserService();
        this.adminService = new AdminService();

        adminCommands.put("/admin_add", "Добавить администратора");
        adminCommands.put("/admin_remove", "Удалить администратора");
        adminCommands.put("/admin_list", "Список администраторов");
        adminCommands.put("/bonus_add", "Добавить бонусы пользователю");
        adminCommands.put("/bonus_remove", "Удалить бонусы пользователю");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText();
        User telegramUser = message.getFrom();
        Long userId = telegramUser.getId();

        // Проверяем, является ли пользователь администратором
        boolean isAdmin = adminService.isAdmin(userId);

        // Обрабатываем команды администратора
        if (isAdmin && text.startsWith("/admin_")) {
            handleAdminCommand(chatId, text, userId, message);
            return;
        }

        if (isAdmin && text.startsWith("/bonus_")) {
            handleBonusCommand(chatId, text, userId, message);
            return;
        }

        // Получаем или создаем пользователя
        userService.getOrCreateUser(chatId, telegramUser.getUserName());

        if (!userService.hasAcceptedTerms(chatId)) {
            handleTerms(chatId, text);
            return;
        }

        // Основной функционал для обычных пользователей
        switch (text) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "🎮 Добавить игру":
                handleGameRequest(chatId, telegramUser.getUserName());
                break;
            case "⭐ Моя статистика":
                sendMessage(chatId, userService.getUserStats(chatId));
                break;
            case "🎁 Бонусы":
                handleBonusInfo(chatId);
                break;
            default:
                // Обработка ожидания ввода от администратора
                if (pendingAdminActions.containsKey(chatId)) {
                    handlePendingAdminAction(chatId, text, userId);
                }
                break;
        }
    }

    private void handleTerms(Long chatId, String text) {
        if ("/start".equals(text)) {
            sendTermsMessage(chatId);
        } else if ("✅ Принять соглашение".equals(text)) {
            userService.acceptTerms(chatId);
            sendMessage(chatId, "✅ Отлично! Вы приняли пользовательское соглашение.\n\nТеперь вам доступен весь функционал бота!");
            sendWelcomeMessage(chatId);
        } else {
            sendTermsMessage(chatId);
        }
    }

    private void handleGameRequest(Long chatId, String username) {
        Long requestId = adminService.createGameRequest(chatId, username);

        if (requestId != null) {
            sendMessage(chatId, "✅ Запрос на добавление игры отправлен администраторам. Ожидайте подтверждения.");

            // Отправляем уведомление всем администраторам
            notifyAdminsAboutGameRequest(requestId, chatId, username);
        } else {
            sendMessage(chatId, "❌ Ошибка при создании запроса. Попробуйте позже.");
        }
    }

    private void notifyAdminsAboutGameRequest(Long requestId, Long userChatId, String username) {
        List<Long> adminIds = adminService.getAllAdminIds();
        String message = String.format(
                "🎮 *Новый запрос на добавление игры*\n\n" +
                        "ID запроса: %d\n" +
                        "Пользователь: @%s\n" +
                        "Chat ID: %d\n" +
                        "Время: %s\n\n" +
                        "Подтвердить добавление игры?",
                requestId, username, userChatId, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );

        for (Long adminId : adminIds) {
            SendMessage adminMsg = new SendMessage();
            adminMsg.setChatId(adminId.toString());
            adminMsg.setText(message);
            adminMsg.setParseMode("Markdown");
            adminMsg.setReplyMarkup(createAdminApproveKeyboard(requestId));

            try {
                execute(adminMsg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private InlineKeyboardMarkup createAdminApproveKeyboard(Long requestId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton approveBtn = new InlineKeyboardButton();
        approveBtn.setText("✅ Подтвердить");
        approveBtn.setCallbackData("approve_" + requestId);
        row1.add(approveBtn);

        InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
        rejectBtn.setText("❌ Отклонить");
        rejectBtn.setCallbackData("reject_" + requestId);
        row1.add(rejectBtn);

        rows.add(row1);
        markup.setKeyboard(rows);
        return markup;
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long adminId = callbackQuery.getFrom().getId();
        String callbackId = callbackQuery.getId();

        if (data.startsWith("approve_")) {
            Long requestId = Long.parseLong(data.split("_")[1]);
            adminService.approveGameRequest(requestId, adminId);

            // Уведомляем пользователя
            Long userChatId = adminService.getUserChatIdFromRequest(requestId);
            if (userChatId != null) {
                sendMessage(userChatId, "✅ Ваша игра подтверждена администратором! +10 бонусов");
                sendMessage(userChatId, userService.getUserStats(userChatId));
            }

            // Отвечаем на callback
            answerCallbackQuery(callbackId, "✅ Игра подтверждена");

        } else if (data.startsWith("reject_")) {
            Long requestId = Long.parseLong(data.split("_")[1]);
            adminService.rejectGameRequest(requestId, adminId);

            // Уведомляем пользователя
            Long userChatId = adminService.getUserChatIdFromRequest(requestId);
            if (userChatId != null) {
                sendMessage(userChatId, "❌ Ваш запрос на игру отклонен администратором.");
            }

            answerCallbackQuery(callbackId, "❌ Игра отклонена");
        }
    }

    private void handleAdminCommand(Long chatId, String command, Long adminId, Message message) {
        switch (command) {
            case "/admin_list":
                showAdminList(chatId);
                break;
            case "/admin_add":
                sendMessage(chatId, "Для добавления администратора отправьте:\n/admin_add [ID пользователя]");
                pendingAdminActions.put(chatId, 1L); // 1 = ожидание ID для добавления админа
                break;
            case "/admin_remove":
                sendMessage(chatId, "Для удаления администратора отправьте:\n/admin_remove [ID пользователя]");
                pendingAdminActions.put(chatId, 2L); // 2 = ожидание ID для удаления админа
                break;
            default:
                if (command.startsWith("/admin_add ")) {
                    String[] parts = command.split(" ");
                    if (parts.length == 2) {
                        try {
                            Long newAdminId = Long.parseLong(parts[1]);
                            adminService.addAdmin(newAdminId, "username", adminId);
                            sendMessage(chatId, "✅ Администратор успешно добавлен!");
                        } catch (NumberFormatException e) {
                            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /admin_add [числовой ID]");
                        }
                    }
                } else if (command.startsWith("/admin_remove ")) {
                    String[] parts = command.split(" ");
                    if (parts.length == 2) {
                        try {
                            Long removeAdminId = Long.parseLong(parts[1]);
                            adminService.removeAdmin(removeAdminId);
                            sendMessage(chatId, "✅ Администратор успешно удален!");
                        } catch (NumberFormatException e) {
                            sendMessage(chatId, "❌ Неверный формат ID. Используйте: /admin_remove [числовой ID]");
                        }
                    }
                }
                break;
        }
    }

    private void handleBonusCommand(Long chatId, String command, Long adminId, Message message) {
        if (command.startsWith("/bonus_add ")) {
            String[] parts = command.split(" ");
            if (parts.length == 3) {
                try {
                    Long userChatId = Long.parseLong(parts[1]);
                    int points = Integer.parseInt(parts[2]);
                    adminService.addBonusPoints(userChatId, points, adminId);
                    sendMessage(chatId, "✅ Бонусы успешно добавлены пользователю!");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат. Используйте: /bonus_add [ID пользователя] [количество баллов]");
                }
            } else {
                sendMessage(chatId, "❌ Неверный формат. Используйте: /bonus_add [ID пользователя] [количество баллов]");
            }
        } else if (command.startsWith("/bonus_remove ")) {
            String[] parts = command.split(" ");
            if (parts.length == 3) {
                try {
                    Long userChatId = Long.parseLong(parts[1]);
                    int points = Integer.parseInt(parts[2]);
                    adminService.removeBonusPoints(userChatId, points, adminId);
                    sendMessage(chatId, "✅ Бонусы успешно сняты у пользователя!");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат. Используйте: /bonus_remove [ID пользователя] [количество баллов]");
                }
            } else {
                sendMessage(chatId, "❌ Неверный формат. Используйте: /bonus_remove [ID пользователя] [количество баллов]");
            }
        }
    }

    private void handlePendingAdminAction(Long chatId, String text, Long adminId) {
        Long actionType = pendingAdminActions.get(chatId);

        try {
            Long targetId = Long.parseLong(text);

            if (actionType == 1L) { // Добавление администратора
                adminService.addAdmin(targetId, "username", adminId);
                sendMessage(chatId, "✅ Администратор успешно добавлен!");
            } else if (actionType == 2L) { // Удаление администратора
                adminService.removeAdmin(targetId);
                sendMessage(chatId, "✅ Администратор успешно удален!");
            }

            pendingAdminActions.remove(chatId);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат ID. Отправьте числовой ID пользователя.");
        }
    }

    private void showAdminList(Long chatId) {
        List<String> admins = adminService.getAdminList();
        StringBuilder message = new StringBuilder("👑 *Список администраторов:*\n\n");

        for (String admin : admins) {
            message.append("• ").append(admin).append("\n");
        }

        sendMessage(chatId, message.toString());
    }

    private void answerCallbackQuery(String callbackId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(text);

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTermsMessage(Long chatId) {
        String terms = "📝 *Пользовательское соглашение*\n\n" +
                "1. Бот предназначен для учета посещений пейнтбольного клуба\n" +
                "2. Ваши данные (ID чата, статистика) сохраняются в базе данных\n" +
                "3. Вы можете в любой момент прекратить использование бота\n" +
                "4. Администрация оставляет за собой право изменять правила\n\n" +
                "Для использования бота необходимо принять соглашение:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(terms);
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("✅ Принять соглашение");
        rows.add(row);

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎯 Добро пожаловать в Paintball Club Bot!\n\nВыберите действие:");
        message.setReplyMarkup(createMainKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createMainKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🎮 Добавить игру");
        row1.add("⭐ Моя статистика");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🎁 Бонусы");

        rows.add(row1);
        rows.add(row2);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private void handleBonusInfo(Long chatId) {
        int bonusPoints = userService.getBonusPoints(chatId);
        int freeGames = bonusPoints / 10;

        String message = String.format(
                "🎁 *Бонусная система*\n\n" +
                        "⭐ Ваши бонусы: %d\n" +
                        "🎮 Доступно бесплатных игр: %d\n\n" +
                        "💎 *Правила:*\n" +
                        "• 1 игра = 10 бонусов\n" +
                        "• 100 бонусов = 1 бесплатная игра\n" +
                        "• Бонусы не сгорают",
                bonusPoints, freeGames
        );

        sendMessage(chatId, message);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}