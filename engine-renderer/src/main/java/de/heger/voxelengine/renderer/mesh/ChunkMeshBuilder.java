package de.heger.voxelengine.renderer.mesh;

import de.heger.voxelengine.core.math.Vec3i;
import de.heger.voxelengine.world.block.BlockProperties;
import de.heger.voxelengine.world.block.BlockRegistry;
import de.heger.voxelengine.world.block.TextureRef;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.chunk.ChunkPos;
import de.heger.voxelengine.world.chunk.CoordinateUtils;
import de.heger.voxelengine.world.chunk.Direction;

import java.util.Map;
import java.util.HashMap;

/**
 * Builds {@link MeshData} for a given {@link Chunk}.
 * It collects all visible faces of the blocks within the chunk and consolidates them into mesh data,
 * grouped by texture. This class is designed to be used by background threads for mesh generation.
 */
public class ChunkMeshBuilder {

    // Vertex data for each face, relative to block center (0,0,0)
    // Each vertex: 3 pos, 2 uv, 3 normal
    private static final float[] UP_FACE_VERTICES = {
        // Positions          // TexCoords    // Normals (0,1,0)
        -0.5f, 0.5f,  0.5f,   0.0f, 0.0f,     0.0f, 1.0f, 0.0f, // Top-Front-Left
         0.5f, 0.5f,  0.5f,   1.0f, 0.0f,     0.0f, 1.0f, 0.0f, // Top-Front-Right
         0.5f, 0.5f, -0.5f,   1.0f, 1.0f,     0.0f, 1.0f, 0.0f, // Top-Back-Right
        -0.5f, 0.5f, -0.5f,   0.0f, 1.0f,     0.0f, 1.0f, 0.0f  // Top-Back-Left
    };
    private static final float[] DOWN_FACE_VERTICES = {
        // Positions          // TexCoords    // Normals (0,-1,0)
        -0.5f, -0.5f, -0.5f,   0.0f, 1.0f,     0.0f, -1.0f, 0.0f, // Bottom-Back-Left (v0)
         0.5f, -0.5f, -0.5f,   1.0f, 1.0f,     0.0f, -1.0f, 0.0f, // Bottom-Back-Right (v1)
         0.5f, -0.5f,  0.5f,   1.0f, 0.0f,     0.0f, -1.0f, 0.0f, // Bottom-Front-Right (v2)
        -0.5f, -0.5f,  0.5f,   0.0f, 0.0f,     0.0f, -1.0f, 0.0f  // Bottom-Front-Left  (v3)
    };
    private static final float[] NORTH_FACE_VERTICES = { // Z-
        // Positions          // TexCoords    // Normals (0,0,-1)
         0.5f, -0.5f, -0.5f,   0.0f, 1.0f,     0.0f, 0.0f, -1.0f, // Bottom-Right
        -0.5f, -0.5f, -0.5f,   1.0f, 1.0f,     0.0f, 0.0f, -1.0f, // Bottom-Left
        -0.5f,  0.5f, -0.5f,   1.0f, 0.0f,     0.0f, 0.0f, -1.0f, // Top-Left
         0.5f,  0.5f, -0.5f,   0.0f, 0.0f,     0.0f, 0.0f, -1.0f  // Top-Right
    };
    private static final float[] SOUTH_FACE_VERTICES = { // Z+
        // Positions          // TexCoords    // Normals (0,0,1)
        -0.5f, -0.5f,  0.5f,   0.0f, 1.0f,     0.0f, 0.0f, 1.0f,  // Bottom-Left
         0.5f, -0.5f,  0.5f,   1.0f, 1.0f,     0.0f, 0.0f, 1.0f,  // Bottom-Right
         0.5f,  0.5f,  0.5f,   1.0f, 0.0f,     0.0f, 0.0f, 1.0f,  // Top-Right
        -0.5f,  0.5f,  0.5f,   0.0f, 0.0f,     0.0f, 0.0f, 1.0f   // Top-Left
    };
    private static final float[] WEST_FACE_VERTICES = { // X-
        // Positions          // TexCoords    // Normals (-1,0,0)
        -0.5f, -0.5f, -0.5f,   0.0f, 1.0f,    -1.0f, 0.0f, 0.0f, // Bottom-Back
        -0.5f, -0.5f,  0.5f,   1.0f, 1.0f,    -1.0f, 0.0f, 0.0f, // Bottom-Front
        -0.5f,  0.5f,  0.5f,   1.0f, 0.0f,    -1.0f, 0.0f, 0.0f, // Top-Front
        -0.5f,  0.5f, -0.5f,   0.0f, 0.0f,    -1.0f, 0.0f, 0.0f  // Top-Back
    };
    private static final float[] EAST_FACE_VERTICES = { // X+
        // Positions          // TexCoords    // Normals (1,0,0)
         0.5f, -0.5f,  0.5f,   0.0f, 1.0f,     1.0f, 0.0f, 0.0f, // Bottom-Front
         0.5f, -0.5f, -0.5f,   1.0f, 1.0f,     1.0f, 0.0f, 0.0f, // Bottom-Back
         0.5f,  0.5f, -0.5f,   1.0f, 0.0f,     1.0f, 0.0f, 0.0f, // Top-Back
         0.5f,  0.5f,  0.5f,   0.0f, 0.0f,     1.0f, 0.0f, 0.0f  // Top-Front
    };

    private static final int[] FACE_INDICES = {0, 1, 2, 0, 2, 3}; // Standard quad indices (CCW)
    private static final int FLOATS_PER_VERTEX = 3 + 2 + 3; // Pos (3) + UV (2) + Normal (3)
    private static final String DEFAULT_TEXTURE_KEY = "core:block/unknown_texture_fallback";
    
    // Pre-allocated arrays for bulk operations to avoid repeated allocations
    private static final ThreadLocal<float[]> TEMP_VERTICES = ThreadLocal.withInitial(() -> new float[32]); // 4 vertices * 8 floats per vertex
    private static final ThreadLocal<int[]> TEMP_INDICES = ThreadLocal.withInitial(() -> new int[6]); // 6 indices per face

    /**
     * Generates a map of texture names to {@link MeshData} objects for the given chunk.
     * This method is safe to call from multiple threads as it only processes chunk data
     * and does not perform any OpenGL operations.
     *
     * @param chunk The chunk to generate mesh data for.
     * @param chunkManager The chunk manager to access neighbor chunks.
     * @param blockRegistry The block registry to access block properties.
     * @return A map where keys are texture names and values are the corresponding MeshData objects.
     *         Returns an empty map if the input is invalid or no visible geometry is found.
     */
    public static Map<String, MeshData> generateMeshDataByTexture(Chunk chunk, ChunkManager chunkManager, BlockRegistry blockRegistry) {
        if (chunk == null || chunkManager == null || blockRegistry == null) {
            return new HashMap<>(); // Return an empty map
        }

        Map<String, MeshData> meshDataMap = new HashMap<>();
        // ChunkPos chunkPos = chunk.getPosition(); // Not directly used in this version of mesh building

        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    Vec3i localBlockPos = new Vec3i(x, y, z);
                    BlockProperties currentBlockProps = chunk.getBlockProperties(localBlockPos);

                    if (currentBlockProps == null || currentBlockProps.getId() == BlockRegistry.AIR.getId()) {
                        continue;
                    }

                    for (Direction faceDir : Direction.values()) {
                        if (isFaceVisible(chunk, localBlockPos, faceDir, chunkManager, blockRegistry)) {
                            TextureRef texRef = currentBlockProps.getTexture(faceDir);
                            String textureName = (texRef != null && texRef.getName() != null) ? texRef.getName() : DEFAULT_TEXTURE_KEY;

                            MeshData currentMeshData = meshDataMap.computeIfAbsent(textureName, k -> new MeshData());

                            float[] faceVertices = getFaceVertices(faceDir);

                            // Use thread-local pre-allocated arrays for better performance
                            float[] transformedVertices = TEMP_VERTICES.get();
                            int[] transformedIndices = TEMP_INDICES.get();

                            // Transform vertex data in bulk
                            for (int i = 0; i < faceVertices.length; i += FLOATS_PER_VERTEX) {
                                // Transform position coordinates
                                transformedVertices[i] = faceVertices[i] + x + 0.5f;     // X
                                transformedVertices[i + 1] = faceVertices[i + 1] + y + 0.5f; // Y
                                transformedVertices[i + 2] = faceVertices[i + 2] + z + 0.5f; // Z
                                // Copy UV and normal data as-is
                                transformedVertices[i + 3] = faceVertices[i + 3]; // U
                                transformedVertices[i + 4] = faceVertices[i + 4]; // V
                                transformedVertices[i + 5] = faceVertices[i + 5]; // NX
                                transformedVertices[i + 6] = faceVertices[i + 6]; // NY
                                transformedVertices[i + 7] = faceVertices[i + 7]; // NZ
                            }

                            // Add all vertices in bulk for better performance
                            currentMeshData.addVertices(transformedVertices, 0, faceVertices.length);

                            // Transform indices in bulk
                            for (int i = 0; i < FACE_INDICES.length; i++) {
                                transformedIndices[i] = currentMeshData.currentIndexOffset + FACE_INDICES[i];
                            }

                            // Add all indices in bulk for better performance
                            currentMeshData.addIndices(transformedIndices, 0, FACE_INDICES.length);
                            currentMeshData.incrementVertexOffset(4); // Each face adds 4 vertices
                        }
                    }
                }
            }
        }

        //Map<String, ChunkMesh> finalMeshes = new HashMap<>();
        for (Map.Entry<String, MeshData> entry : meshDataMap.entrySet()) {
            MeshData md = entry.getValue();
            if (!md.isEmpty()) {
                // finalMeshes.put(entry.getKey(), new ChunkMesh(md.getVertexBuffer(), md.getIndexBuffer()));
                // We no longer create ChunkMesh here. The caller (Renderer) will do it on the main thread.
            }
        }
        // Return the meshDataMap directly
        return meshDataMap;
    }

    private static float[] getFaceVertices(Direction direction) {
        switch (direction) {
            case UP:    return UP_FACE_VERTICES;
            case DOWN:  return DOWN_FACE_VERTICES;
            case NORTH: return NORTH_FACE_VERTICES;
            case SOUTH: return SOUTH_FACE_VERTICES;
            case WEST:  return WEST_FACE_VERTICES;
            case EAST:  return EAST_FACE_VERTICES;
            default:    throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    /**
     * Determines if a specific face of a block is visible.
     * A face is visible if the block in the direction of the face is transparent or outside loaded chunks.
     * This is a modified version of the logic in Renderer.isFaceVisible.
     */
    private static boolean isFaceVisible(Chunk currentChunk, Vec3i localBlockPosInCurrentChunk, Direction faceDir,
                                         ChunkManager chunkManager, BlockRegistry blockRegistry) {
        Vec3i neighborOffset = faceDir.getOffset();
        int neighborLocalX = localBlockPosInCurrentChunk.x + neighborOffset.x;
        int neighborLocalY = localBlockPosInCurrentChunk.y + neighborOffset.y;
        int neighborLocalZ = localBlockPosInCurrentChunk.z + neighborOffset.z;

        // Cull downward faces at world bottom (y < 0 in world coordinates)
        // Chunk's Y position * Chunk.SIZE_Y gives the world Y of the chunk's base.
        // Add neighborLocalY to get world Y of the neighbor block.
        int worldNeighborY = currentChunk.getPosition().y * Chunk.SIZE_Y + neighborLocalY;
        if (faceDir == Direction.DOWN && worldNeighborY < 0) {
            return false;
        }
        
        // Check for neighbors at max world height (e.g. Y = 255 for 16 chunks high)
        // TODO: Make MAX_WORLD_HEIGHT configurable and accessible here
        // For now, assume a max build height relative to chunk system rather than absolute.
        // If neighborLocalY goes above Chunk.SIZE_Y-1 and it's an UP face, it might be exposed.
        // This needs more robust handling if there's a strict world height limit.

        BlockProperties neighborBlockProps;

        if (neighborLocalX >= 0 && neighborLocalX < Chunk.SIZE_X &&
            neighborLocalY >= 0 && neighborLocalY < Chunk.SIZE_Y &&
            neighborLocalZ >= 0 && neighborLocalZ < Chunk.SIZE_Z) {
            // Neighbor is within the same chunk
            neighborBlockProps = currentChunk.getBlockProperties(neighborLocalX, neighborLocalY, neighborLocalZ);
        } else {
            // Neighbor is in an adjacent chunk
            Vec3i currentBlockWorldPos = CoordinateUtils.localToWorldCoords(currentChunk.getPosition(), localBlockPosInCurrentChunk);
            Vec3i neighborBlockWorldPos = new Vec3i(
                currentBlockWorldPos.x + neighborOffset.x,
                currentBlockWorldPos.y + neighborOffset.y,
                currentBlockWorldPos.z + neighborOffset.z
            );

            ChunkPos neighborChunkPos = CoordinateUtils.worldToChunkCoords(neighborBlockWorldPos);
            Vec3i localPosInNeighborChunk = CoordinateUtils.worldToLocalCoords(neighborBlockWorldPos);

            Chunk neighborChunk = chunkManager.getChunk(neighborChunkPos);

            if (neighborChunk == null) {
                return true; // Neighbor chunk not loaded, face is visible (exposed to void)
            }
            neighborBlockProps = neighborChunk.getBlockProperties(localPosInNeighborChunk);
        }

        // If neighborBlockProps is null (e.g., BlockRegistry.getBlock(id) returned null for an unregistered ID),
        // treat it as AIR, which is transparent.
        if (neighborBlockProps == null) {
             return true; // Treat as transparent if properties are null
        }
        return neighborBlockProps.isTransparent();
    }
} 