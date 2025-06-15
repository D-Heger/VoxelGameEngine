package de.heger.voxelengine.game;

import de.heger.voxelengine.world.entity.Entity;
import org.joml.Vector3f;

/**
 * The concrete representation of the user inside the voxel world.
 * <p>
 * For now the {@code Player} only extends {@link Entity} with pre-defined
 * hit-box dimensions and placeholders for future player-specific state such as
 * health, inventory, game-mode, etc.
 * </p>
 */
public class Player extends Entity {

    public static final float PLAYER_WIDTH  = 0.6f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_DEPTH  = 0.6f;

    /**
     * Creates a new {@code Player} at the given spawn position.
     *
     * @param spawnPosition initial position in world coordinates.
     */
    public Player(Vector3f spawnPosition) {
        super(spawnPosition, PLAYER_WIDTH, PLAYER_HEIGHT, PLAYER_DEPTH);
    }

    // ---------------------------------------------------------------------
    // Placeholder for future player-specific features
    // ---------------------------------------------------------------------

    private int health = 20;

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = Math.max(0, health);
    }
} 