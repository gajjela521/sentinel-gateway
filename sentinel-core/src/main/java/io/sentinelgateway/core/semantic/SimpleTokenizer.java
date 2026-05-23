package io.sentinelgateway.core.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal WordPiece tokenizer for BERT-family models.
 * Handles the [CLS], [SEP], [UNK] special tokens and basic whitespace splitting.
 * For production, replace with the HuggingFace tokenizers Java binding if
 * multi-lingual or complex unicode support is required.
 */
final class SimpleTokenizer {

    private static final Logger log = LoggerFactory.getLogger(SimpleTokenizer.class);

    private static final long CLS = 101L;
    private static final long SEP = 102L;
    private static final long UNK = 100L;

    private final Map<String, Long> vocab;

    SimpleTokenizer(Path vocabPath) {
        this.vocab = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(vocabPath)) {
            String line;
            long idx = 0;
            while ((line = r.readLine()) != null) {
                vocab.put(line.strip(), idx++);
            }
            log.info("Loaded vocab with {} tokens from {}", vocab.size(), vocabPath);
        } catch (IOException e) {
            log.error("Failed to load vocab from {}", vocabPath, e);
        }
    }

    long[] tokenize(String text, int maxLength) {
        List<Long> ids = new ArrayList<>();
        ids.add(CLS);

        for (String word : text.toLowerCase().split("\\s+")) {
            ids.addAll(wordpiece(word));
            if (ids.size() >= maxLength - 1) break;
        }

        if (ids.size() > maxLength - 1) {
            ids = ids.subList(0, maxLength - 1);
        }
        ids.add(SEP);

        return ids.stream().mapToLong(Long::longValue).toArray();
    }

    private List<Long> wordpiece(String word) {
        List<Long> result = new ArrayList<>();
        if (word.isEmpty()) return result;

        if (vocab.containsKey(word)) {
            result.add(vocab.get(word));
            return result;
        }

        int start = 0;
        boolean isBad = false;
        while (start < word.length()) {
            int end = word.length();
            String cur = null;
            while (start < end) {
                String sub = (start > 0 ? "##" : "") + word.substring(start, end);
                if (vocab.containsKey(sub)) { cur = sub; break; }
                end--;
            }
            if (cur == null) { isBad = true; break; }
            result.add(vocab.get(cur));
            start = end;
        }

        if (isBad) { result.clear(); result.add(UNK); }
        return result;
    }
}
