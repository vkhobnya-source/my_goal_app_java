package com.goaltracker.service;

import java.util.List;

import com.goaltracker.api.ApiException;
import com.goaltracker.domain.Task;
import com.goaltracker.dto.TaskCreateRequest;
import com.goaltracker.dto.TaskResponse;
import com.goaltracker.dto.TaskUpdateRequest;
import com.goaltracker.i18n.Messages;
import com.goaltracker.repository.GoalRepository;
import com.goaltracker.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final Messages messages;

    public TaskService(
            TaskRepository taskRepository,
            GoalRepository goalRepository,
            Messages messages
    ) {
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.messages = messages;
    }

    public List<TaskResponse> list(long goalId, long userId, String language) {
        requireOwnedGoal(goalId, userId, language);
        return taskRepository.findByGoalId(goalId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse create(
            long goalId,
            long userId,
            TaskCreateRequest request,
            String language
    ) {
        requireOwnedGoal(goalId, userId, language);
        if (request == null || request.title() == null) {
            throw invalidRequestBody(language);
        }

        long taskId = taskRepository.create(goalId, request.title());
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> taskNotFound(language));
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse update(
            long taskId,
            long userId,
            TaskUpdateRequest request,
            String language
    ) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> taskNotFound(language));
        requireTaskOwnership(task, userId, language);

        if (request == null) {
            throw invalidRequestBody(language);
        }

        String title = request.title() == null ? task.title() : request.title();
        boolean isCompleted = request.isCompleted() == null
                ? task.isCompleted()
                : request.isCompleted();

        taskRepository.update(taskId, title, isCompleted);
        Task updated = taskRepository.findById(taskId)
                .orElseThrow(() -> taskNotFound(language));
        return TaskResponse.from(updated);
    }

    @Transactional
    public TaskResponse delete(long taskId, long userId, String language) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> taskNotFound(language));
        requireTaskOwnership(task, userId, language);

        taskRepository.delete(taskId);
        return TaskResponse.from(task);
    }

    private void requireOwnedGoal(long goalId, long userId, String language) {
        if (goalRepository.findByIdAndUserId(goalId, userId).isEmpty()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    messages.t("goal_not_found", language)
            );
        }
    }

    private void requireTaskOwnership(Task task, long userId, String language) {
        if (goalRepository.findByIdAndUserId(task.goalId(), userId).isEmpty()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    messages.t("not_authorized", language)
            );
        }
    }

    private ApiException taskNotFound(String language) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                messages.t("task_not_found", language)
        );
    }

    private ApiException invalidRequestBody(String language) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                messages.t("invalid_request_body", language)
        );
    }
}
