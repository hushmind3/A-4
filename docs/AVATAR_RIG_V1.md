# Avatar rig v1

The native OBJ renderer supports an optional two-joint skin (head parent, jaw child), independent of the previously implemented mouth morph. This is a compact authored rig, not a general VRM/glTF importer.

Import the neutral triangulated OBJ first. Prepare a JSON file:

```json
{"schema":"dominion-avatar-rig/v1","baseSha256":"<SHA-256 of exact original OBJ bytes>","headPivot":[0,0.5,0],"jawPivot":[0,0.2,0],"headWeights":[1,0.5,0],"jawWeights":[0,0.5,1]}
```

The example weights describe only one triangle. Provide one head and jaw weight per emitted triangle corner, in OBJ face order. Degenerate faces are omitted by the existing parser. Positions and pivots use the original OBJ's normalized centered coordinate system (longest dimension = 2). Weights must be finite in [0,1], with head+jaw <= 1; the residual is the static/root weight. The renderer blends positions and normals on the GPU. Jaw transformation inherits head rotation. If an authored mouth target is present it is used for speech; otherwise the jaw follows actual WAV amplitude. Head and jaw sliders provide direct preview. The descriptor is persisted per engine, character and original OBJ hash; a different base cannot reuse the rig accidentally. General skeletons, textures and phoneme-level alignment are not implemented by this two-joint format.

## Viseme targets

The scene now also stores optional authored OBJ targets for a/e/i/o/u/closed. Select the label and import a mesh with the same topology. Targets are bound to the original OBJ hash. When the engine advertises aligned_speech, actual playback time selects the corresponding target and openness; otherwise the existing amplitude driver is used. Full glTF/VRM import and general-purpose skeletons remain separate work.
