package io.sentinelgateway.core.semantic;

/**
 * Stub embedding model for lightweight deployments where the ONNX Runtime
 * dependency is excluded from the classpath.
 *
 * <p>Always reports {@link #isAvailable()} as {@code false}, which causes
 * {@link SemanticDriftLayer} to skip all drift checks cleanly.
 *
 * <p>Thread safety: stateless singleton — safe for concurrent use.
 */
public final class NoOpEmbeddingModel implements EmbeddingModel {

    /** Shared singleton; no state to protect. */
    public static final NoOpEmbeddingModel INSTANCE = new NoOpEmbeddingModel();

    private NoOpEmbeddingModel() {}

    @Override
    public int dimension() { return OnnxEmbeddingModel.EMBEDDING_DIMENSION; }

    @Override
    public boolean isAvailable() { return false; }

    @Override
    public float[] embed(String text) { return new float[OnnxEmbeddingModel.EMBEDDING_DIMENSION]; }
}
