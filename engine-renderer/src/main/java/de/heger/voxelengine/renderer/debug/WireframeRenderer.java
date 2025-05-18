package de.heger.voxelengine.renderer.debug;

import de.heger.voxelengine.renderer.mesh.ChunkMesh;
import org.lwjgl.opengl.GL11;

/**
 * A utility class for rendering chunk meshes in wireframe mode.
 * This is primarily used for debugging and visualization purposes.
 */
public class WireframeRenderer {

    /**
     * Renders the given chunk mesh in wireframe mode.
     * @param mesh The chunk mesh to render.
     */
    public void render(ChunkMesh mesh) {
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        // Enable wireframe mode
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);

        // Render the mesh
        mesh.render();

        // Disable wireframe mode to return to default rendering
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
    }
} 