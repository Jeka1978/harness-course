package com.harness.agent;

import com.harness.agent.event.AgentEvent;
import com.harness.tool.Tool;
import com.harness.tool.ToolContext;
import com.harness.tool.ToolResult;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AgentTool implements Tool<AgentTool.Input> {

    static final String NAME = "Task";

    private final SubagentFactory subagentFactory;
    private final ApplicationEventPublisher publisher;

    public AgentTool(SubagentFactory subagentFactory, ApplicationEventPublisher publisher) {
        this.subagentFactory = subagentFactory;
        this.publisher = publisher;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
                Launch a new agent to handle complex, multi-step tasks autonomously.

                The Task tool launches specialized agents that autonomously handle complex tasks. Each agent type has specific capabilities and tools available to it.

                Available agent types are listed in <system-reminder> messages in the conversation.

                When NOT to use the Task tool:
                - If you want to read a specific file path, use the Read tool or the Glob tool instead of the Task tool, to find the match more quickly
                - If you are searching for a specific class definition like "class Foo", use the Glob tool instead, to find the match more quickly
                - If you are searching for code within a specific file or set of 2-3 files, use the Read tool instead of the Task tool, to find the match more quickly

                Usage notes:
                - Always include a short description (3-5 words) summarizing what the agent will do
                - When the agent is done, it will return a single message back to you. The result returned by the agent is not visible to the user. To show the user the result, you should send a text message back to the user with a concise summary of the result.
                - Each Task invocation starts fresh — provide a complete task description.
                - The agent's outputs should generally be trusted
                - Clearly tell the agent whether you expect it to write code or just to do research (search, file reads), since it is not aware of the user's intent""";
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public String renderToolUseMessage(Input input) {
        return tag() + " " + input.description();
    }

    @Override
    public ToolResult call(Input input, ToolContext context) {
        publisher.publishEvent(new AgentEvent.SubagentStarted(input.description()));
        Instant startedAt = Instant.now();
        String report = subagentFactory.create().run(input.prompt());
        publisher.publishEvent(new AgentEvent.SubagentFinished(
                input.description(), report.length(), Duration.between(startedAt, Instant.now())));
        return ToolResult.ok(report + "\n\n" + AFTER_REPORT);

    }

    /** Хвост результата: «avoid duplicating work» оригинала, сказанный там, где модель решает следующий шаг. */
    static final String AFTER_REPORT = "This was the agent's report; the user cannot see it. Do not repeat the agent's"
            + " work yourself — trust the report and move on to the next step of the task.";

    static final String EXPLORE_LINE = "- Explore: Fast agent specialized for exploring codebases. Use this when you need"
            + " to quickly find files by patterns, search code for keywords, or answer questions about the codebase."
            + " (Tools: Read, Glob, Grep)";

    static final String LISTING_REMINDER = "Available agent types for the Task tool:\n" + EXPLORE_LINE
            + "\n\nLaunch multiple agents concurrently whenever possible, to maximize performance;"
            + " to do that, use a single message with multiple tool uses.";

    public record Input(
            @ToolParam(description = "A short (3-5 word) description of the task")
            String description,

            @ToolParam(description = "The task for the agent to perform")
            String prompt) {
    }
}
