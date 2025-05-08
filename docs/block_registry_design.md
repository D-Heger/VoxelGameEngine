# Block Registry System Design Document

This document details the design decisions for the `BlockRegistry` system, specifically addressing **Subtask P3-T4.1: Block Registry System Analysis**.

## 1. Purpose and Requirements

The Block Registry System serves as the central authority for defining and accessing information about all available block types within the engine. It maps numerical block IDs (stored within Chunks) to their corresponding properties (like name, solidity, transparency, texture information, etc.).

**Key Requirements:**

1. **ID Mapping:** Provide efficient lookup from a `short` block ID to its properties.
2. **Property Storage:** Store essential block characteristics required by various engine systems (rendering, physics, world generation, game logic).
3. **Memory Efficiency:** Minimize the memory footprint, as block data might be accessed frequently.
4. **Performance:** Ensure fast access to block properties, especially commonly needed ones like solidity or transparency.
5. **Extensibility:** Allow easy addition of new block types and new block properties in the future without requiring major engine refactoring.
6. **Data-Driven:** Load block definitions from external configuration files (e.g., JSON) rather than hardcoding them in Java.
7. **Thread Safety:** Allow safe access from multiple threads (e.g., main game loop, rendering thread, world generation worker threads).
8. **Integration:** Integrate seamlessly with the `Chunk` system, rendering pipeline, physics engine, and potentially game logic systems.

## 2. Core Data Structures

Two primary data structures will be used to store and access block information:

1. **`BlockProperties[] registryById`**:
    * **Type:** A simple Java array (`BlockProperties[]`).
    * **Indexing:** The index of the array directly corresponds to the `short` block ID.
    * **Rationale:** Provides the absolute fastest O(1) lookup performance for converting a block ID (which is stored in chunks and frequently accessed) into its full property set. This assumes block IDs are assigned densely starting from 0.
    * **Management:** The size of this array will be determined during the loading phase based on the highest assigned block ID. Access needs bounds checking or a guarantee that only valid IDs are used.

2. **`Object2ObjectMap<String, BlockProperties> registryByName`**:
    * **Type:** FastUtil's `Object2ObjectOpenHashMap<String, BlockProperties>`.
    * **Indexing:** Uses the unique block name string (e.g., "core:dirt") as the key.
    * **Rationale:** Allows lookup by name, which is essential for loading block definitions, debugging, potential console commands, and potentially game logic that references blocks by name. FastUtil is used for memory efficiency compared to standard `HashMap`.

## 3. `BlockProperties` Data Structure

A dedicated class (or potentially an interface with implementations) will hold the properties for a single block type.

* **Class:** `BlockProperties` (likely immutable after creation).
* **Key Fields (Initial):**
  * `short id`: The numerical ID assigned to this block type.
  * `String name`: The unique identifier string (e.g., "core:dirt").
  * `boolean isSolid`: Used by physics for collision and movement obstruction.
  * `boolean isTransparent`: Used by the meshing algorithm to determine if faces behind this block should be rendered and potentially by lighting algorithms.
  * `byte luminance`: Light level emitted by the block (0-15). Used by lighting.
  * `Map<Direction, TextureRef> textures`: Maps each face direction to its texture reference (name like `core:block/dirt` to be resolved during asset loading as to load the dirt block texture). Used by meshing/rendering. `TextureRef` would be a simple class holding a name reference for a texture .
* **Rationale:** Encapsulates all relevant data for a block type. Making it immutable simplifies thread safety, as once the registry is loaded, these objects won't change.
* **Extensibility:** New properties can be added to this class. If a property is not applicable to all blocks, it can use default values or nullable types (though primitives with defaults are often preferred for performance).

## 4. Loading and Initialization

1. **Source:** Block definitions will be loaded from external files (e.g., JSON files located in a predefined `/assets/definitions/blocks/` directory).
2. **Process:**
    * At engine startup, the `BlockRegistry` initializes.
    * It scans the definition files.
    * It parses each definition (using a library like Jackson for JSON).
    * A special "core:block/air" block is hardcoded or guaranteed to be loaded first and assigned ID `0`.
    * For each subsequent block definition read:
        * Assign the next available sequential `short` ID.
        * Create an immutable `BlockProperties` instance.
        * Store the instance in the `registryByName` map (using the name as the key).
        * Store the instance in the `registryById` array (using the assigned ID as the index). The array might need resizing if the initial estimate was too small.
    * After all definitions are loaded, the registry is considered "frozen" and ready for use.
3. **Error Handling:** Handle missing files, parsing errors, duplicate block names, and running out of `short` IDs (though highly unlikely with 65k IDs).
4. **Single-Threaded:** The entire loading process occurs synchronously and single-threaded during engine initialization before other systems start accessing the registry.

## 5. Access Patterns

* **`BlockProperties getBlock(short id)`:** Primary access method. Performs a direct array lookup `registryById[id]`. Requires caller to handle potentially invalid IDs (e.g., via checks or by the registry returning a default "unknown" block). Extremely fast.
* **`BlockProperties getBlock(String name)`:** Looks up via the `registryByName` map. Used less frequently, primarily during setup, debugging, or specific game logic.
* **`short getId(String name)`:** Convenience method to get the ID from a name (looks up in `registryByName` and returns the `id` field).
* **`BlockProperties getAirBlock()`:** Returns the pre-defined properties for Air (ID 0).
* **`BlockProperties getDefaultBlock()`:** Returns a default/fallback block properties instance for handling errors or unloaded blocks.

## 6. Thread Safety

* **Initialization:** Loading and population of the registry structures happen single-threaded during startup.
* **Publication:** Once loaded, the internal array and map references are effectively final and the `BlockProperties` objects they contain are immutable. This internal state needs to be safely published to other threads (e.g., using `volatile` or `final` fields for the registry instance itself if using a singleton).
* **Read Access:** All standard access methods (`getBlock(id)`, `getBlock(name)`, etc.) are read-only operations on immutable data or safely published structures. Therefore, they are inherently thread-safe and do not require explicit synchronization (`synchronized` blocks or locks). Multiple threads can read from the registry concurrently without issues.

## 7. Integration with Other Systems

* **Chunk Storage (`engine-world`):**
  * Chunks store only the `short` block IDs.
  * When needing properties (e.g., `chunk.getBlockProperties(x,y,z)`), the chunk retrieves the `short` ID and calls `BlockRegistry.getInstance().getBlock(id)`.
* **Meshing (`engine-world`):** Accesses `BlockProperties` via ID to get `isTransparent` and `textures` for generating mesh faces and UVs.
* **Physics (`engine-physics`):** Accesses `BlockProperties` via ID to get `isSolid` (and potentially collision shape later) for collision checks.
* **Rendering (`engine-renderer`):** Uses texture information derived from `BlockProperties` (likely passed via the mesh data) to render blocks correctly. Might access luminance for lighting shaders.
* **World Generation (`engine-world`):** Uses block names or IDs (obtained via `getId(name)`) to place specific blocks during generation.
* **Serialization:** Chunks only serialize the `short` IDs. The `BlockRegistry` itself is *not* saved with the world. This implies that the block definitions (and their assigned IDs) must remain consistent across game sessions or require a migration mechanism if definitions change between engine versions.

## 8. Performance Considerations

* **ID Lookup:** Array lookup (`getBlock(id)`) is extremely fast.
* **Name Lookup:** Map lookup (`getBlock(name)`) is fast (average O(1)) but significantly slower than array lookup. Should be avoided in performance-critical loops.
* **Memory:** Using `short` IDs is memory-efficient in chunks. `BlockProperties` objects add overhead, but this is amortized across all instances of that block type in the world. Using FastUtil for the name map helps reduce overhead compared to `java.util.HashMap`. The `registryById` array overhead depends on the number of registered blocks.

## 9. Extensibility

* **New Blocks:** Add a new JSON definition file. The registry will automatically pick it up on the next startup and assign a new ID.
* **New Properties:** Add a new field to the `BlockProperties` class and update the JSON parsing logic. Provide default values for existing blocks if the property isn't specified in their old definitions. Systems needing the new property can then access it.

## 10. Example JSON Definition (`dirt.json`)

```json
{
  "name": "core:dirt",
  "solid": true,
  "transparent": false,
  "luminance": 0,
  "textures": {
    "north": "core:block/dirt",
    "south": "core:block/dirt",
    "east": "core:block/dirt",
    "west": "core:block/dirt",
    "up": "core:block/dirt",
    "down": "core:block/dirt"
  }
  // Future properties like "hardness", "toolRequired" could be added here
}
```

```json
{
  "name": "core:grass",
  "solid": true,
  "transparent": false,
  "luminance": 0,
  "textures": {
    "north": "core:block/grass_side",
    "south": "core:block/grass_side",
    "east": "core:block/grass_side",
    "west": "core:block/grass_side",
    "up": "core:block/grass_top",
    "down": "core:block/dirt"
  }
}
```

(Note: )