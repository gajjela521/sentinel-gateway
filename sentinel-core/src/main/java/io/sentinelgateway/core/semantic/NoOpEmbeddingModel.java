package io.sentinelgateway.core.semantic;

/**
 * Stub embedding model for lightweight deployments where ONNX is excluded.
 * Always reports unavailable so {@link SemanticDriftLayer} skips drift checks.
 */
public final class NoOpEmbeddingModel implements EmbeddingModel {

    public static final NoOpEmbeddingModel INSTANCE = new NoOpEmbeddingModel();

    private NoOpEmbeddingModel() {}

    @Override public int dimension()          { return 384; }
    @Override public boolean isAvailable()    { return false; }
    @Override public float[] embed(String t)  { return new float[384]; }
}
