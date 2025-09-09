package org.altmir.Service;

import org.altmir.dao.User;
import org.altmir.db.DatabaseManager;

import java.time.LocalDateTime;

public class UserService {
    private final DatabaseManager databaseManager;

    public UserService() {
        this.databaseManager = new DatabaseManager();
    }

    public User getOrCreateUser(Long chatId, String username) {
        User user = databaseManager.getUser(chatId);

        if (user == null) {
            user = new User(chatId, username, 0, 0, LocalDateTime.now(), false, false);
            databaseManager.saveUser(user);
        }

        return user;
    }

    public boolean hasAcceptedTerms(Long chatId) {
        User user = databaseManager.getUser(chatId);
        return user != null && user.isTermsAccepted();
    }

    public boolean hasPendingRequest(Long chatId) {
        return databaseManager.hasPendingRequest(chatId);
    }

    public void setPendingRequestStatus(Long chatId, boolean status) {
        databaseManager.setPendingRequestStatus(chatId, status);
    }

    public void acceptTerms(Long chatId) {
        databaseManager.updateUserTermsAccepted(chatId, true);
    }

    public void addGame(Long chatId) {
        databaseManager.addGame(chatId);
    }

    public void addBonusPoints(Long chatId, int points) {
        databaseManager.addBonusPoints(chatId, points);
    }

    public String getUserStats(Long chatId) {
        User user = databaseManager.getUser(chatId);
        if (user != null) {
            return String.format(
                    "🎯 Ваша статистика:\n\n" +
                            "🎮 Сыграно игр: %d\n" +
                            "⭐ Бонусные баллы: %d\n" +
                            "📅 Дата регистрации: %s\n\n" +
                            "💎 10 бонусов = 1 бесплатная игра",
                    user.getGamesPlayed(),
                    user.getBonusPoints(),
                    user.getRegistrationDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            );
        }
        return "❌ Пользователь не найден!";
    }

    public int getBonusPoints(Long chatId) {
        User user = databaseManager.getUser(chatId);
        return user != null ? user.getBonusPoints() : 0;
    }

    public int getGamesPlayed(Long chatId) {
        User user = databaseManager.getUser(chatId);
        return user != null ? user.getGamesPlayed() : 0;
    }
}