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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;

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

        // Инициализация команд администраторов
        adminCommands.put("/admin_add", "Добавить администратора");
        adminCommands.put("/admin_remove", "Удалить администратора");
        adminCommands.put("/admin_list", "Список администраторов");
        adminCommands.put("/bonus_add", "Добавить бонусы пользователю");
        adminCommands.put("/bonus_remove", "Удалить бонусы пользователю");
        adminCommands.put("/stats", "Статистика пользователя");
        adminCommands.put("/admin_help", "Показать справку");
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
        String username = telegramUser.getUserName();

        System.out.println("User ID: " + userId + ", Username: @" + username + ", Chat ID: " + chatId);

        // Проверяем, является ли пользователь администратором
        boolean isAdmin = adminService.isAdmin(userId);

        // Если администратор - показываем админ-панель
        if (isAdmin) {
            if ("/start".equals(text) || "🏠 Главное меню".equals(text)) {
                showAdminPanel(chatId, userId);
                return;
            }

            // Обрабатываем команды администратора
            if (text.startsWith("/admin_") || text.startsWith("/bonus_") || text.startsWith("/stats") || "/admin_help".equals(text)) {
                handleAdminCommand(chatId, text, userId, message);
                return;
            }

            // Обработка кнопок админ-панели
            if (isAdmin && handleAdminPanelButtons(chatId, text, userId)) {
                return;
            }

            // Обработка ожидания ввода от администратора
            if (pendingAdminActions.containsKey(chatId)) {
                handlePendingAdminAction(chatId, text, userId);
                return;
            }
        }

        // Получаем или создаем пользователя
        userService.getOrCreateUser(chatId, username);

        if (!userService.hasAcceptedTerms(chatId)) {
            handleTerms(chatId, text, isAdmin);
            return;
        }

        // Основной функционал для обычных пользователей
        switch (text) {
            case "/start":
                sendWelcomeMessage(chatId, isAdmin);
                break;
            case "🎮 Добавить игру":
                handleGameRequest(chatId, username);
                break;
            case "⭐ Моя статистика":
                sendMessage(chatId, userService.getUserStats(chatId));
                break;
            case "🎁 Бонусы":
                handleBonusInfo(chatId);
                break;
        }
    }

    private boolean handleAdminPanelButtons(Long chatId, String text, Long adminId) {
        switch (text) {
            case "📊 Статистика пользователя":
                sendMessage(chatId, "Для просмотра статистики пользователя отправьте:\n`/stats [ID пользователя]`");
                return true;
            case "🎮 Запросы игр":
                showPendingGameRequests(chatId);
                return true;
            case "⭐ Управление бонусами":
                showBonusManagementPanel(chatId);
                return true;
            case "👑 Управление админами":
                showAdminManagementPanel(chatId);
                return true;
            case "📋 Справка":
                showAdminHelp(chatId);
                return true;
        }
        return false;
    }

    private void showAdminPanel(Long chatId, Long adminId) {
        String welcomeMessage = "👑 *Панель администратора*\n\n" +
                "ID: " + adminId + "\n" +
                "Выберите действие:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(welcomeMessage);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createAdminKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup createAdminKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setSelective(true);

        List<KeyboardRow> rows = new ArrayList<>();

        // Первый ряд
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📊 Статистика пользователя");
        row1.add("🎮 Запросы игр");

        // Второй ряд
        KeyboardRow row2 = new KeyboardRow();
        row2.add("⭐ Управление бонусами");
        row2.add("👑 Управление админами");

        // Третий ряд
        KeyboardRow row3 = new KeyboardRow();
        row3.add("📋 Справка");
        row3.add("🏠 Главное меню");

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private void handleTerms(Long chatId, String text, boolean isAdmin) {
        if ("/start".equals(text)) {
            sendTermsMessage(chatId, isAdmin);
        } else if ("✅ Принять соглашение".equals(text)) {
            userService.acceptTerms(chatId);
            String message = "✅ Отлично! Вы приняли пользовательское соглашение.\n\n";
            if (isAdmin) {
                message += "Вам доступен функционал администратора!";
                sendMessage(chatId, message);
                showAdminPanel(chatId, chatId);
            } else {
                message += "Теперь вам доступен весь функционал бота!";
                sendMessage(chatId, message);
                sendWelcomeMessage(chatId, false);
            }
        } else {
            sendTermsMessage(chatId, isAdmin);
        }
    }

    private void sendTermsMessage(Long chatId, boolean isAdmin) {
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

    private void handleAdminCommand(Long chatId, String command, Long adminId, Message message) {
        if ("/admin_help".equals(command)) {
            showAdminHelp(chatId);
            return;
        }

        if ("/admin_list".equals(command)) {
            showAdminList(chatId);
            return;
        }

        if (command.startsWith("/stats ")) {
            handleStatsCommand(chatId, command);
            return;
        }

        if (command.startsWith("/admin_add ")) {
            handleAdminAddCommand(chatId, command, adminId);
            return;
        }

        if (command.startsWith("/admin_remove ")) {
            handleAdminRemoveCommand(chatId, command);
            return;
        }

        if (command.startsWith("/bonus_add ")) {
            handleBonusAddCommand(chatId, command, adminId);
            return;
        }

        if (command.startsWith("/bonus_remove ")) {
            handleBonusRemoveCommand(chatId, command, adminId);
            return;
        }

        if ("/admin_add".equals(command)) {
            sendMessage(chatId, "Для добавления администратора отправьте:\n`/admin_add [ID пользователя]`");
            pendingAdminActions.put(chatId, 1L);
            return;
        }

        if ("/admin_remove".equals(command)) {
            sendMessage(chatId, "Для удаления администратора отправьте:\n`/admin_remove [ID пользователя]`");
            pendingAdminActions.put(chatId, 2L);
            return;
        }

        // Если команда не распознана
        sendMessage(chatId, "❌ Неизвестная команда. Используйте `/admin_help` для просмотра доступных команд.");
    }

    private void showAdminManagementPanel(Long chatId) {
        String message = "👑 *Управление администраторами*\n\n" +
                "• `/admin_list` - Список администраторов\n" +
                "• `/admin_add [ID]` - Добавить администратора\n" +
                "• `/admin_remove [ID]` - Удалить администратора\n\n" +
                "📋 *Текущие администраторы:*\n";

        List<String> admins = adminService.getAdminList();
        for (String admin : admins) {
            message += "• " + admin + "\n";
        }

        sendMessage(chatId, message);
    }

    private void showBonusManagementPanel(Long chatId) {
        String message = "⭐ *Управление бонусами*\n\n" +
                "• `/bonus_add [ID] [кол-во]` - Добавить бонусы\n" +
                "• `/bonus_remove [ID] [кол-во]` - Снять бонусы\n\n" +
                "Пример: `/bonus_add 123456789 50` - добавить 50 бонусов пользователю 123456789";

        sendMessage(chatId, message);
    }

    private void showPendingGameRequests(Long chatId) {
        String message = "🎮 *Запросы на добавление игр*\n\n" +
                "Новые запросы приходят автоматически. Используйте кнопки для подтверждения или отклонения.";

        sendMessage(chatId, message);
    }

    private void showAdminHelp(Long chatId) {
        StringBuilder helpMessage = new StringBuilder("👑 *Команды администратора:*\n\n");

        for (Map.Entry<String, String> entry : adminCommands.entrySet()) {
            helpMessage.append("• ").append(entry.getKey())
                    .append(" - ").append(entry.getValue()).append("\n");
        }

        helpMessage.append("\n📋 *Примеры использования:*\n");
        helpMessage.append("• `/admin_add 123456789` - добавить администратора\n");
        helpMessage.append("• `/admin_remove 123456789` - удалить администратора\n");
        helpMessage.append("• `/bonus_add 987654321 50` - добавить 50 бонусов\n");
        helpMessage.append("• `/bonus_remove 987654321 25` - снять 25 бонусов\n");
        helpMessage.append("• `/stats 987654321` - посмотреть статистику пользователя");

        sendMessage(chatId, helpMessage.toString());
    }

    private void handleStatsCommand(Long chatId, String command) {
        if (command.startsWith("/stats ")) {
            String[] parts = command.split(" ");
            if (parts.length == 2) {
                try {
                    Long userChatId = Long.parseLong(parts[1]);
                    String stats = userService.getUserStats(userChatId);
                    if (stats.contains("не найден")) {
                        sendMessage(chatId, "❌ Пользователь с ID " + userChatId + " не найден.");
                    } else {
                        sendMessage(chatId, "📊 *Статистика пользователя " + userChatId + ":*\n\n" + stats);
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат ID. Используйте: `/stats [ID пользователя]`");
                }
            } else {
                sendMessage(chatId, "❌ Неверный формат. Используйте: `/stats [ID пользователя]`");
            }
        } else {
            sendMessage(chatId, "❌ Неверный формат. Используйте: `/stats [ID пользователя]`");
        }
    }

    private void handleAdminAddCommand(Long chatId, String command, Long adminId) {
        String[] parts = command.split(" ");
        if (parts.length == 2) {
            try {
                Long newAdminId = Long.parseLong(parts[1]);
                String username = "username";
                adminService.addAdmin(newAdminId, username, adminId);
                sendMessage(chatId, "✅ Администратор успешно добавлен!");
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный формат ID. Используйте: `/admin_add [числовой ID]`");
            }
        } else {
            sendMessage(chatId, "❌ Неверный формат. Используйте: `/admin_add [ID пользователя]`");
        }
    }

    private void handleAdminRemoveCommand(Long chatId, String command) {
        String[] parts = command.split(" ");
        if (parts.length == 2) {
            try {
                Long removeAdminId = Long.parseLong(parts[1]);
                adminService.removeAdmin(removeAdminId);
                sendMessage(chatId, "✅ Администратор успешно удален!");
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный формат ID. Используйте: `/admin_remove [числовой ID]`");
            }
        } else {
            sendMessage(chatId, "❌ Неверный формат. Используйте: `/admin_remove [ID пользователя]`");
        }
    }

    private void handleBonusAddCommand(Long chatId, String command, Long adminId) {
        String[] parts = command.split(" ");
        if (parts.length == 3) {
            try {
                Long userChatId = Long.parseLong(parts[1]);
                int points = Integer.parseInt(parts[2]);
                adminService.addBonusPoints(userChatId, points, adminId);
                sendMessage(chatId, "✅ Бонусы успешно добавлены пользователю!");
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный формат. Используйте: `/bonus_add [ID пользователя] [количество баллов]`");
            }
        } else {
            sendMessage(chatId, "❌ Неверный формат. Используйте: `/bonus_add [ID пользователя] [количество баллов]`");
        }
    }

    private void handleBonusRemoveCommand(Long chatId, String command, Long adminId) {
        String[] parts = command.split(" ");
        if (parts.length == 3) {
            try {
                Long userChatId = Long.parseLong(parts[1]);
                int points = Integer.parseInt(parts[2]);
                adminService.removeBonusPoints(userChatId, points, adminId);
                sendMessage(chatId, "✅ Бонусы успешно сняты у пользователя!");
            } catch (NumberFormatException e) {
                sendMessage(chatId, "❌ Неверный формат. Используйте: `/bonus_remove [ID пользователя] [количество баллов]`");
            }
        } else {
            sendMessage(chatId, "❌ Неверный формат. Используйте: `/bonus_remove [ID пользователя] [количество баллов]`");
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

    private void sendWelcomeMessage(Long chatId, boolean isAdmin) {
        String welcomeText;
        ReplyKeyboardMarkup keyboard;

        if (isAdmin) {
            welcomeText = "👑 Добро пожаловать, администратор!\n\nВыберите действие:";
            keyboard = createAdminKeyboard();
        } else {
            welcomeText = "🎯 Добро пожаловать в Paintball Club Bot!\n\nВыберите действие:";
            keyboard = createMainKeyboard();
        }

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(welcomeText);
        message.setReplyMarkup(keyboard);

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

    private void handleGameRequest(Long chatId, String username) {
        if (userService.hasPendingRequest(chatId)) {
            sendMessage(chatId, "⏳ У вас уже есть активный запрос на добавление игры. " +
                    "Дождитесь ответа администратора перед отправкой нового запроса.");
            return;
        }

        try {
            Long requestId = adminService.createGameRequest(chatId, username);

            if (requestId != null) {
                userService.setPendingRequestStatus(chatId, true);

                sendMessage(chatId, "✅ Запрос на добавление игры отправлен администраторам. " +
                        "Ожидайте подтверждения.\n\n" +
                        "📋 Вы не можете отправлять новые запросы до обработки текущего.");

                notifyAdminsAboutGameRequest(requestId, chatId, username);
            } else {
                sendMessage(chatId, "❌ Ошибка при создании запроса. Попробуйте позже.");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка в handleGameRequest: " + e.getMessage());
            sendMessage(chatId, "❌ Произошла ошибка. Попробуйте позже.");
        }
    }

    private void notifyAdminsAboutGameRequest(Long requestId, Long userChatId, String username) {
        List<Long> adminIds = adminService.getAllAdminIds();

        String message = String.format(
                "🎮 Новый запрос на добавление игры\n\n" +
                        "ID запроса: %d\n" +
                        "Пользователь: @%s\n" +
                        "Chat ID: %d\n" +
                        "Время: %s\n\n" +
                        "Подтвердить добавление игры?",
                requestId, username, userChatId,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        );

        for (Long adminId : adminIds) {
            SendMessage adminMsg = new SendMessage();
            adminMsg.setChatId(adminId.toString());
            adminMsg.setText(message);
            adminMsg.setReplyMarkup(createAdminApproveKeyboard(requestId));

            try {
                execute(adminMsg);
            } catch (TelegramApiException e) {
                System.err.println("❌ Ошибка при отправке уведомления администратору " + adminId + ": " + e.getMessage());
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
        Integer messageId = callbackQuery.getMessage().getMessageId();
        Long chatId = callbackQuery.getMessage().getChatId();

        try {
            if (data.startsWith("approve_")) {
                Long requestId = Long.parseLong(data.split("_")[1]);
                adminService.approveGameRequest(requestId, adminId);

                Long userChatId = adminService.getUserChatIdFromRequest(requestId);
                if (userChatId != null) {
                    sendMessage(userChatId, "✅ Ваша игра подтверждена администратором! +10 бонусов");
                    sendMessage(userChatId, userService.getUserStats(userChatId));
                }

                editAdminMessage(chatId, messageId, "✅ Запрос одобрен", requestId);

                answerCallbackQuery(callbackId, "✅ Игра подтверждена");

            } else if (data.startsWith("reject_")) {
                Long requestId = Long.parseLong(data.split("_")[1]);
                adminService.rejectGameRequest(requestId, adminId);

                Long userChatId = adminService.getUserChatIdFromRequest(requestId);
                if (userChatId != null) {
                    sendMessage(userChatId, "❌ Ваш запрос на игру отклонен администратором.");
                }

                editAdminMessage(chatId, messageId, "❌ Запрос отклонен", requestId);

                answerCallbackQuery(callbackId, "❌ Игра отклонена");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки callback: " + e.getMessage());
            answerCallbackQuery(callbackId, "❌ Произошла ошибка");
        }
    }

    private void editAdminMessage(Long chatId, Integer messageId, String newText, Long requestId) {
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(messageId);

            String originalText = getOriginalMessageText(requestId);
            editMessage.setText(newText + "\n\n" + originalText);

            editMessage.setReplyMarkup(null);

            execute(editMessage);

        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка при редактировании сообщения: " + e.getMessage());
        }
    }

    private String getOriginalMessageText(Long requestId) {
        return "ID запроса: " + requestId + "\nВремя: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
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