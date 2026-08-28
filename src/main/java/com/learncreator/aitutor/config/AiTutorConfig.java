package com.learncreator.aitutor.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VectorStore (PgVectorStore) and EmbeddingModel (OpenAI) beans are provided automatically by
 * Spring AI's own autoconfiguration based on application.yml — no manual @Bean needed for those.
 *
 * The one thing we do configure explicitly: wrap the auto-configured ChatModel (Anthropic — see
 * application.yml, where OpenAI's chat autoconfiguration is disabled to avoid two competing
 * ChatModel beans) in a ChatClient, which is the actual API AiTutorChatService calls against.
 */
@Configuration
public class AiTutorConfig {

    @Bean
    public ChatClient aiTutorChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
