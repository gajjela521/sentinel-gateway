package io.sentinelgateway.core.semantic;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.*;

/**
 * Wraps all-MiniLM-L6-v2 (or any sentence-transformer exported to ONNX)
 * via the Microsoft ONNX Runtime Java binding.
 *
 * The model file (~22 MB quantized) and vocabulary must be present at the
 * paths supplied to the constructor. Bundle them inside the sentinel-core JAR
 * or supply them at runtime via sentinel.semantic.model-path.
 *
 * This class is optional — sentinel-core compiles without it when onnxruntime
 * is excluded from the classpath.
 */
public final class OnnxEmbeddingModel implements EmbeddingModel, Closeable {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingModel.class);
    private static final int DIMENSION = 384; // all-MiniLM-L6-v2 output size
    private static final int MAX_TOKENS = 128;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final SimpleTokenizer tokenizer;
    private volatile boolean available = false;

    public OnnxEmbeddingModel(Path modelPath, Path vocabPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(2);  // keep CPU footprint small
        this.session = env.createSession(modelPath.toString(), opts);
        this.tokenizer = new SimpleTokenizer(vocabPath);
        this.available = true;
        log.info("ONNX embedding model loaded from {}", modelPath);
    }

    @Override
    public int dimension() { return DIMENSION; }

    @Override
    public boolean isAvailable() { return available; }

    @Override
    public float[] embed(String text) {
        try {
            long[] tokenIds = tokenizer.tokenize(text, MAX_TOKENS);
            long[] attentionMask = new long[tokenIds.length];
            Arrays.fill(attentionMask, 1L);
            long[] tokenTypeIds = new long[tokenIds.length]; // all zeros

            long[] shape = {1, tokenIds.length};

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids",      OnnxTensor.createTensor(env, new long[][]{tokenIds}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{attentionMask}));
            inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{tokenTypeIds}));

            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state shape: [1, seq_len, 384] — mean-pool over seq_len
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                return meanPool(hidden[0]);
            }
        } catch (OrtException e) {
            log.error("Embedding failed for input of length {}", text.length(), e);
            return new float[DIMENSION]; // zero vector on failure — will score as maximally drifted
        }
    }

    private float[] meanPool(float[][] tokenVectors) {
        float[] mean = new float[DIMENSION];
        for (float[] vec : tokenVectors) {
            for (int i = 0; i < DIMENSION; i++) mean[i] += vec[i];
        }
        for (int i = 0; i < DIMENSION; i++) mean[i] /= tokenVectors.length;
        return normalizeL2(mean);
    }

    private float[] normalizeL2(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm > 0f) for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }

    @Override
    public void close() {
        available = false;
        try { session.close(); } catch (OrtException ignored) {}
        env.close();
    }
}
