package de.heger.voxelengine.physics;

import de.heger.voxelengine.world.entity.Entity;

/**
 * Very lightweight physics integrator responsible for applying gravity and
 * advancing an {@link Entity}'s position each simulation step.
 *
 * <p>This is a placeholder implementation that will be replaced by a proper
 * physics system (tasks P4-T8). For now it only supports vertical gravity and
 * simple Euler integration without collision.</p>
 */
public class PhysicsService {

    /** Gravity acceleration in blocks/second² (approx. 9.81). */
    public static final float GRAVITY = -9.81f;

    private final CollisionResolver collisionResolver;

    public PhysicsService(CollisionResolver collisionResolver) {
        this.collisionResolver = collisionResolver;
    }

    /**
     * Advances the given entity by one physics step.
     *
     * @param entity       the entity to update
     * @param deltaSeconds timestep in seconds
     * @param applyGravity whether gravity should be applied this step
     * @param applyCollision whether collision should be applied this step
     */
    public void update(Entity entity, float deltaSeconds, boolean applyGravity, boolean applyCollision) {
        if (entity == null) return;

        // 1. Integrate velocity due to forces (gravity)
        if (applyGravity) {
            entity.getVelocity().y += GRAVITY * deltaSeconds;
        }

        // 2. Desired movement this tick (before collisions)
        org.joml.Vector3f desiredMove = new org.joml.Vector3f(entity.getVelocity()).mul(deltaSeconds);

        if (applyCollision && collisionResolver != null) {
            collisionResolver.resolve(entity, desiredMove);
        } else {
            // No collision resolver -> simple Euler step
            entity.integrate(deltaSeconds);
        }
    }

    // Convenience overload (collision on by default)
    public void update(Entity entity, float deltaSeconds, boolean applyGravity) {
        update(entity, deltaSeconds, applyGravity, true);
    }
} 