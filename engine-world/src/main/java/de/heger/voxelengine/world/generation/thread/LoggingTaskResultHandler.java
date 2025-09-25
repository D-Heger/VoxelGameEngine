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
        
        ChunkManager.getInstance().addChunk(chunk);
        LOGGER.debug("Chunk {} added to ChunkManager.", chunk.getPosition());
    }

    @Override
    public void onTaskFailed(ChunkGenerationTask task, Exception exception) {
        LOGGER.error("Task failed for chunk: {}. Priority: {}. Exception: {}",
                task.getChunkPos(), task.getPriority(), exception.getMessage(), exception);
        // Further error handling or recovery mechanisms could be implemented here.
    }
} 