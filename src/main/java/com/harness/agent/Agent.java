package com.harness.agent;

import com.harness.agent.event.AgentEvent;
import com.harness.context.Compactor;
import com.harness.tool.SpringAiToolAdapter;
import com.harness.tool.TodoStore;
import com.harness.tool.ToolRegistry;
import com.harness.tool.ToolResult;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Agent {


    private final ApplicationEventPublisher publisher;

    private final Conversation conversation;
    private final ToolRegistry toolRegistry;
    private final SpringAiToolAdapter springAiToolAdapter;
    private final AgentProperties agentProperties;
    private final TodoStore todos;
    private final Compactor compactor;
    static final String BLOCK_NOTICE = """
            TOOL CALL BLOCKED — HARD RESTRICTION

            The previous tool call was blocked. This operation and all previously failed approaches are strictly forbidden.

            You MUST NOT retry any blocked or previously failed operation, including with identical, equivalent, or superficially modified arguments.

            You MAY continue the original task ONLY if you can identify a genuinely different, untried approach that could make meaningful progress.

            If no such approach exists, you MUST STOP immediately and clearly explain to the user why the task cannot be completed and what is blocking you.

            Under no circumstances attempt a blocked operation again.
            """;
    /** {@code null} у главного агента, непустой у субагента. */
    private final @Nullable String agentId;

    /** Список типов агентов передаётся один раз за разговор. */
    private boolean agentsListed;


    private Agent(Builder builder) {
        this.chatModel = builder.chatModel;
        this.publisher = builder.publisher;
        this.agentProperties = builder.properties;
        this.compactor = builder.compactor;
        this.toolRegistry = Objects.requireNonNull(builder.tools, "tools");
        this.springAiToolAdapter = new SpringAiToolAdapter(toolRegistry);
        this.conversation = new Conversation(Objects.requireNonNull(builder.systemPrompt, "systemPrompt"));
        this.todos = Objects.requireNonNull(builder.todos, "todos");
        this.agentId = builder.agentId;
    }


    public Conversation conversation() {
        return conversation;
    }

    private final ChatModel chatModel;


    @Nullable
    public String chatModel() {

        return chatModel.getOptions().getModel();
    }

    public String run(String userMessage) {
        Instant startTime = Instant.now();
        compactor.autocompact(conversation);
        conversation.addUserMessage(userMessage);
        if (isMain()) {
            if (!agentsListed && toolRegistry.tools().stream()
                    .anyMatch(tool -> tool.name().equals(AgentTool.NAME))) {
                conversation.addSystemReminder(AgentTool.LISTING_REMINDER);
                agentsListed = true;
            }
            todos.reminderAfterUserMessage().ifPresent(conversation::addSystemReminder);
            publisher.publishEvent(new AgentEvent.TurnStarted(userMessage));
        }
        contextChanged("реплика человека");
        RepeatedCallGuard repeatedCallGuard = new RepeatedCallGuard();
        for (int iteration = 1; iteration <= agentProperties.maxTurns(); iteration++) {
            compactor.microcompact(conversation);
            ChatResponse response = askModel();
            AssistantMessage assistantMessage = response.getResult().getOutput();
            conversation.addAssistantMessage(assistantMessage);
            conversation.countedByModel(response.getMetadata().getUsage());
            publisher.publishEvent(new AgentEvent.AgentSpoke(assistantMessage.getText()));
            contextChanged("ответ модели");
            if(!assistantMessage.hasToolCalls()){
                if (isMain()) {
                    publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now()), iteration));
                }
                return assistantMessage.getText();
            }
            executeAll(assistantMessage,repeatedCallGuard);
            contextChanged("ответы инструментов");
        }
        publisher.publishEvent(new AgentEvent.TurnLimitReached(agentProperties.maxTurns()));
        if (isMain()) {
            publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now()), agentProperties.maxTurns()));
        }
        return "Достигнут предел в %d итераций — задача не завершена.".formatted(agentProperties.maxTurns());

    }

    private ChatResponse askModel() {
        Prompt prompt = Prompt.builder()
                .messages(conversation.toPromptMessages())
                .chatOptions(toolOptions())
                .build();

        return chatModel.call(prompt);
    }

    private boolean isMain() {
        return agentId == null;
    }

    private void contextChanged(String cause) {
        if (isMain()) {
            publisher.publishEvent(new AgentEvent.ContextChanged(cause));
        }
    }

    private ToolResponseMessage executeAll(AssistantMessage assistantMessage, RepeatedCallGuard repeatedCallGuard) {
        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
        boolean anyBlocked = false;
        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
            ToolResult toolResult = repeatedCallGuard.execute(toolRegistry,toolCall,publisher);
            anyBlocked |= repeatedCallGuard.isBlocked();
            todos.countToolResult(toolCall.arguments(), toolResult);
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(),toolCall.name(),toolResult.content()));
        }
        conversation.addToolResponses(ToolResponseMessage.builder().responses(toolResponses).build());
        if(anyBlocked){
            conversation.addUserMessage(BLOCK_NOTICE);
        } else if (isMain()) {
            todos.reminderAfterToolResults().ifPresent(conversation::addSystemReminder);
        }

        return ToolResponseMessage.builder().responses(toolResponses).build();
    }

    private ChatOptions toolOptions() {
        return chatModel
                .getOptions()
                .mutate()
                .combineWith(
                        ToolCallingChatOptions
                                .builder()
                                .toolCallbacks((springAiToolAdapter.toolCallbacks()))
                )
                .build();
    }

    public static final class Builder {

        private final ChatModel chatModel;
        private final ApplicationEventPublisher publisher;
        private final AgentProperties properties;
        private final Compactor compactor;

        private @Nullable ToolRegistry tools;
        private @Nullable String systemPrompt;
        private @Nullable TodoStore todos;
        private @Nullable String agentId;

        Builder(ChatModel chatModel, ApplicationEventPublisher publisher, AgentProperties properties, Compactor compactor) {
            this.chatModel = chatModel;
            this.publisher = publisher;
            this.properties = properties;
            this.compactor = compactor;
        }

        public Builder tools(ToolRegistry tools) {
            this.tools = tools;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder todos(TodoStore todos) {
            this.todos = todos;
            return this;
        }

        /** Номер есть только у субагента: главного отличает как раз то, что номера у него нет. */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Agent build() {
            return new Agent(this);
        }
    }
}
