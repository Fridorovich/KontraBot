package org.altmir.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Admin {
    private Long userId;
    private String username;
    private Long addedBy;
    private LocalDateTime addedDate;
}