package io.sentinelgateway.core.semantic;

/**
 * Pluggable embedding backend.
 * Default implementation uses ONNX Runtime + all-MiniLM-L6-v2.
 * A stub no-op implementation is provided for lightweight deployments.
 */
public interface EmbeddingModel {

    int dimension();

    /**
     * Embed the given text into a float vector of length {@link #dimension()}.
     * Implementations must be thread-safe.
     */
    float[] embed(String text);

    /** Returns true when the model is loaded and warm. */
    boolean isAvailable();
}
