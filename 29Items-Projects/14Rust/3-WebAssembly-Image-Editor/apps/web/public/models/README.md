# Auto-crop model contract

The checked-in `model-manifest.json` selects the local `heuristic` provider, which produces a deterministic crop without downloading a model. This is the release default and keeps auto-crop available offline after the application assets are loaded.

To deploy a reviewed ONNX model, publish the immutable model asset under this directory or a trusted public CDN and update the manifest with:

- `provider: "onnx"`, a versioned `modelUrl`, and the SHA-256 checksum.
- Exact input width, height, layout, channel order, and normalization used during training.
- The expected normalized `xywh` output shape and minimum confidence policy.

The runtime fetches the model, verifies the checksum with Web Crypto, prepares the configured tensor, validates finite normalized coordinates, and falls back to the local heuristic on any load or inference error. Model assets are public supply-chain artifacts: retain provenance, license, evaluation notes, and rollback ownership with the release record.
