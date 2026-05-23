#!/usr/bin/env bash
# Downloads the quantized all-MiniLM-L6-v2 ONNX model and vocabulary file
# required for semantic drift detection.
#
# Usage:
#   bash scripts/download-model.sh [TARGET_DIR]
#
# Default target: ./models/
# After running, set in application.yml:
#   sentinel:
#     semantic-drift:
#       enabled: true
#       model-path: ./models/all-MiniLM-L6-v2.onnx
#       vocab-path:  ./models/vocab.txt

set -euo pipefail

TARGET_DIR="${1:-./models}"
mkdir -p "$TARGET_DIR"

BASE_URL="https://huggingface.co/optimum/all-MiniLM-L6-v2/resolve/main"
MODEL_FILE="$TARGET_DIR/all-MiniLM-L6-v2.onnx"
VOCAB_FILE="$TARGET_DIR/vocab.txt"

echo "Downloading all-MiniLM-L6-v2 ONNX model to $TARGET_DIR ..."

if command -v curl &>/dev/null; then
    curl -fL --progress-bar -o "$MODEL_FILE" "$BASE_URL/model.onnx"
    curl -fL --progress-bar -o "$VOCAB_FILE" "$BASE_URL/vocab.txt"
elif command -v wget &>/dev/null; then
    wget -q --show-progress -O "$MODEL_FILE" "$BASE_URL/model.onnx"
    wget -q --show-progress -O "$VOCAB_FILE" "$BASE_URL/vocab.txt"
else
    echo "ERROR: curl or wget is required."
    exit 1
fi

echo ""
echo "Done. Add the following to your application.yml:"
echo ""
echo "  sentinel:"
echo "    semantic-drift:"
echo "      enabled: true"
echo "      model-path: $MODEL_FILE"
echo "      vocab-path:  $VOCAB_FILE"
