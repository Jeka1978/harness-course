package com.harness.context;

import com.harness.agent.Conversation;
import com.harness.agent.event.AgentEvent;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class Compactor {

    public static final int KEEP_RECENT_RESULTS = 5;
    public static final String CLEARED = "[Old tool result content cleared]";
    static final int AUTOCOMPACT_PERCENT = 40;
    private static final Set<String> COMPACTABLE_TOOLS = Set.of("Read", "Bash", "Grep", "Glob", "Edit", "Write");

    private final ChatModel chatModel;
    private final ApplicationEventPublisher publisher;
    private final int autocompactThreshold;

    public Compactor(ChatModel chatModel, ApplicationEventPublisher publisher, @Value("${spring.ai.ollama.chat.num-ctx}") int contextWindow) {
        this.chatModel = chatModel;
        this.publisher = publisher;
        this.autocompactThreshold = contextWindow * AUTOCOMPACT_PERCENT / 100;
    }


    public void autocompact(Conversation conversation) {
        if (conversation.tokens() < autocompactThreshold) {
            return;
        }
        List<Message> request = new ArrayList<>(conversation.toPromptMessages());
        request.add(new UserMessage(SUMMARY_REQUEST));
        String summary = chatModel.call(new Prompt(request)).getResult().getOutput().getText();
        conversation.replaceWithSummary(SUMMARY_HEADER+summary);
        publisher.publishEvent(new AgentEvent.ContextChanged("автокомпакт"));

    }


    static final String SUMMARY_HEADER = """
            This session is being continued from a previous conversation that ran out of context.
            The summary below covers the earlier portion of the conversation.

            """;


    static final String SUMMARY_REQUEST = """
            Summarize the conversation above. This summary will REPLACE the conversation:
            everything you leave out is lost. Respond with plain text only, no tool calls.

            Cover, in this order:
            1. The user's request and intent, in their own words where possible.
            2. What you have already done, and what came of it.
            3. Files you read or changed, with paths.
            4. Errors you hit and how you resolved them.
            5. Facts you established that are expensive to establish again.
            6. All user messages, one by one, each copied in full, word for word. Never shorten
               one with "..." or a [placeholder]: names, numbers and paths the user gave you
               exist nowhere else.
            7. What remains unfinished.
            8. The exact next step.""";


    public void microcompact(Conversation conversation) {
        int freed = conversation.clearToolResults(COMPACTABLE_TOOLS, KEEP_RECENT_RESULTS, CLEARED);
        if(freed > 0){
            publisher.publishEvent(new AgentEvent.ContextChanged("микрокомпакт"));
        }
    }
}
