package de.heger.voxelengine.renderer.camera;

import de.heger.voxelengine.platform.InputManager;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {

    private Vector3f position;
    private float pitch; // Rotation around the X-axis (looking up/down)
    private float yaw;   // Rotation around the Y-axis (looking left/right)
    // Roll (rotation around Z) is typically not needed for FPS cameras

    private final Vector3f front;
    private final Vector3f up;
    private final Vector3f right;
    private final Vector3f worldUp;

    private float movementSpeed = 5.0f; // Units per second
    private float mouseSensitivity = 0.1f;
    private float viewDistance = 200.0f; // Default view distance
    private float fov = 75.0f; // Default field of view in degrees
    private Matrix4f projectionMatrix;
    private float aspectRatio = 16.0f / 9.0f; // Default aspect ratio

    private static final float SPRINT_MULTIPLIER = 5.0f;

    public Camera() {
        this(new Vector3f(0.0f, 80.0f, 0.0f)); // Default position
    }

    public Camera(Vector3f position) {
        this.position = position;
        this.worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
        this.yaw = -90.0f; // Start looking towards -Z
        this.pitch = 0.0f;
        this.front = new Vector3f();
        this.up = new Vector3f();
        this.right = new Vector3f();
        updateCameraVectors();
        updateProjectionMatrix(); // Initialize projection matrix
    }

    public Matrix4f getViewMatrix() {
        // Eye: Camera position
        // Center: Position + Front vector
        // Up: Camera's local up vector
        return new Matrix4f().lookAt(position, new Vector3f(position).add(front), up);
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
        updateProjectionMatrix();
    }

    public void setViewDistance(float viewDistance) {
        this.viewDistance = viewDistance;
        updateProjectionMatrix();
    }

    public void setFov(float fov) {
        this.fov = fov;
        updateProjectionMatrix();
    }

    private void updateProjectionMatrix() {
        // FOV (field of view), aspect ratio, near plane, far plane
        projectionMatrix = new Matrix4f().perspective(
            (float) Math.toRadians(this.fov), // FOV
            aspectRatio,
            0.1f, // Near plane
            viewDistance // Far plane (view distance)
        );
    }

    public void processKeyboard(InputManager inputManager, float deltaTime) {
        float velocity = movementSpeed * deltaTime;
        if (inputManager.isKeyPressed(GLFW_KEY_LEFT_CONTROL)) {
            velocity *= SPRINT_MULTIPLIER;
        }
        if (inputManager.isKeyPressed(GLFW_KEY_W)) {
            position.add(new Vector3f(front).mul(velocity));
        }
        if (inputManager.isKeyPressed(GLFW_KEY_S)) {
            position.sub(new Vector3f(front).mul(velocity));
        }
        if (inputManager.isKeyPressed(GLFW_KEY_A)) {
            position.sub(new Vector3f(right).mul(velocity));
        }
        if (inputManager.isKeyPressed(GLFW_KEY_D)) {
            position.add(new Vector3f(right).mul(velocity));
        }
        if (inputManager.isKeyPressed(GLFW_KEY_SPACE)) {
            position.add(new Vector3f(worldUp).mul(velocity)); // Move directly up globally
        }
        if (inputManager.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            position.sub(new Vector3f(worldUp).mul(velocity)); // Move directly down globally
        }
    }

    public void processMouseMovement(double xoffset, double yoffset) {
        xoffset *= mouseSensitivity;
        yoffset *= mouseSensitivity;

        yaw += xoffset;
        pitch += yoffset;

        // Constrain pitch to avoid flipping
        if (pitch > 89.0f) {
            pitch = 89.0f;
        }
        if (pitch < -89.0f) {
            pitch = -89.0f;
        }

        // Update Front, Right and Up Vectors using the updated Euler angles
        updateCameraVectors();
    }

    private void updateCameraVectors() {
        // Calculate the new Front vector
        Vector3f newFront = new Vector3f();
        newFront.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front.set(newFront).normalize();

        // Also re-calculate the Right and Up vector
        // Normalize the vectors, because their length gets closer to 0 the more you look up or down which results in slower movement.
        right.set(front).cross(worldUp).normalize();
        up.set(right).cross(front).normalize();
    }

    // --- Getters ---
    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getFront() {
        return front;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getFov() {
        return fov;
    }

    public float getViewDistance() {
        return viewDistance;
    }

    public void setMovementSpeed(float movementSpeed) {
        this.movementSpeed = movementSpeed;
    }

    public void setMouseSensitivity(float mouseSensitivity) {
        this.mouseSensitivity = mouseSensitivity;
    }
}
