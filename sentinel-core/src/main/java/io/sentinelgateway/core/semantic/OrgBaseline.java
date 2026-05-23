package io.sentinelgateway.core.semantic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Running centroid of legitimate API calls for one organization.
 * Uses Welford's online algorithm so the baseline updates incrementally
 * without storing every historical embedding.
 */
public final class OrgBaseline {

    private final String organizationId;
    private final int dimension;
    private final double[] centroid;
    private final AtomicInteger count = new AtomicInteger(0);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public OrgBaseline(String organizationId, int dimension) {
        this.organizationId = organizationId;
        this.dimension = dimension;
        this.centroid = new double[dimension];
    }

    /** Bootstrap from a pre-computed centroid (e.g., loaded from persistent store). */
    public OrgBaseline(String organizationId, double[] centroid, int sampleCount) {
        this.organizationId = organizationId;
        this.dimension = centroid.length;
        this.centroid = centroid.clone();
        this.count.set(sampleCount);
    }

    /**
     * Add a new embedding to the baseline using Welford's online mean update.
     * Thread-safe; uses a write lock only during the centroid update.
     */
    public void update(float[] embedding) {
        if (embedding.length != dimension)
            throw new IllegalArgumentException("Embedding dimension mismatch: expected " + dimension);

        lock.writeLock().lock();
        try {
            int n = count.incrementAndGet();
            for (int i = 0; i < dimension; i++) {
                centroid[i] += (embedding[i] - centroid[i]) / n;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Cosine similarity between the given embedding and the org centroid.
     * Returns 0.0 if baseline has fewer than {@code minSamples} observations.
     */
    public double cosineSimilarity(float[] embedding, int minSamples) {
        if (count.get() < minSamples) return 1.0; // treat as normal until baseline is warm

        lock.readLock().lock();
        try {
            double dot = 0.0, normA = 0.0, normB = 0.0;
            for (int i = 0; i < dimension; i++) {
                dot  += centroid[i] * embedding[i];
                normA += centroid[i] * centroid[i];
                normB += (double) embedding[i] * embedding[i];
            }
            if (normA == 0.0 || normB == 0.0) return 1.0;
            return dot / (Math.sqrt(normA) * Math.sqrt(normB));
        } finally {
            lock.readLock().unlock();
        }
    }

    public String organizationId() { return organizationId; }
    public int sampleCount()       { return count.get(); }

    public double[] centroidSnapshot() {
        lock.readLock().lock();
        try { return centroid.clone(); } finally { lock.readLock().unlock(); }
    }
}
