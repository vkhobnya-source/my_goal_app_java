package com.goaltracker.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import com.goaltracker.domain.Goal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class GoalRepository {

    private static final RowMapper<Goal> ROW_MAPPER = (resultSet, rowNumber) -> new Goal(
            resultSet.getLong("id"),
            resultSet.getString("title"),
            resultSet.getString("description"),
            resultSet.getBoolean("is_completed"),
            resultSet.getLong("user_id")
    );

    private final JdbcTemplate jdbcTemplate;

    public GoalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Goal> findByUserId(long userId) {
        return jdbcTemplate.query(
                "SELECT id, title, description, is_completed, user_id "
                        + "FROM goals WHERE user_id = ? ORDER BY id",
                ROW_MAPPER,
                userId
        );
    }

    public Optional<Goal> findByIdAndUserId(long goalId, long userId) {
        List<Goal> goals = jdbcTemplate.query(
                "SELECT id, title, description, is_completed, user_id "
                        + "FROM goals WHERE id = ? AND user_id = ?",
                ROW_MAPPER,
                goalId,
                userId
        );
        return goals.stream().findFirst();
    }

    public long create(long userId, String title, String description) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO goals (title, description, is_completed, user_id) "
                            + "VALUES (?, ?, 0, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setLong(3, userId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("SQLite did not return the new goal id");
        }
        return key.longValue();
    }

    public void update(long goalId, String title, String description) {
        jdbcTemplate.update(
                "UPDATE goals SET title = ?, description = ? WHERE id = ?",
                title,
                description,
                goalId
        );
    }

    public void delete(long goalId) {
        jdbcTemplate.update("DELETE FROM goals WHERE id = ?", goalId);
    }
}
