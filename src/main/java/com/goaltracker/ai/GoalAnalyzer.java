package com.goaltracker.ai;

import com.goaltracker.domain.Goal;

/**
 * Turns a goal into a short analysis and a list of proposed tasks.
 */
public interface GoalAnalyzer {

    GoalAnalysis analyze(Goal goal, String language);
}
