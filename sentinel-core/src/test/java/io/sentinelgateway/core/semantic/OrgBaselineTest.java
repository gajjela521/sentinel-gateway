package io.sentinelgateway.core.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrgBaselineTest {

    private float[] vector(float... values) { return values; }

    @Test
    void returnsSimilarityOneBeforeMinSamples() {
        OrgBaseline baseline = new OrgBaseline("org-1", 3);
        // No updates yet — baseline is cold
        double sim = baseline.cosineSimilarity(vector(1f, 0f, 0f), 50);
        assertEquals(1.0, sim, "Cold baseline should return 1.0 (normal)");
    }

    @Test
    void centroidConvergesToMean() {
        OrgBaseline baseline = new OrgBaseline("org-1", 3);
        baseline.update(vector(1f, 0f, 0f));
        baseline.update(vector(0f, 1f, 0f));
        baseline.update(vector(0f, 0f, 1f));

        double[] centroid = baseline.centroidSnapshot();
        assertEquals(3, baseline.sampleCount());
        // Centroid should be ~[0.33, 0.33, 0.33]
        for (double v : centroid) {
            assertEquals(1.0 / 3.0, v, 0.001);
        }
    }

    @Test
    void perfectlySimilarVectorScoresOne() {
        OrgBaseline baseline = new OrgBaseline("org-1", new double[]{1.0, 0.0, 0.0}, 100);
        double sim = baseline.cosineSimilarity(vector(1f, 0f, 0f), 5);
        assertEquals(1.0, sim, 0.001);
    }

    @Test
    void orthogonalVectorScoresZero() {
        OrgBaseline baseline = new OrgBaseline("org-1", new double[]{1.0, 0.0, 0.0}, 100);
        double sim = baseline.cosineSimilarity(vector(0f, 1f, 0f), 5);
        assertEquals(0.0, sim, 0.001);
    }

    @Test
    void dimensionMismatchThrows() {
        OrgBaseline baseline = new OrgBaseline("org-1", 3);
        assertThrows(IllegalArgumentException.class,
                () -> baseline.update(vector(1f, 0f)));
    }

    @Test
    void concurrentUpdatesDoNotCorruptCentroid() throws InterruptedException {
        OrgBaseline baseline = new OrgBaseline("org-1", 2);
        int threads = 20;
        int updatesPerThread = 100;
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < updatesPerThread; j++) {
                    baseline.update(vector(1f, 1f));
                }
            });
            workers[i].start();
        }
        for (Thread w : workers) w.join();

        assertEquals(threads * updatesPerThread, baseline.sampleCount());
        double[] c = baseline.centroidSnapshot();
        // All vectors are [1, 1] so centroid should be ≈ [1, 1] (normalized direction, but raw mean)
        assertTrue(c[0] > 0.9 && c[1] > 0.9, "Centroid should converge to ~[1,1]");
    }
}
