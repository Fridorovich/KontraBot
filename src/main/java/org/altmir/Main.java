package org.altmir;

import org.altmir.db.DatabaseManager;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            DatabaseManager dbManager = new DatabaseManager();

            //Long firstAdminId = 1070503623L;
            //dbManager.addAdmin(firstAdminId, "EN", firstAdminId);

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new PaintballBot("Контра - Первоуральск", "8432592072:AAGeUEXPJ52UnEJJpCLuNqOhQa8CZSZ14f0"));

            System.out.println("✅ Бот запущен и готов к работе!");

        } catch (TelegramApiException e) {
            System.err.println("❌ Ошибка запуска бота: " + e.getMessage());
            e.printStackTrace();
        }
    }
}