CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email VARCHAR UNIQUE,
    hashed_password VARCHAR
);

CREATE UNIQUE INDEX IF NOT EXISTS ix_users_email ON users (email);

CREATE TABLE IF NOT EXISTS goals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR,
    description VARCHAR,
    is_completed BOOLEAN NOT NULL DEFAULT 0,
    user_id INTEGER REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_goals_user_id ON goals (user_id);
CREATE INDEX IF NOT EXISTS ix_goals_title ON goals (title);

CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR,
    is_completed BOOLEAN NOT NULL DEFAULT 0,
    goal_id INTEGER REFERENCES goals (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_tasks_goal_id ON tasks (goal_id);
CREATE INDEX IF NOT EXISTS ix_tasks_title ON tasks (title);
