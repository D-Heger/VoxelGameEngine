package de.heger.voxelengine.world.block;

/**
 * A by-name reference to a texture used by a block face.
 *
 * <p>Block definitions do not embed pixels; they name the texture they want
 * (for example {@code "grass_top"}). This lightweight, immutable holder
 * carries that name from the JSON definition to the renderer, which resolves
 * it against the loaded texture atlas at runtime.</p>
 */
public class TextureRef {
    private final String name;
    
    public TextureRef(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "TextureRef{" +
                "name='" + name + '\'' +
                '}';
    }
}