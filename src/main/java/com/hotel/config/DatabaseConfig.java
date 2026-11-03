package com.hotel.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Production-ready Database configuration supporting PostgreSQL as the primary database,
 * with cloud platform compatibility (Railway, Render, Docker via DATABASE_URL)
 * and an automatic local development fallback to embedded H2 (PostgreSQL compatibility mode)
 * if the local PostgreSQL server service is currently stopped or unavailable.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${DATABASE_URL:#{null}}")
    private String envDatabaseUrl;

    @Value("${POSTGRES_URL:#{null}}")
    private String envPostgresUrl;

    @Value("${POSTGRES_PRISMA_URL:#{null}}")
    private String envPostgresPrismaUrl;

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/luxury_hotel_db}")
    private String postgresUrl;

    @Value("${spring.datasource.username:postgres}")
    private String postgresUsername;

    @Value("${spring.datasource.password:postgres}")
    private String postgresPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String postgresDriver;

    @Bean
    @Primary
    public DataSource dataSource() {
        String effectiveUrl = postgresUrl;
        String effectiveUsername = postgresUsername;
        String effectivePassword = postgresPassword;

        // Support cloud provider database URLs (Vercel Postgres, Railway, Render, Supabase, Neon)
        String cloudDbUrl = (envDatabaseUrl != null && !envDatabaseUrl.isBlank()) ? envDatabaseUrl :
                (envPostgresUrl != null && !envPostgresUrl.isBlank()) ? envPostgresUrl : envPostgresPrismaUrl;

        if (cloudDbUrl != null && !cloudDbUrl.isBlank()) {
            try {
                if (cloudDbUrl.startsWith("postgres://") || cloudDbUrl.startsWith("postgresql://")) {
                    URI dbUri = new URI(cloudDbUrl);
                    String userInfo = dbUri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        effectiveUsername = parts[0];
                        effectivePassword = parts[1];
                    }
                    String host = dbUri.getHost();
                    int port = dbUri.getPort() > 0 ? dbUri.getPort() : 5432;
                    String path = dbUri.getPath();
                    effectiveUrl = "jdbc:postgresql://" + host + ":" + port + path;
                    if (dbUri.getQuery() != null && !dbUri.getQuery().isBlank()) {
                        effectiveUrl += "?" + dbUri.getQuery();
                    }
                } else if (cloudDbUrl.startsWith("jdbc:")) {
                    effectiveUrl = cloudDbUrl;
                }
                log.info("Detected cloud Database URL, configured JDBC URL: {}", sanitizeUrl(effectiveUrl));
            } catch (Exception e) {
                log.warn("Could not parse cloud Database URL environment variable: {}. Using default datasource configuration.", e.getMessage());
            }
        }

        boolean isRemote = !effectiveUrl.contains("localhost") && !effectiveUrl.contains("127.0.0.1");
        boolean postgresAvailable = false;

        if (isRemote) {
            // For remote cloud PostgreSQL, connect directly
            postgresAvailable = true;
        } else {
            postgresAvailable = isPortAvailable("localhost", 5432, 1000);
        }

        if (postgresAvailable) {
            try {
                log.info(">>> Connecting to PostgreSQL Database ({}) <<<", sanitizeUrl(effectiveUrl));
                HikariDataSource ds = new HikariDataSource();
                ds.setDriverClassName(postgresDriver);
                ds.setJdbcUrl(effectiveUrl);
                ds.setUsername(effectiveUsername);
                ds.setPassword(effectivePassword);
                ds.setMaximumPoolSize(10);
                ds.setConnectionTimeout(15000);
                
                // Quick verification of connection
                ds.getConnection().close();
                log.info(">>> Successfully verified PostgreSQL connection <<<");
                return ds;
            } catch (Exception e) {
                log.warn("PostgreSQL connection attempt failed: {}. Falling back to in-memory database.", e.getMessage());
            }
        }

        log.warn("=========================================================================");
        log.warn(">>> PostgreSQL is not currently running or reachable on localhost:5432. <<<");
        log.warn(">>> Using Embedded In-Memory Database (PostgreSQL mode) for local dev.  <<<");
        log.warn(">>> To connect to PostgreSQL: configure DATABASE_URL or start service.  <<<");
        log.warn("=========================================================================");

        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setJdbcUrl("jdbc:h2:mem:luxury_hotel_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        ds.setUsername("sa");
        ds.setPassword("");
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        return ds;
    }

    private boolean isPortAvailable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        return url.replaceAll(":[^/@:]+@", ":***@");
    }
}
