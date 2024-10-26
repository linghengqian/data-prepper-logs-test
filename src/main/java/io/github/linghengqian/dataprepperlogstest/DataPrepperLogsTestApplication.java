package io.github.linghengqian.dataprepperlogstest;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.IntStream;

@SuppressWarnings("SqlNoDataSourceInspection")
@EnableScheduling
@SpringBootApplication
public class DataPrepperLogsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPrepperLogsTestApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(ServletWebServerApplicationContext applicationContext, JdbcClient jdbcClient,
                             TaskScheduler taskScheduler) {
        return _ -> {
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS t_order(
                    order_id BIGINT NOT NULL AUTO_INCREMENT,
                    order_type INT(11),
                    user_id INT NOT NULL,
                    address_id BIGINT NOT NULL,
                    status VARCHAR(50),
                    PRIMARY KEY (order_id))""").update();
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS t_order_item(
                    order_item_id BIGINT NOT NULL AUTO_INCREMENT,
                    order_id BIGINT NOT NULL,
                    user_id INT NOT NULL,
                    phone VARCHAR(50),
                    status VARCHAR(50),
                    PRIMARY KEY (order_item_id))""").update();
            jdbcClient.sql("""
                    CREATE TABLE IF NOT EXISTS t_address (
                    address_id BIGINT NOT NULL,
                    address_name VARCHAR(100) NOT NULL,
                    PRIMARY KEY (address_id))""").update();
            jdbcClient.sql("TRUNCATE TABLE t_order").update();
            jdbcClient.sql("TRUNCATE TABLE t_order_item").update();
            jdbcClient.sql("TRUNCATE TABLE t_address").update();
            IntStream.rangeClosed(1, 10).forEachOrdered(i -> {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                jdbcClient.sql("INSERT INTO t_order (user_id, order_type, address_id, status) VALUES (?, ?, ?, ?)")
                        .param(1, i)
                        .param(2, i % 2)
                        .param(3, i)
                        .param(4, "INSERT_TEST")
                        .update(keyHolder);
                Number orderIdKey = keyHolder.getKey();
                Objects.requireNonNull(orderIdKey);
                long orderId = orderIdKey.longValue();
                jdbcClient.sql("INSERT INTO t_order_item (order_id, user_id, phone, status) VALUES (?, ?, ?, ?)")
                        .param(1, orderId)
                        .param(2, i)
                        .param(3, "13800000001")
                        .param(4, "INSERT_TEST")
                        .update();
                jdbcClient.sql("INSERT INTO t_address (address_id, address_name) VALUES (?, ?)")
                        .param(1, (long) i)
                        .param(2, "address_test_" + i)
                        .update();
            });
            RestClient restClient = RestClient.create();
            int port = applicationContext.getWebServer().getPort();
            taskScheduler.scheduleAtFixedRate(() -> {
                restClient.get()
                        .uri("http://127.0.0.1:" + port + "/test_all")
                        .retrieve()
                        .body(Void.class);
                restClient.post()
                        .uri("http://127.0.0.1:" + port + "/test_transaction")
                        .retrieve()
                        .body(Void.class);
            }, Duration.of(1L, ChronoUnit.SECONDS));
        };
    }
}

record Order(long orderId, int orderType, int userId, long addressId, String status) {
}

record OrderItem(long orderItemId, long orderId, int userId, String phone, String status) {
}

record Address(Long addressId, String addressName) {
}

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
@RestController
class TestController {
    private final JdbcClient jdbcClient;

    private final TransactionTemplate transactionTemplate;

    public TestController(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/test_all")
    public void testAll() {
        jdbcClient.sql("SELECT * FROM t_order").query(Order.class).list();
        jdbcClient.sql("SELECT i.* FROM t_order o, t_order_item i WHERE o.order_id = i.order_id")
                .query(OrderItem.class)
                .list();
        jdbcClient.sql("SELECT * FROM t_address").query(Address.class).list();
    }

    @PostMapping("/test_transaction")
    public void testTransaction() {
        try {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(@NonNull TransactionStatus status) {
                    jdbcClient.sql("INSERT INTO t_order_item (order_id, user_id, phone, status) VALUES (2024, 2024, '13800000001', 'INSERT_TEST')")
                            .update();
                    jdbcClient.sql("INSERT INTO t_order_item_does_not_exist (test_id_does_not_exist) VALUES (2024)")
                            .update();
                }
            });
        } catch (Exception ignored) {
        }
        if (jdbcClient.sql("SELECT * FROM t_order_item WHERE user_id = 2024").query(OrderItem.class).optional().isPresent()) {
            throw new RuntimeException("Normally, this exception should not be thrown.");
        }
    }
}
