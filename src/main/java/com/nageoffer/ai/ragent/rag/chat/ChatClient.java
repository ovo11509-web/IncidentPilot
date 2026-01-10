package com.nageoffer.ai.ragent.rag.chat;

import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;

public interface ChatClient {

    String provider();

    String chat(ChatRequest request, ModelTarget target);

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target);
}
