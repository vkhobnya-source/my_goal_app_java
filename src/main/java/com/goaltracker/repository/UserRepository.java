package com.goaltracker.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import com.goaltracker.domain.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<User> ROW_MAPPER = (resultSet, rowNumber) -> new User(
            resultSet.getLong("id"),
            resultSet.getString("email"),
            resultSet.getString("hashed_password")
    );

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<User> findByEmail(String email) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, email, hashed_password FROM users WHERE email = ?",
                ROW_MAPPER,
                email
        );
        return users.stream().findFirst();
    }

    public Optional<User> findById(long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, email, hashed_password FROM users WHERE id = ?",
                ROW_MAPPER,
                id
        );
        return users.stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    public long create(String email, String hashedPassword) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (email, hashed_password) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, email);
            statement.setString(2, hashedPassword);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("SQLite did not return the new user id");
        }
        return key.longValue();
    }
}
