package de.heger.voxelengine.world.generation.thread;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkManager;
import de.heger.voxelengine.world.generation.tasks.ChunkGenerationTask;

public class LoggingTaskResultHandler implements TaskResultHandler {

    private static final LoggerFacade LOGGER = LoggerFacade.get(LoggingTaskResultHandler.class);

    @Override
    public void onTaskCompleted(ChunkGenerationTask task, Chunk chunk, long durationMillis) {
        LOGGER.debug("Task completed successfully for chunk: {}. Priority: {}. Resulting chunk: {}. Duration: {}ms",
                task.getChunkPos(), task.getPriority(), chunk.getPosition(), durationMillis);
        
        // Add the generated chunk to the ChunkManager
        if (chunk != null) {
            ChunkManager.getInstance().addChunk(chunk);
            LOGGER.debug("Chunk {} added to ChunkManager.", chunk.getPosition());
        } else {
            // This case should ideally not happen if a task completes successfully with a null chunk,
            // but good to log if it does. The ChunkGenerationTask should ensure a non-null chunk on success.
            LOGGER.warn("Task for {} completed but the resulting chunk was null. Not added to ChunkManager.", task.getChunkPos());
        }
        // Further actions could be taken here, e.g., adding chunk to a queue for meshing,
        // or notifying the main thread that a chunk is ready for rendering if meshing is also done.
    }

    @Override
    public void onTaskFailed(ChunkGenerationTask task, Exception exception) {
        LOGGER.error("Task failed for chunk: {}. Priority: {}. Exception: {}",
                task.getChunkPos(), task.getPriority(), exception.getMessage(), exception);
        // Further error handling or recovery mechanisms could be implemented here.
    }
} 