package de.heger.voxelengine.game;

import de.heger.voxelengine.platform.InputManager;
import de.heger.voxelengine.renderer.camera.Camera;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles player-centric input and converts it to velocity updates that will be
 * processed by the physics system.
 */
public class PlayerController {

    private static final float WALK_SPEED   = 5.0f;  // blocks / s
    private static final float SPRINT_MULT  = 2.5f;   // sprint velocity factor
    private static final float FLY_SPEED    = 10.0f;  // blocks / s when flying
    private static final float CROUCH_MULT  = 0.3f;   // crouch slows movement
    private static final float JUMP_VELOCITY = 7.0f;  // upwards velocity when jumping

    private final Player player;
    private final Camera camera;
    private boolean flying = false;
    private boolean prevF9State = false;
    private boolean noClip = false;
    private boolean prevF10State = false;
    private boolean crouching = false;
    private boolean prevSpaceState = false;

    public PlayerController(Player player, Camera camera) {
        this.player = player;
        this.camera = camera;
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isNoClipEnabled() {
        return noClip;
    }

    public boolean isCrouching() {
        return crouching;
    }

    /**
     * Reads the current input state and updates the Player's velocity.
     */
    public void update(InputManager input, float deltaSeconds) {
        // --- Flying toggle (F9) ---
        boolean f9Pressed = input.isKeyPressed(GLFW_KEY_F9);
        if (f9Pressed && !prevF9State) {
            flying = !flying; // toggle on rising edge
        }
        prevF9State = f9Pressed;

        // --- No-clip toggle (F10) ---
        boolean f10Pressed = input.isKeyPressed(GLFW_KEY_F10);
        if (f10Pressed && !prevF10State) {
            noClip = !noClip;
        }
        prevF10State = f10Pressed;

        // Reset horizontal velocity each frame (acceleration not modelled yet)
        Vector3f vel = player.getVelocity();
        vel.set(0, vel.y, 0); // keep vertical component from gravity/jumps

        // Determine movement speed considering sprint / crouch
        float speed = flying ? FLY_SPEED : WALK_SPEED;
        if (input.isKeyPressed(GLFW_KEY_LEFT_CONTROL)) {
            speed *= SPRINT_MULT;
        }
        if (!flying && input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            crouching = true;
            speed *= CROUCH_MULT;
        } else {
            crouching = false;
        }

        Vector3f moveDir = new Vector3f();
        Vector3f front = new Vector3f(camera.getFront());
        front.y = 0; // ignore vertical component for horizontal movement
        front.normalize();
        Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();

        if (flying) {
            if (input.isKeyPressed(GLFW_KEY_SPACE)) {
                moveDir.add(0, 1, 0);
            }
            if (input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
                moveDir.add(0, -1, 0);
            }
        }

        if (input.isKeyPressed(GLFW_KEY_W)) {
            moveDir.add(front);
        }
        if (input.isKeyPressed(GLFW_KEY_S)) {
            moveDir.sub(front);
        }
        if (input.isKeyPressed(GLFW_KEY_A)) {
            moveDir.sub(right);
        }
        if (input.isKeyPressed(GLFW_KEY_D)) {
            moveDir.add(right);
        }

        if (moveDir.lengthSquared() > 0) {
            moveDir.normalize(speed);
            vel.x = moveDir.x;
            vel.z = moveDir.z;
            if (flying) {
                vel.y = moveDir.y; // in flying, override vertical velocity
            }
        } else {
            // No horizontal input: apply friction (primitive)
            vel.x = 0;
            vel.z = 0;
            if (flying) {
                vel.y = 0;
            }
        }

        // ----------------------------------------------------
        // Jumping (space) when not flying and on ground
        // ----------------------------------------------------
        boolean spacePressed = input.isKeyPressed(GLFW_KEY_SPACE);
        if (!flying && spacePressed && !prevSpaceState && Math.abs(player.getVelocity().y) < 0.001f) {
            player.getVelocity().y = JUMP_VELOCITY;
        }
        prevSpaceState = spacePressed;
    }
} 