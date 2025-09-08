package org.altmir.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import java.sql.Connection;
import java.sql.DriverManager;

public class LiquibaseMigration {

    public static void runMigrations() {
        String url = "jdbc:sqlite:paintball_bot.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(),
                    database
            );

            liquibase.update();
            System.out.println("✅Миграции успешно применены");

        } catch (Exception e) {
            System.err.println("❌Ошибка при применении миграций: " + e.getMessage());
            e.printStackTrace();
        }
    }
}