package org.altmir.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long chatId;
    private String username;
    private int gamesPlayed;
    private int bonusPoints;
    private LocalDateTime registrationDate;
    private boolean termsAccepted;
    private boolean hasPendingRequest;
}