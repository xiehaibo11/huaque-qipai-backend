package com.nanbei.entertainment.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PaymentProductRepairMigrationIT {
    private static final Map<String, Long> EXPECTED_PRODUCTS =
            Map.of(
                    "SXVIP_CONTINUOUS_MONTH", 2_800L,
                    "SXVIP_30_DAYS", 3_500L,
                    "SXVIP_90_DAYS", 7_800L,
                    "SXVIP_365_DAYS", 26_800L,
                    "SXVIP_7_DAYS", 2_500L);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void restoresAllMembershipPaymentProductsAfterVersionFourteen()
            throws Exception {
        flyway("14").migrate();
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "update payment_products set enabled = false "
                            + "where product_code like 'SXVIP_%'");
        }

        flyway(null).migrate();

        Map<String, Long> enabledProducts = new LinkedHashMap<>();
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "select product_code, amount_minor "
                                        + "from payment_products "
                                        + "where enabled = true "
                                        + "and currency = 'CNY' "
                                        + "and product_code like 'SXVIP_%'")) {
            while (rows.next()) {
                enabledProducts.put(rows.getString(1), rows.getLong(2));
            }
        }
        assertThat(enabledProducts)
                .containsExactlyInAnyOrderEntriesOf(EXPECTED_PRODUCTS);
    }

    private static Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection connection() throws Exception {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }
}
