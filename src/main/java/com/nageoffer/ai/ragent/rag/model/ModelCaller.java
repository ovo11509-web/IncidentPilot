package com.nageoffer.ai.ragent.rag.model;

@FunctionalInterface
public interface ModelCaller<C, T> {

    T call(C client, ModelTarget target) throws Exception;
}
