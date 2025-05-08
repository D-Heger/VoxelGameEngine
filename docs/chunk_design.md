# Chunk Design Document

This document details the design decisions for the `Chunk` data structure, specifically addressing **Subtask P3-T1.1: Design Chunk Dimensions and Memory Layout**.

## 1. Chunk Dimensions

- **Size:** 16x16x16 blocks.
- **Rationale:**
    - **Power of Two:** Simplifies coordinate calculations. World coordinates can be converted to chunk coordinates and local block coordinates using efficient bitwise operations (division/modulo by 16 becomes right shift/bitwise AND).
    - **Industry Standard:** This size is widely used in similar voxel games (like Minecraft), indicating a proven balance.
    - **Memory vs. Overhead:** It strikes a good balance between the memory required for each chunk and the overhead associated with managing a large number of chunk objects (e.g., in hash maps, during iteration). Larger chunks reduce management overhead but increase memory per chunk and potentially make fine-grained loading/unloading less efficient. Smaller chunks increase management overhead.
    - **Meshing Performance:** Chunk size impacts the surface area to volume ratio, which can affect meshing algorithm performance. 16x16x16 is generally considered reasonable for common algorithms like Greedy Meshing or Naive Surface Nets.

## 2. World Height

- **Vertical Chunks:** 16 chunks stacked vertically.
- **Total Height:** 16 chunks * 16 blocks/chunk = 256 blocks.
- **Rationale:**
    - **Alignment:** Matches the common world height limit found in Minecraft, providing a familiar scale.
    - **Configurability:** While 256 is the initial target, the design should ideally allow this to be configured if different world height limits are desired later.
    - **Memory Considerations:** Total world height directly impacts the number of chunks needed vertically, influencing overall memory usage for a loaded world column.

## 3. Memory Layout (Block IDs)

- **Structure:** Flat 1D `short` array (`short[4096]`).
- **Indexing Formula:** `index = y * CHUNK_WIDTH * CHUNK_DEPTH + z * CHUNK_WIDTH + x`
    - Where `CHUNK_WIDTH = 16`, `CHUNK_DEPTH = 16`.
    - `x`, `y`, `z` are local coordinates within the chunk (0-15).
- **Data Type:** `short` (2 bytes). Allows for 65,536 unique block types, which is ample for initial development and most foreseeable needs.
- **Rationale:**
    - **Cache Locality:** Iterating over a flat array generally results in better CPU cache performance compared to a 3D array (`short[16][16][16]`). This is crucial for performance-sensitive operations like chunk meshing, physics simulations, and lighting updates that often iterate through blocks sequentially.
    - **Simplicity:** While requiring an explicit index calculation, managing a single array can be simpler than nested arrays.
    - **FastUtil Integration:** This layout works well with FastUtil's primitive collections if needed later for optimized storage or operations.

## 4. Memory Footprint (Initial)

- **Calculation:** 16 (width) * 16 (depth) * 16 (height) blocks/chunk * 2 bytes/block (`short`) = 4096 blocks * 2 bytes/block = 8192 bytes.
- **Result:** 8 KB per chunk *just for block ID storage*.

## 5. Future Considerations

- **Additional Data:** Data like block light, sky light, block metadata (e.g., rotation, inventory), or biome information will likely be needed later.
- **Storage Strategy:** To maintain cache efficiency for specific tasks, this additional data might be stored in *separate* flat arrays of the same size (4096 elements) rather than interleaving it within a single large block data structure. For example, meshing primarily needs block IDs, while lighting updates primarily need light levels. Separating these allows each system to iterate over only the data it requires, improving cache performance.

## 6. World Storage & ChunkManager

- **Instance Lifecycle:** We use a singleton pattern via `ChunkManager.getInstance()`. This ensures a single global manager accessible to all systems.

- **Access:** Systems (Renderer, Game Logic, World Generation) obtain the manager via `ChunkManager.getInstance()`, avoiding the need to pass references explicitly.

- **Thread Safety:** All public methods on `ChunkManager` are synchronized, providing safe concurrent access from the core loop, render thread, and worker threads.

- **Future Considerations:** If more flexibility or testability is required, the singleton could be replaced by dependency injection (e.g., via a DI framework) or by passing the manager as a constructor parameter to consuming systems.
