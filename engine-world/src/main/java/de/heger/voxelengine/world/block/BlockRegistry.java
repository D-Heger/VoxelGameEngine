package de.heger.voxelengine.world.block;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.core.utils.Validate;
import de.heger.voxelengine.world.chunk.Direction;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Central registry for managing block types and their properties.
 * Provides efficient lookup by block ID and name.
 *
 * This class is designed to be populated during a single-threaded
 * initialization phase
 * and then accessed concurrently from multiple threads. Access methods are
 * thread-safe
 * after initialization.
 *
 * Implements requirements from P3-T4.3 and block_registry_design.md.
 */
public final class BlockRegistry {

    private static final LoggerFacade LOGGER = LoggerFacade.get(BlockRegistry.class);
    private static final int INITIAL_CAPACITY = 256; // Initial guess for map/list sizes

    private static final String DEFAULT_BLOCK_DEFINITIONS_PATH = "assets/definitions/blocks";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper(); // Reusable Jackson mapper

    // --- Singleton Holder Pattern ---
    private static class Holder {
        static final BlockRegistry INSTANCE = new BlockRegistry();
    }

    public static BlockRegistry getInstance() {
        return Holder.INSTANCE;
    }

    // --- Core Data Structures ---
    // Use temporary maps during registration, convert to final structures upon
    // finalization.
    private final Object2ObjectMap<String, BlockProperties> tempRegistryByName = new Object2ObjectOpenHashMap<>(
            INITIAL_CAPACITY);
    private final Object2ObjectMap<Short, BlockProperties> tempRegistryById = new Object2ObjectOpenHashMap<>(
            INITIAL_CAPACITY);

    // Final, immutable structures for fast, thread-safe access after initialization
    private BlockProperties[] registryById = null; // Array for direct ID lookup
    private Object2ObjectMap<String, BlockProperties> registryByName = null; // Map for name lookup
    private short maxBlockId = -1;

    // --- State Management ---
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(); // To protect registration/finalization

    // --- Special Blocks ---
    public static BlockProperties AIR = null; // Will be initialized

    private BlockRegistry() {
        // Private constructor for singleton
        // Register the fundamental AIR block immediately
        initializeAirBlock();
        loadBlockDefinitions(DEFAULT_BLOCK_DEFINITIONS_PATH);
        // NOTE: finalizeRegistry() must be called externally after all initialization
        // is complete.
    }

    private void initializeAirBlock() {
        // Air block properties: ID 0, non-solid, transparent, non-luminous, no textures
        // needed
        Map<Direction, TextureRef> airTextures = new EnumMap<>(Direction.class); // Empty map
        BlockProperties airProps = new BlockProperties(
                (short) 0, // ID 0
                "core:block/air",
                false, // not solid
                true, // transparent
                (byte) 0, // no luminance
                airTextures);
        AIR = airProps; // Assign static final field

        // Register AIR internally before allowing external registrations
        lock.writeLock().lock();
        try {
            if (tempRegistryById.containsKey(airProps.getId()) || tempRegistryByName.containsKey(airProps.getName())) {
                throw new IllegalStateException("AIR block registration conflict. This should not happen.");
            }
            tempRegistryById.put(airProps.getId(), airProps);
            tempRegistryByName.put(airProps.getName(), airProps);
            maxBlockId = (short) Math.max(maxBlockId, airProps.getId());
            LOGGER.info("Registered fundamental block: {}", airProps.getName());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Registers a new block type. This method should only be called during the
     * engine's initialization phase before {@link #finalizeRegistry()} is called.
     *
     * @param properties The properties of the block to register. Must not be null.
     * @throws IllegalStateException if the registry is already finalized or if a
     *                               block
     *                               with the same ID or name already exists.
     * @throws NullPointerException  if properties is null.
     */
    public void registerBlock(BlockProperties properties) {
        Validate.notNull(properties, "BlockProperties cannot be null");

        lock.writeLock().lock();
        try {
            if (initialized.get()) {
                throw new IllegalStateException("BlockRegistry is already finalized. Cannot register new blocks.");
            }
            if (tempRegistryById.containsKey(properties.getId())) {
                throw new IllegalStateException("Duplicate block ID registration attempt: " + properties.getId() + " ('"
                        + properties.getName() + "') conflicts with existing block '"
                        + tempRegistryById.get(properties.getId()).getName() + "'");
            }
            if (tempRegistryByName.containsKey(properties.getName())) {
                throw new IllegalStateException("Duplicate block name registration attempt: '" + properties.getName()
                        + "' (ID: " + properties.getId() + ") conflicts with existing block ID "
                        + tempRegistryByName.get(properties.getName()).getId());
            }

            tempRegistryById.put(properties.getId(), properties);
            tempRegistryByName.put(properties.getName(), properties);
            maxBlockId = (short) Math.max(maxBlockId, properties.getId());
            LOGGER.debug("Registered block: {} (ID: {})", properties.getName(), properties.getId());

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finalizes the registry after all blocks have been registered.
     * This converts the internal temporary maps into the final, optimized data
     * structures
     * (array for ID lookup, immutable map for name lookup) and marks the registry
     * as initialized.
     * No further registrations are allowed after this point.
     * This method should be called once during engine startup.
     *
     * @throws IllegalStateException if the registry is already finalized.
     */
    public void finalizeRegistry() {
        lock.writeLock().lock();
        try {
            if (initialized.get()) {
                throw new IllegalStateException("BlockRegistry is already finalized.");
            }

            if (maxBlockId < 0) {
                LOGGER.warn("BlockRegistry finalized with no blocks registered (except potentially AIR).");
                maxBlockId = 0; // Ensure array size is at least 1 for AIR
            }

            LOGGER.info("Finalizing BlockRegistry. Max Block ID: {}. Total Blocks (including AIR): {}", maxBlockId,
                    tempRegistryById.size());

            // Create the final ID lookup array
            registryById = new BlockProperties[maxBlockId + 1];
            for (Object2ObjectMap.Entry<Short, BlockProperties> entry : tempRegistryById.object2ObjectEntrySet()) {
                short id = entry.getKey();
                if (id < 0) {
                    LOGGER.error(
                            "Block ID {} is negative, which is not supported for array indexing. Skipping block '{}'.",
                            id, entry.getValue().getName());
                    continue; // Skip negative IDs for array population
                }
                if (id >= registryById.length) {
                    // This should not happen if maxBlockId was calculated correctly, but safeguard
                    // anyway.
                    LOGGER.error("Block ID {} is out of bounds for the registry array (size {}). Skipping block '{}'.",
                            id, registryById.length, entry.getValue().getName());
                    continue;
                }
                registryById[id] = entry.getValue();
            }

            // Fill gaps in the array with AIR block? Or leave null? Design decision.
            // Let's leave null for now to clearly indicate an invalid/unregistered ID.
            // If needed later, we could fill with AIR.
            // LOGGER.debug("Filling registry gaps with AIR block is currently disabled.");

            // Create the final name lookup map (make it unmodifiable)
            registryByName = Object2ObjectMaps.unmodifiable(new Object2ObjectOpenHashMap<>(tempRegistryByName));

            // Clear temporary maps to free memory
            tempRegistryById.clear();
            tempRegistryByName.clear();

            // Mark as initialized
            initialized.set(true);
            LOGGER.info("BlockRegistry finalized successfully.");

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the BlockProperties for the given block ID.
     * Access is thread-safe after the registry is finalized.
     *
     * @param id The numerical ID of the block.
     * @return The corresponding BlockProperties, or null if the ID is invalid,
     *         unregistered,
     *         or the registry is not yet finalized. Consider returning AIR for
     *         invalid IDs if needed.
     */
    public BlockProperties getBlock(short id) {
        if (!initialized.get()) {
            LOGGER.warn("Attempted to access BlockRegistry before it was finalized. Returning AIR.");
            return AIR; // Return AIR if not initialized
        }
        // No lock needed for read access on final structures
        if (id >= 0 && id < registryById.length) {
            BlockProperties props = registryById[id];
            // Return AIR if the specific ID wasn't registered? Or null? Returning null is
            // clearer.
            // return props != null ? props : AIR;
            return props; // Return null if ID is valid range but unregistered
        }
        LOGGER.trace("Requested block ID {} is out of bounds or invalid.", id);
        // Return AIR for out-of-bounds IDs? Or null? Let's return null.
        // return AIR;
        return null;
    }

    /**
     * Gets the BlockProperties for the given block name (e.g., "core:dirt").
     * Access is thread-safe after the registry is finalized.
     *
     * @param name The unique name of the block. Case-sensitive.
     * @return The corresponding BlockProperties, or null if the name is not found
     *         or the registry is not yet finalized.
     */
    public BlockProperties getBlock(String name) {
        if (!initialized.get()) {
            LOGGER.warn("Attempted to access BlockRegistry before it was finalized. Returning AIR.");
            return AIR; // Return AIR if not initialized
        }
        Validate.notEmpty(name, "Block name cannot be null or empty");
        // No lock needed for read access on final structures
        return registryByName.get(name);
    }

    /**
     * Gets the numerical ID for the given block name.
     * Access is thread-safe after the registry is finalized.
     *
     * @param name The unique name of the block.
     * @return The block ID as a short.
     * @throws IllegalArgumentException if the name is not found or the registry is
     *                                  not finalized.
     *                                  Consider returning a default ID (like AIR)
     *                                  instead of throwing.
     */
    public short getId(String name) {
        BlockProperties props = getBlock(name); // Leverages existing checks and finalization status
        if (props == null) {
            // Decide whether to throw or return AIR's ID
            // Throwing is stricter and indicates a programming error or missing definition.
            throw new IllegalArgumentException("Block name not found in registry: " + name);
            // Alternatively, return AIR's ID:
            // LOGGER.warn("Block name '{}' not found, returning AIR ID.", name);
            // return AIR.getId();
        }
        return props.getId();
    }

    /**
     * Checks if the BlockRegistry has been finalized and is ready for use.
     *
     * @return true if finalized, false otherwise.
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Gets the highest block ID registered. Returns -1 if only AIR is registered or
     * registry not finalized.
     * 
     * @return The maximum block ID.
     */
    public short getMaxBlockId() {
        if (!initialized.get()) {
            // Return the temporary max ID if queried before finalization, might be useful
            // for debugging
            lock.readLock().lock();
            try {
                return this.maxBlockId;
            } finally {
                lock.readLock().unlock();
            }
        }
        // After finalization, the length of the array determines the max possible ID
        // index
        return (short) (registryById != null ? registryById.length - 1 : -1);
    }

    /**
     * Gets the total number of registered blocks (including AIR).
     * 
     * @return The number of registered blocks.
     */
    public int getRegisteredBlockCount() {
        if (!initialized.get()) {
            lock.readLock().lock();
            try {
                return tempRegistryById.size();
            } finally {
                lock.readLock().unlock();
            }
        }
        return registryByName.size(); // Name map size reflects actual count after finalization
    }

    public Collection<BlockProperties> getAllProperties() {
        if (!initialized.get()) {
            lock.readLock().lock();
            try {
                return Collections.unmodifiableCollection(tempRegistryById.values());
            } finally {
                lock.readLock().unlock();
            }
        }
        return Collections.unmodifiableCollection(registryByName.values());
    }

    /**
     * Loads block definitions from JSON files found within the specified classpath
     * resource path.
     * This method should be called during initialization before the registry is
     * finalized.
     * It assigns sequential IDs starting after the AIR block.
     *
     * @param resourcePath The root path within the classpath to scan for *.json
     *                     files (e.g., "assets/definitions/blocks").
     */
    private void loadBlockDefinitions(String resourcePath) {
        LOGGER.info("Loading block definitions from classpath resource path: {}", resourcePath);
        lock.writeLock().lock(); // Ensure exclusive access during loading/registration
        try {
            if (initialized.get()) {
                LOGGER.error("Attempted to load block definitions after registry was finalized. Skipping.");
                return;
            }

            // Use AtomicInteger for assigning IDs sequentially, starting after AIR (ID 0)
            // Find the next available ID based on current maxBlockId
            short nextAvailableId = (short) (this.maxBlockId + 1);
            LOGGER.debug("Starting block definition loading with next available ID: {}", nextAvailableId);

            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Enumeration<URL> resources = classLoader.getResources(resourcePath);

                if (!resources.hasMoreElements()) {
                    LOGGER.warn("No block definition resources found at path: {}", resourcePath);
                    return;
                }

                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    LOGGER.debug("Scanning for block definitions in: {}", url);
                    URI uri = url.toURI();
                    Path dirPath;

                    if (uri.getScheme().equals("jar")) {
                        // Handle resources inside a JAR file
                        FileSystem fileSystem = null;
                        try {
                            fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                            dirPath = fileSystem.getPath(resourcePath);
                        } catch (FileSystemAlreadyExistsException e) {
                            fileSystem = FileSystems.getFileSystem(uri); // Get existing filesystem
                            dirPath = fileSystem.getPath(resourcePath);
                        } catch (ProviderNotFoundException e) {
                            LOGGER.error(
                                    "Could not create/access JAR file system for URI: {}. Ensure the 'jdk.zipfs' module is available.",
                                    uri, e);
                            continue; // Skip this resource URL
                        }
                        // Ensure the filesystem is closed if we opened it
                        // Note: Closing the filesystem might be problematic if other parts of the app
                        // need it.
                        // Consider a central filesystem manager if this becomes an issue.
                        // For now, we'll rely on the application lifecycle to close it implicitly,
                        // or manage it more carefully if needed. Let's try without explicit close
                        // first.
                        // AutoCloseable fileSystemClosable = fileSystem; // Use try-with-resources if
                        // closing needed

                    } else {
                        // Handle resources in a regular file system directory
                        dirPath = Paths.get(uri);
                    }

                    if (!Files.isDirectory(dirPath)) {
                        LOGGER.warn("Resource path is not a directory: {}", dirPath);
                        continue;
                    }

                    try (Stream<Path> stream = Files.walk(dirPath, 1)) { // Walk only immediate children
                        List<Path> jsonFiles = stream
                                .filter(Files::isRegularFile)
                                .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                                .collect(Collectors.toList());

                        LOGGER.info("Found {} potential block definition files in {}", jsonFiles.size(), dirPath);

                        for (Path jsonPath : jsonFiles) {
                            String fileName = jsonPath.getFileName().toString();
                            try (InputStream inputStream = Files.newInputStream(jsonPath)) {
                                BlockDefinitionPojo pojo = JSON_MAPPER.readValue(inputStream,
                                        BlockDefinitionPojo.class);

                                // --- Validation ---
                                if (pojo.name == null || pojo.name.trim().isEmpty()) {
                                    LOGGER.error("Skipping block definition from '{}': Missing or empty 'name' field.",
                                            fileName);
                                    continue;
                                }
                                if (pojo.textures == null || pojo.textures.isEmpty()) {
                                    LOGGER.error(
                                            "Skipping block definition '{}' from '{}': Missing or empty 'textures' field.",
                                            pojo.name, fileName);
                                    continue;
                                }
                                // Check for duplicate name before assigning ID
                                if (tempRegistryByName.containsKey(pojo.name)) {
                                    LOGGER.error(
                                            "Skipping block definition '{}' from '{}': Duplicate block name already registered (ID {}).",
                                            pojo.name, fileName, tempRegistryByName.get(pojo.name).getId());
                                    continue;
                                }

                                // --- Convert POJO to BlockProperties ---
                                Map<Direction, TextureRef> textureMap = new EnumMap<>(Direction.class);
                                boolean textureMappingError = false;
                                for (Map.Entry<String, String> entry : pojo.textures.entrySet()) {
                                    try {
                                        Direction direction = Direction.valueOf(entry.getKey().toUpperCase()); // Map
                                                                                                               // string
                                                                                                               // key to
                                                                                                               // Enum
                                        TextureRef textureRef = new TextureRef(entry.getValue());
                                        textureMap.put(direction, textureRef);
                                    } catch (IllegalArgumentException e) {
                                        LOGGER.error(
                                                "Skipping block definition '{}' from '{}': Invalid direction name '{}' in textures.",
                                                pojo.name, fileName, entry.getKey());
                                        textureMappingError = true;
                                        break; // Stop processing textures for this block
                                    }
                                }
                                if (textureMappingError) {
                                    continue; // Skip this block due to texture mapping error
                                }

                                // Optional: Check if all 6 directions are present
                                if (textureMap.size() != Direction.values().length) {
                                    LOGGER.warn(
                                            "Block definition '{}' from '{}' does not specify textures for all 6 directions.",
                                            pojo.name, fileName);
                                }

                                // Assign the next available ID
                                short currentId = nextAvailableId++;

                                // Create BlockProperties instance
                                BlockProperties properties = new BlockProperties(
                                        currentId,
                                        pojo.name,
                                        pojo.solid,
                                        pojo.transparent,
                                        pojo.luminance,
                                        textureMap);

                                // --- Register the Block ---
                                // Use internal maps directly as we hold the write lock
                                tempRegistryById.put(properties.getId(), properties);
                                tempRegistryByName.put(properties.getName(), properties);
                                maxBlockId = (short) Math.max(maxBlockId, properties.getId());
                                LOGGER.debug("Loaded and registered block: {} (ID: {}) from {}", properties.getName(),
                                        properties.getId(), fileName);

                            } catch (IOException e) {
                                LOGGER.error("Failed to read or parse block definition file: {}", jsonPath, e);
                            } catch (Exception e) { // Catch other potential issues during processing
                                LOGGER.error("Unexpected error processing block definition file: {}", jsonPath, e);
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed to list files in directory: {}", dirPath, e);
                    }
                }

            } catch (IOException | URISyntaxException e) {
                LOGGER.error("Failed to access block definition resources at path: {}", resourcePath, e);
            } catch (Exception e) { // Catch broader exceptions during resource scanning
                LOGGER.error("Unexpected error scanning for block definitions at path: {}", resourcePath, e);
            }

        } finally {
            lock.writeLock().unlock();
        }
        LOGGER.info("Finished loading block definitions. Current max ID: {}. Total blocks registered: {}", maxBlockId,
                tempRegistryById.size());
    }
}
