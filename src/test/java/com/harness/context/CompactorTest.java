package com.harness.context;

import com.harness.agent.Conversation;
import com.harness.agent.ScriptedChatModel;
import com.harness.agent.event.AgentEvent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactorTest {

    private final List<Object> events = new ArrayList<>();
    private final ScriptedChatModel model = ScriptedChatModel.replyingWith("пересказ");
    private final Compactor compactor = new Compactor(model, events::add, 1_000);

    @Test
    void microcompactClearsOldResultsAndKeepsRecent() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addUserMessage("расследуй");
        conversation.addToolResponses(results(new ToolResponse("1", "Read", "журнал ".repeat(500))));
        addRecentResults(conversation);
        int before = conversation.tokens();

        compactor.microcompact(conversation);

        assertThat(contents(conversation)).containsExactly(Compactor.CLEARED,
                "отчёт", "учёт", "сборки", "incident/team-calendar.md", "календарь");
        assertThat(conversation.tokens()).isLessThan(before);
        assertThat(events).containsExactly(new AgentEvent.ContextChanged("микрокомпакт"));
    }

    @Test
    void microcompactLeavesPlanAlone() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addToolResponses(results(new ToolResponse("1", "TodoWrite", "план обновлён ".repeat(50))));
        addRecentResults(conversation);

        compactor.microcompact(conversation);

        assertThat(contents(conversation)).first().isEqualTo("план обновлён ".repeat(50));
        assertThat(events).isEmpty();
    }

    @Test
    void microcompactDoesNotClearTwice() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addToolResponses(results(new ToolResponse("1", "Read", "журнал ".repeat(500))));
        addRecentResults(conversation);

        compactor.microcompact(conversation);
        compactor.microcompact(conversation);

        assertThat(events).hasSize(1);
    }

    @Test
    void microcompactKeepsResultShorterThanStub() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addToolResponses(results(new ToolResponse("1", "Glob", "hr/x.md")));
        addRecentResults(conversation);

        compactor.microcompact(conversation);

        assertThat(contents(conversation)).first().isEqualTo("hr/x.md");
        assertThat(events).isEmpty();
    }

    @Test
    void autocompactWaitsForThreshold() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addUserMessage("карточки подозреваемых");

        compactor.autocompact(conversation);

        assertThat(model.callCount()).isZero();
        assertThat(conversation.toPromptMessages()).hasSize(2);
        assertThat(events).isEmpty();
    }

    @Test
    void autocompactReplacesHistoryWithSummary() {
        Conversation conversation = new Conversation("SYSTEM");
        conversation.addUserMessage("карточки ".repeat(250));
        int before = conversation.tokens();

        compactor.autocompact(conversation);

        List<Message> request = model.lastPrompt().getInstructions();
        assertThat(request.get(1).getText()).startsWith("карточки");
        assertThat(request.getLast().getText()).isEqualTo(Compactor.SUMMARY_REQUEST);
        assertThat(conversation.toPromptMessages())
                .extracting(Message::getText)
                .containsExactly("SYSTEM", Compactor.SUMMARY_HEADER + "пересказ");
        assertThat(conversation.tokens()).isLessThan(before);
        assertThat(events).containsExactly(new AgentEvent.ContextChanged("автокомпакт"));
    }

    /** Столько свежих ответов, сколько микрокомпакт не трогает. */
    private static void addRecentResults(Conversation conversation) {
        conversation.addToolResponses(results(new ToolResponse("r1", "Read", "отчёт")));
        conversation.addToolResponses(results(new ToolResponse("r2", "Read", "учёт")));
        conversation.addToolResponses(results(new ToolResponse("r3", "Read", "сборки")));
        conversation.addToolResponses(results(new ToolResponse("r4", "Glob", "incident/team-calendar.md")));
        conversation.addToolResponses(results(new ToolResponse("r5", "Read", "календарь")));
    }

    private static ToolResponseMessage results(ToolResponse... responses) {
        return ToolResponseMessage.builder().responses(List.of(responses)).build();
    }

    private static List<String> contents(Conversation conversation) {
        return conversation.toPromptMessages().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .flatMap(message -> ((ToolResponseMessage) message).getResponses().stream())
                .map(ToolResponse::responseData)
                .toList();
    }
}
