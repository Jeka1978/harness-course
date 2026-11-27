package com.harness.agent;

import com.harness.TestPermissionConfig;
import com.harness.tool.Tool;
import com.harness.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Обещания субагента проверяются структурой: какие инструменты он получает и что у него своего.
 * Контекст Spring настоящий — второй агент собирается, несмотря на кольцо
 * {@code AgentTool → SubagentFactory → ToolRegistry → AgentTool}.
 */
@SpringBootTest
@Import(TestPermissionConfig.class)
@ActiveProfiles("test")
class AgentToolTest {

    @Autowired
    private SubagentFactory subagents;

    @Autowired
    private ToolRegistry registry;

    private List<String> subagentTools() {
        return registry.tools().stream().filter(SubagentFactory::allowedForSubagent).map(Tool::name).toList();
    }

    @Test
    void subagentOnlyReads() {
        assertThat(subagentTools())
                .contains("Read", "Glob", "Grep")
                .doesNotContain("Bash", "Edit", "Write");
    }

    @Test
    void subagentCannotCallSubagent() {
        assertThat(subagentTools()).doesNotContain("Task");
    }

    @Test
    void mainAgentCanCallSubagent() {
        assertThat(registry.tools()).extracting(Tool::name).contains("Task");
    }

    @Test
    void everySubagentGetsItsOwnConversation() {
        Agent first = subagents.create();
        Agent second = subagents.create();

        assertThat(first.conversation()).isNotSameAs(second.conversation());
    }
}
