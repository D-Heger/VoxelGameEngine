package de.heger.voxelengine.renderer.culling;

import de.heger.voxelengine.core.logging.LoggerFacade;
import de.heger.voxelengine.renderer.camera.Camera;
import de.heger.voxelengine.renderer.shader.ShaderProgram;
import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import de.heger.voxelengine.renderer.management.ChunkMeshManager;
import de.heger.voxelengine.world.chunk.Chunk;
import de.heger.voxelengine.world.chunk.ChunkPos;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.nio.IntBuffer;
import java.util.*;
import java.util.function.IntConsumer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL31.*;

/**
 * Experimental GPU-driven occlusion culler that implements a Hierarchical-Z buffer (HZB) pipeline.
 * <p>
 * – Step 1: Depth pre-pass rendering all opaque chunk geometry into a dedicated depth texture.<br>
 * – Step 2: Generation of a Z-pyramid (mip-mapped depth texture with MAX reduction).<br>
 * – Step 3: GPU compute shader that tests a list of chunk AABBs against the pyramid and marks
 *           visibility in an SSBO that is read back by the CPU (or, in the future, consumed via
 *           indirect draw calls).
 * <p>
 * For the first iteration this class still falls back to the existing CPU occlusion heuristic once
 * the GPU resources have been prepared. This ensures functional parity while we integrate and
 * debug the new path.
 */
public class HZBOcclusionCuller extends OcclusionCuller {

    private static final LoggerFacade logger = LoggerFacade.get(HZBOcclusionCuller.class);

    // -------------- Runtime configuration --------------
    private static final boolean READ_BACK_RESULTS_CPU = true; // Future: use GPU indirect draws

    // -------------- GL resources --------------
    private final int depthFbo;
    private final int depthTexture;

    // Viewport the depth texture was created for
    private final int textureWidth;
    private final int textureHeight;

    // Shaders – kept as placeholders for now so that compilation succeeds.
    private ShaderProgram depthPrePassShader;
    private ShaderProgram visibilityComputeShader;

    // SSBOs
    private int aabbSsbo;
    private int visibilitySsbo;

    private boolean initialized = false;

    // Reusable buffer for read-back of visibility data
    private IntBuffer visibilityReadBack;

    private final Camera camera;
    private final ChunkMeshManager meshManager;

    public HZBOcclusionCuller(Camera camera, ChunkMeshManager meshManager, int viewportWidth, int viewportHeight) {
        this.camera = camera;
        this.meshManager = meshManager;
        this.textureWidth = Math.max(1, viewportWidth);
        this.textureHeight = Math.max(1, viewportHeight);

        // -------- Allocate depth buffer --------
        depthFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);

        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, textureWidth, textureHeight, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Failed to create depth framebuffer for HZB occlusion culling (status={})", status);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // -------- Placeholders for shaders & buffers --------
        setupShaders();

        initialized = true;
        logger.info("Initialized HZB occlusion culler ({}x{})", textureWidth, textureHeight);
    }

    private void setupShaders() {
        try {
            // --- Depth pre-pass shader (simple vertex passthrough) ---
            depthPrePassShader = new ShaderProgram();
            String vsSrc = "#version 330 core\n" +
                    "layout(location = 0) in vec3 inPosition;\n" +
                    "uniform mat4 model;\n" +
                    "uniform mat4 viewProj;\n" +
                    "void main(){\n" +
                    "    gl_Position = viewProj * model * vec4(inPosition,1.0);\n" +
                    "}";
            String fsSrc = "#version 330 core\nvoid main(){}";
            depthPrePassShader.createVertexShader(vsSrc);
            depthPrePassShader.createFragmentShader(fsSrc);
            depthPrePassShader.link();
            depthPrePassShader.createUniform("model");
            depthPrePassShader.createUniform("viewProj");

            // --- Visibility compute shader (placeholder always visible) ---
            visibilityComputeShader = new ShaderProgram();
            String csSrc = "#version 430 core\n" +
                    "layout(local_size_x = 64) in;\n" +
                    "layout(std430, binding = 0) buffer AABBInput{ uint dummy[]; };\n" +
                    "layout(std430, binding = 1) buffer Visibility{ uint visible[]; };\n" +
                    "void main(){ uint idx = gl_GlobalInvocationID.x; visible[idx] = 1u; }";
            visibilityComputeShader.createComputeShader(csSrc);
            visibilityComputeShader.link();
        } catch (Exception e) {
            logger.error("Failed to compile HZB shaders", e);
        }
    }

    @Override
    public Collection<Chunk> filterOccludedChunks(Collection<Chunk> chunks, Vector3f cameraPosition, Vector3f cameraDirection, IntConsumer occludedChunkCounter) {
        if (!initialized || chunks == null || chunks.isEmpty()) {
            return super.filterOccludedChunks(chunks, cameraPosition, cameraDirection, occludedChunkCounter);
        }

        // ---------- 1) Depth pre-pass ----------
        performDepthPrePass(chunks);

        // ---------- 2) Generate Z-pyramid ----------
        generateZPyramid();

        // ---------- 3) GPU visibility test ----------
        Set<Chunk> visible = performVisibilityCompute(chunks);

        int culled = chunks.size() - visible.size();
        if (occludedChunkCounter != null) occludedChunkCounter.accept(culled);
        return visible;
    }

    private void performDepthPrePass(Collection<Chunk> chunks) {
        glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glViewport(0, 0, textureWidth, textureHeight);
        glClear(GL_DEPTH_BUFFER_BIT);
        glColorMask(false, false, false, false);

        depthPrePassShader.bind();
        org.joml.Matrix4f viewProj = new org.joml.Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
        depthPrePassShader.setUniform("viewProj", viewProj);

        for (Chunk chunk : chunks) {
            Map<String, ChunkMesh> meshes = meshManager.getMeshesForChunk(chunk.getPosition());
            if (meshes == null) continue;
            for (ChunkMesh mesh : meshes.values()) {
                if (mesh.isEmpty()) continue;

                org.joml.Matrix4f model = new org.joml.Matrix4f().translation(
                        chunk.getPosition().x * Chunk.SIZE_X,
                        chunk.getPosition().y * Chunk.SIZE_Y,
                        chunk.getPosition().z * Chunk.SIZE_Z);
                depthPrePassShader.setUniform("model", model);
                glBindVertexArray(mesh.getVaoId());
                mesh.render();
            }
        }
        glBindVertexArray(0);
        depthPrePassShader.unbind();

        // Re-enable color writes for normal rendering
        glColorMask(true, true, true, true);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void generateZPyramid() {
        // Placeholder implementation – simple mipmap generation.
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glGenerateMipmap(GL_TEXTURE_2D);
    }

    private Set<Chunk> performVisibilityCompute(Collection<Chunk> chunks) {
        int chunkCount = chunks.size();
        allocateSsboIfNeeded(chunkCount);

        // Upload dummy data for now (only size matters for placeholder shader)
        IntBuffer dummy = org.lwjgl.BufferUtils.createIntBuffer(chunkCount);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, aabbSsbo);
        glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, dummy);

        // Bind SSBOs
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, aabbSsbo);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, visibilitySsbo);

        // Dispatch
        visibilityComputeShader.bind();
        int workGroups = (chunkCount + 63) / 64;
        glDispatchCompute(workGroups, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
        visibilityComputeShader.unbind();

        // --- Read back ---
        Set<Chunk> visibleChunks = new HashSet<>();
        if (READ_BACK_RESULTS_CPU) {
            if (visibilityReadBack == null || visibilityReadBack.capacity() < chunkCount) {
                visibilityReadBack = org.lwjgl.BufferUtils.createIntBuffer(chunkCount);
            }
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, visibilitySsbo);

            // Prepare buffer for read-back: ensure its limit equals the number of ints we will read
            visibilityReadBack.clear();
            visibilityReadBack.limit(chunkCount);

            glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, visibilityReadBack);
            visibilityReadBack.rewind();

            for (Chunk chunk : chunks) {
                int flag = visibilityReadBack.get();
                if (flag != 0) visibleChunks.add(chunk);
            }
        } else {
            // Fallback: assume all visible
            visibleChunks.addAll(chunks);
        }
        return visibleChunks;
    }

    private void allocateSsboIfNeeded(int chunkCount) {
        int requiredBytes = chunkCount * 4; // One uint per chunk for the placeholder implementation

        if (aabbSsbo == 0) {
            aabbSsbo = glGenBuffers();
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, aabbSsbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, requiredBytes, GL_DYNAMIC_DRAW);

        if (visibilitySsbo == 0) {
            visibilitySsbo = glGenBuffers();
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, visibilitySsbo);
        glBufferData(GL_SHADER_STORAGE_BUFFER, requiredBytes, GL_DYNAMIC_DRAW);
    }

    public void cleanup() {
        if (depthFbo != 0) glDeleteFramebuffers(depthFbo);
        if (depthTexture != 0) glDeleteTextures(depthTexture);
        if (aabbSsbo != 0) glDeleteBuffers(aabbSsbo);
        if (visibilitySsbo != 0) glDeleteBuffers(visibilitySsbo);
        if (depthPrePassShader != null) depthPrePassShader.cleanup();
        if (visibilityComputeShader != null) visibilityComputeShader.cleanup();
    }
} 