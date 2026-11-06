package com.goaltracker.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import com.goaltracker.domain.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {

    private static final RowMapper<Task> ROW_MAPPER = (resultSet, rowNumber) -> new Task(
            resultSet.getLong("id"),
            resultSet.getString("title"),
            resultSet.getBoolean("is_completed"),
            resultSet.getLong("goal_id")
    );

    private final JdbcTemplate jdbcTemplate;

    public TaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Task> findByGoalId(long goalId) {
        return jdbcTemplate.query(
                "SELECT id, title, is_completed, goal_id FROM tasks "
                        + "WHERE goal_id = ? ORDER BY id",
                ROW_MAPPER,
                goalId
        );
    }

    /**
     * All tasks of all goals owned by one user. The goals list needs tasks for
     * the progress bars, so fetching them with a join avoids a query per goal.
     */
    public List<Task> findByUserId(long userId) {
        return jdbcTemplate.query(
                "SELECT t.id, t.title, t.is_completed, t.goal_id "
                        + "FROM tasks t JOIN goals g ON g.id = t.goal_id "
                        + "WHERE g.user_id = ? ORDER BY t.id",
                ROW_MAPPER,
                userId
        );
    }

    public Optional<Task> findById(long taskId) {
        List<Task> tasks = jdbcTemplate.query(
                "SELECT id, title, is_completed, goal_id FROM tasks WHERE id = ?",
                ROW_MAPPER,
                taskId
        );
        return tasks.stream().findFirst();
    }

    public long create(long goalId, String title) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tasks (title, is_completed, goal_id) VALUES (?, 0, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setLong(2, goalId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("SQLite did not return the new task id");
        }
        return key.longValue();
    }

    public void update(long taskId, String title, boolean isCompleted) {
        jdbcTemplate.update(
                "UPDATE tasks SET title = ?, is_completed = ? WHERE id = ?",
                title,
                isCompleted,
                taskId
        );
    }

    public void delete(long taskId) {
        jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", taskId);
    }

    public void deleteByGoalId(long goalId) {
        jdbcTemplate.update("DELETE FROM tasks WHERE goal_id = ?", goalId);
    }
}
