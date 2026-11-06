package com.goaltracker.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.goaltracker.ai.GoalAnalysis;
import com.goaltracker.ai.GoalAnalyzer;
import com.goaltracker.api.ApiException;
import com.goaltracker.domain.Goal;
import com.goaltracker.domain.Task;
import com.goaltracker.dto.GoalAnalysisResponse;
import com.goaltracker.dto.GoalCreateRequest;
import com.goaltracker.dto.GoalResponse;
import com.goaltracker.dto.GoalUpdateRequest;
import com.goaltracker.i18n.Messages;
import com.goaltracker.repository.GoalRepository;
import com.goaltracker.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final TaskRepository taskRepository;
    private final GoalAnalyzer goalAnalyzer;
    private final Messages messages;

    public GoalService(
            GoalRepository goalRepository,
            TaskRepository taskRepository,
            GoalAnalyzer goalAnalyzer,
            Messages messages
    ) {
        this.goalRepository = goalRepository;
        this.taskRepository = taskRepository;
        this.goalAnalyzer = goalAnalyzer;
        this.messages = messages;
    }

    public List<GoalResponse> list(long userId) {
        List<Goal> goals = goalRepository.findByUserId(userId);
        Map<Long, List<Task>> tasksByGoal = taskRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(Task::goalId));

        return goals.stream()
                .map(goal -> GoalResponse.from(
                        goal,
                        tasksByGoal.getOrDefault(goal.id(), List.of())
                ))
                .toList();
    }

    @Transactional
    public GoalResponse create(long userId, GoalCreateRequest request, String language) {
        if (request == null || request.title() == null) {
            throw invalidRequestBody(language);
        }

        long goalId = goalRepository.create(userId, request.title(), request.description());
        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> goalNotFound(language));
        return GoalResponse.from(goal, List.of());
    }

    @Transactional
    public GoalResponse update(
            long goalId,
            long userId,
            GoalUpdateRequest request,
            String language
    ) {
        Goal goal = requireOwnedGoal(goalId, userId, language);
        if (request == null) {
            throw invalidRequestBody(language);
        }

        String title = request.title() == null ? goal.title() : request.title();
        String description = request.description() == null
                ? goal.description()
                : request.description();

        goalRepository.update(goalId, title, description);
        Goal updated = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> goalNotFound(language));
        return GoalResponse.from(updated, taskRepository.findByGoalId(goalId));
    }

    @Transactional
    public GoalResponse delete(long goalId, long userId, String language) {
        Goal goal = requireOwnedGoal(goalId, userId, language);
        List<Task> tasks = taskRepository.findByGoalId(goalId);

        taskRepository.deleteByGoalId(goalId);
        goalRepository.delete(goalId);

        return GoalResponse.from(goal, tasks);
    }

    public GoalAnalysisResponse analyze(long goalId, long userId, String language) {
        Goal goal = requireOwnedGoal(goalId, userId, language);
        GoalAnalysis analysis = goalAnalyzer.analyze(goal, language);
        return new GoalAnalysisResponse(analysis.analysis(), analysis.proposedTasks());
    }

    private Goal requireOwnedGoal(long goalId, long userId, String language) {
        return goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> goalNotFound(language));
    }

    private ApiException goalNotFound(String language) {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                messages.t("goal_not_found", language)
        );
    }

    private ApiException invalidRequestBody(String language) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                messages.t("invalid_request_body", language)
        );
    }
}
