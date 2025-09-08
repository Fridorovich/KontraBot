package org.altmir.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingGame {
    private Long id;
    private Long userChatId;
    private String username;
    private LocalDateTime requestDate;
    private String status;
    private Long processedBy;
    private LocalDateTime processedDate;
}