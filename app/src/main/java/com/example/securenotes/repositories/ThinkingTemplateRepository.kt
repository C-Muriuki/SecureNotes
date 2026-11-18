package com.example.securenotes.repositories

import com.example.securenotes.models.ThinkingFramework
import com.example.securenotes.models.ThinkingStep

object ThinkingTemplateRepository {
    const val FRAMEWORK_FIVE_WHYS = "FIVE_WHYS"
    const val FRAMEWORK_SWOT = "SWOT"
    const val FRAMEWORK_PROS_CONS = "PROS_CONS"
    const val FRAMEWORK_PROBLEM_SOLUTION = "PROBLEM_SOLUTION"
    const val FRAMEWORK_IDEA_EXPANSION = "IDEA_EXPANSION"

    private val fiveWhys = ThinkingFramework(
        id = FRAMEWORK_FIVE_WHYS,
        title = "Five Whys",
        description = "Identify the underlying root cause of a problem through iterative questioning.",
        steps = listOf(
            ThinkingStep("WHY_1", "What is the main problem?", "Describe the problem or situation clearly."),
            ThinkingStep("WHY_2", "Why does this problem happen?", "Identify the immediate cause."),
            ThinkingStep("WHY_3", "Why does that situation occur?", "Look one level deeper into the underlying reason."),
            ThinkingStep("WHY_4", "What causes that underlying condition?", "Identify systemic or structural factors."),
            ThinkingStep("WHY_5", "What could address the root cause?", "Propose a fundamental action to fix the root cause.")
        )
    )

    private val swot = ThinkingFramework(
        id = FRAMEWORK_SWOT,
        title = "SWOT Analysis",
        description = "Examine an idea or project from four critical strategic perspectives.",
        steps = listOf(
            ThinkingStep("SWOT_STRENGTHS", "Strengths", "What internal advantages, assets, or positive qualities exist?"),
            ThinkingStep("SWOT_WEAKNESSES", "Weaknesses", "What internal limitations, gaps, or vulnerabilities exist?"),
            ThinkingStep("SWOT_OPPORTUNITIES", "Opportunities", "What external opportunities or trends can be leveraged?"),
            ThinkingStep("SWOT_THREATS", "Threats", "What external risks or obstacles could impede success?")
        )
    )

    private val prosCons = ThinkingFramework(
        id = FRAMEWORK_PROS_CONS,
        title = "Pros and Cons",
        description = "Evaluate a decision by balancing advantages, risks, and potential outcomes.",
        steps = listOf(
            ThinkingStep("PROS", "What are the advantages?", "List the key benefits and positive features."),
            ThinkingStep("CONS", "What are the disadvantages?", "List potential drawbacks, costs, or downsides."),
            ThinkingStep("RISKS", "What could go wrong?", "Consider risks and failure scenarios."),
            ThinkingStep("BENEFITS", "What benefits could result?", "Consider long-term positive outcomes."),
            ThinkingStep("DECISION", "What is the most reasonable decision?", "Summarize your final evaluation and conclusion.")
        )
    )

    private val problemSolution = ThinkingFramework(
        id = FRAMEWORK_PROBLEM_SOLUTION,
        title = "Problem and Solution",
        description = "Transform a complex problem into a structured, actionable solution.",
        steps = listOf(
            ThinkingStep("PROB_SOL_1", "Define the problem", "Clearly articulate the core issue to resolve."),
            ThinkingStep("PROB_SOL_2", "Identify affected people or systems", "Who or what is impacted by this problem?"),
            ThinkingStep("PROB_SOL_3", "Identify possible causes", "What factors contribute to the problem?"),
            ThinkingStep("PROB_SOL_4", "Explore possible solutions", "Brainstorm multiple potential approaches."),
            ThinkingStep("PROB_SOL_5", "Compare the solutions", "Evaluate trade-offs between options."),
            ThinkingStep("PROB_SOL_6", "Select a preferred approach", "Choose the most viable solution."),
            ThinkingStep("PROB_SOL_7", "Define the next action", "What is the immediate next step?")
        )
    )

    private val ideaExpansion = ThinkingFramework(
        id = FRAMEWORK_IDEA_EXPANSION,
        title = "Idea Expansion",
        description = "Develop incomplete thoughts into fully-fleshed actionable plans.",
        steps = listOf(
            ThinkingStep("EXPAND_1", "What is the main idea?", "Summarize the core concept clearly."),
            ThinkingStep("EXPAND_2", "What problem does it address?", "Why is this idea useful or valuable?"),
            ThinkingStep("EXPAND_3", "Who could benefit from it?", "Identify your target audience or beneficiaries."),
            ThinkingStep("EXPAND_4", "What resources would be required?", "List tools, time, or assistance needed."),
            ThinkingStep("EXPAND_5", "What challenges could arise?", "Anticipate friction or technical hurdles."),
            ThinkingStep("EXPAND_6", "What could make the idea better?", "Iterate and refine the initial concept."),
            ThinkingStep("EXPAND_7", "What is the first practical step?", "Define an actionable starting point.")
        )
    )

    private val frameworks = listOf(fiveWhys, swot, prosCons, problemSolution, ideaExpansion)

    fun getFrameworks(): List<ThinkingFramework> = frameworks

    fun getFramework(id: String): ThinkingFramework? =
        frameworks.find { it.id.equals(id, ignoreCase = true) }
}
