package com.goaltracker.api;

import java.util.List;

import com.goaltracker.config.CurrentUser;
import com.goaltracker.domain.User;
import com.goaltracker.dto.GoalAnalysisResponse;
import com.goaltracker.dto.GoalCreateRequest;
import com.goaltracker.dto.GoalResponse;
import com.goaltracker.dto.GoalUpdateRequest;
import com.goaltracker.i18n.Messages;
import com.goaltracker.service.GoalService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;
    private final Messages messages;

    public GoalController(GoalService goalService, Messages messages) {
        this.goalService = goalService;
        this.messages = messages;
    }

    @GetMapping
    public List<GoalResponse> list(@CurrentUser User user) {
        return goalService.list(user.id());
    }

    @PostMapping
    public GoalResponse create(
            @CurrentUser User user,
            @RequestBody GoalCreateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return goalService.create(
                user.id(),
                request,
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @PutMapping("/{goalId}")
    public GoalResponse update(
            @CurrentUser User user,
            @PathVariable long goalId,
            @RequestBody GoalUpdateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return goalService.update(
                goalId,
                user.id(),
                request,
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @DeleteMapping("/{goalId}")
    public GoalResponse delete(
            @CurrentUser User user,
            @PathVariable long goalId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return goalService.delete(
                goalId,
                user.id(),
                messages.resolveLanguage(acceptLanguage)
        );
    }

    @PostMapping("/{goalId}/analyze")
    public GoalAnalysisResponse analyze(
            @CurrentUser User user,
            @PathVariable long goalId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return goalService.analyze(
                goalId,
                user.id(),
                messages.resolveLanguage(acceptLanguage)
        );
    }
}
