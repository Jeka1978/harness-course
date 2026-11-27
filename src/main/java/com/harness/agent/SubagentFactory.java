package com.harness.agent;

import com.harness.tool.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SubagentFactory {
    private final ObjectProvider<ToolRegistry> registryProvider;
    private final ToolContext mainContext;
    private final int contextWindow;
    private final ObjectProvider<Agent.Builder> agentBuilder;

    static final String SUBAGENT_PROMPT = """
            You are a code search specialist working inside a repository. You explore
            and report; you do not change anything.

            You can search and read files. You have no way to edit files, run commands
            or launch other agents — do not plan around abilities you do not have.

            How to work:
            - Use Glob to find files by name, Grep to search their contents
            - Read a file only when you actually need what is inside it
            - Prefer several targeted searches over reading everything

            Your report is the only thing the caller will ever see. They cannot read your
            history and cannot ask follow-up questions. So: answer the question that was
            asked, in full, with concrete file paths and names. State plainly what you
            could not find. Do not describe how you searched.""";


    public SubagentFactory(ObjectProvider<Agent.Builder> agentBuilder, ObjectProvider<ToolRegistry> registryProvider,
                           ToolContext mainContext, @Value("${spring.ai.ollama.chat.num-ctx}") int contextWindow) {
        this.agentBuilder = agentBuilder;
        this.registryProvider = registryProvider;
        this.mainContext = mainContext;
        this.contextWindow = contextWindow;
    }

    static boolean allowedForSubagent(Tool<?> tool) {
        return tool.isReadOnly() && !tool.name().equals(AgentTool.NAME) && !tool.name().equals("TodoWrite");
    }

    public Agent create() {
        TodoStore todos = new TodoStore(contextWindow);
        ToolContext context = new ToolContext(new FileStateCache(), mainContext.ordersKeeper(), todos);
        return agentBuilder.getObject()
                .tools(registryProvider.getObject().subset(SubagentFactory::allowedForSubagent, context))
                .systemPrompt(SUBAGENT_PROMPT)
                .todos(todos)
                .agentId(UUID.randomUUID().toString())
                .build();
    }
}
