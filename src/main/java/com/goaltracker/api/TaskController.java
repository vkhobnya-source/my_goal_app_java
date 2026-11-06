package com.goaltracker.api;

import java.util.List;

import com.goaltracker.config.CurrentUser;
import com.goaltracker.domain.User;
import com.goaltracker.dto.TaskCreateRequest;
import com.goaltracker.dto.TaskResponse;
import com.goaltracker.dto.TaskUpdateRequest;
import com.goaltracker.i18n.Messages;
import com.goaltracker.service.TaskService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService taskService;
    private final Messages messages;

    public TaskController(TaskService taskService, Messages messages) {
        this.taskService = taskService;
        this.messages = messages;
    }

    @GetMapping("/goals/{goalId}/tasks")
    public List<TaskResponse> list(
            @CurrentUser User user,
            @PathVariable long goalId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return taskService.list(
                goalId,
                user.id(),
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @PostMapping("/goals/{goalId}/tasks")
    public TaskResponse create(
            @CurrentUser User user,
            @PathVariable long goalId,
            @RequestBody TaskCreateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return taskService.create(
                goalId,
                user.id(),
                request,
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @RequestMapping(value = "/tasks/{taskId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    public TaskResponse update(
            @CurrentUser User user,
            @PathVariable long taskId,
            @RequestBody TaskUpdateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return taskService.update(
                taskId,
                user.id(),
                request,
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @DeleteMapping("/tasks/{taskId}")
    public TaskResponse delete(
            @CurrentUser User user,
            @PathVariable long taskId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return taskService.delete(
                taskId,
                user.id(),
                messages.resolveLanguage(acceptLanguage)
        );
    }
}
