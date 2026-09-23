package com.harness.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Agent {

    private static final String SYSTEM_PROMPT = """
            You are Harness, a coding agent that works in a local repository.
            Answer concisely.""";

    private Conversation conversation = new Conversation(SYSTEM_PROMPT);


    private final ChatModel chatModel;

    public Agent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String run(String userMessage) {
        conversation.addUserMessage(userMessage);
        AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
        conversation.addAssistantMessage(assistantMessage);
        return assistantMessage.getText();
    }
}
