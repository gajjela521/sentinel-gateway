package io.sentinelgateway.core.semantic;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Wraps all-MiniLM-L6-v2 (or any sentence-transformer exported to ONNX)
 * via the Microsoft ONNX Runtime Java binding.
 *
 * <p>Thread safety: {@link OrtSession} is thread-safe for concurrent inference.
 * This class is safe to share across virtual threads.
 *
 * <p>Deployment note: the model file (~22 MB quantized) and vocabulary must be
 * present at the paths supplied to the constructor. To skip drift detection in
 * lightweight deployments, use {@link NoOpEmbeddingModel} instead and omit the
 * {@code onnxruntime} dependency.
 */
public final class OnnxEmbeddingModel implements EmbeddingModel, Closeable {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingModel.class);

    /** Output dimension of all-MiniLM-L6-v2. */
    public static final int EMBEDDING_DIMENSION = 384;

    /** Maximum number of tokens fed to the model per request. */
    private static final int MAX_TOKENS = 128;

    /** Intra-op thread count kept low to avoid starving the request-handling thread pool. */
    private static final int ONNX_INTRA_OP_THREADS = 2;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final SimpleTokenizer tokenizer;
    private volatile boolean available = false;

    /**
     * @param modelPath path to the ONNX model file; must exist and be readable
     * @param vocabPath path to the WordPiece vocabulary file; must exist and be readable
     * @throws OrtException              if the ONNX runtime cannot load the model
     * @throws IllegalArgumentException  if either path does not exist
     */
    public OnnxEmbeddingModel(Path modelPath, Path vocabPath) throws OrtException {
        Objects.requireNonNull(modelPath, "modelPath must not be null");
        Objects.requireNonNull(vocabPath, "vocabPath must not be null");
        if (!Files.exists(modelPath))
            throw new IllegalArgumentException("ONNX model file not found: " + modelPath);
        if (!Files.exists(vocabPath))
            throw new IllegalArgumentException("Vocab file not found: " + vocabPath);

        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(ONNX_INTRA_OP_THREADS);
        this.session   = env.createSession(modelPath.toString(), opts);
        this.tokenizer = new SimpleTokenizer(vocabPath);
        this.available = true;
        log.info("ONNX embedding model loaded: path={} dimension={}", modelPath, EMBEDDING_DIMENSION);
    }

    @Override
    public int dimension() { return EMBEDDING_DIMENSION; }

    @Override
    public boolean isAvailable() { return available; }

    /**
     * Embeds {@code text} into a unit-normalized float vector of length {@link #EMBEDDING_DIMENSION}.
     * Returns a zero vector on inference error (the caller treats zero vectors as maximally drifted).
     */
    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            log.debug("Received null or blank text; returning zero vector");
            return new float[EMBEDDING_DIMENSION];
        }

        long[] tokenIds     = tokenizer.tokenize(text, MAX_TOKENS);
        long[] attentionMask = new long[tokenIds.length];
        long[] tokenTypeIds  = new long[tokenIds.length];
        Arrays.fill(attentionMask, 1L);
        // tokenTypeIds stays all-zero (single-sequence input)

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids",      OnnxTensor.createTensor(env, new long[][]{tokenIds}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{attentionMask}));
            inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{tokenTypeIds}));

            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state shape: [1, seq_len, EMBEDDING_DIMENSION]
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                return meanPool(hidden[0]);
            }
        } catch (OrtException e) {
            log.error("Embedding inference failed for text of length {}; returning zero vector", text.length(), e);
            return new float[EMBEDDING_DIMENSION];
        } finally {
            // Always release native tensor memory regardless of success or failure
            for (OnnxTensor tensor : inputs.values()) {
                try { tensor.close(); } catch (Exception ignored) {}
            }
        }
    }

    private float[] meanPool(float[][] tokenVectors) {
        float[] mean = new float[EMBEDDING_DIMENSION];
        for (float[] vec : tokenVectors) {
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                mean[i] += vec[i];
            }
        }
        int n = tokenVectors.length;
        if (n > 0) {
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                mean[i] /= n;
            }
        }
        return normalizeL2(mean);
    }

    private float[] normalizeL2(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-9f) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
        return v;
    }

    @Override
    public void close() {
        available = false;
        try { session.close(); } catch (OrtException e) {
            log.warn("Error closing ONNX session", e);
        }
        env.close();
    }
}
