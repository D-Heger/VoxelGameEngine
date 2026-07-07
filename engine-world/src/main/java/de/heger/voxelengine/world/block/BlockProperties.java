package de.heger.voxelengine.world.block;

import de.heger.voxelengine.core.utils.Validate;
import de.heger.voxelengine.world.chunk.Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable class holding all properties for a single block type.
 * Instances of this class are created and managed by the BlockRegistry.
 *
 * Based on Subtask P3-T4.2 and block_registry_design.md.
 */
public final class BlockProperties {

    private final short id;
    private final String name;
    private final boolean solid;
    private final boolean transparent;
    private final byte luminance; // 0-15
    private final Map<Direction, TextureRef> textures;

    /**
     * Creates a new BlockProperties instance.
     *
     * @param id The numerical ID assigned to this block type.
     * @param name The unique identifier string (e.g., "core:block/dirt"). Must not be null or empty.
     * @param solid True if the block obstructs movement and physics.
     * @param transparent True if the block allows light to pass through and doesn't fully obscure vision.
     * @param luminance The light level emitted by the block (0-15).
     * @param textures A map providing texture information for each face. Must not be null and should ideally contain entries for all 6 directions. A defensive copy is made.
     */
    public BlockProperties(short id, String name, boolean solid, boolean transparent, byte luminance, Map<Direction, TextureRef> textures) {
        Validate.notEmpty(name, "Block name cannot be null or empty");
        Validate.notNull(textures, "Textures map cannot be null");
        // Basic validation for luminance range
        if (luminance < 0 || luminance > 15) {
            throw new IllegalArgumentException("Luminance must be between 0 and 15, but was " + luminance);
        }

        this.id = id;
        this.name = name;
        this.solid = solid;
        this.transparent = transparent;
        this.luminance = luminance;
        // Defensive copy and make unmodifiable externally
        this.textures = Collections.unmodifiableMap(new EnumMap<>(textures));

        // Optional: Validate that all directions are present in the map, depending on requirements
        // if (this.textures.size() != Direction.values().length) {
        //     System.err.println("Warning: Texture map for block '" + name + "' does not contain entries for all directions.");
        // }
    }

    /** @return The numerical ID assigned to this block type. */
    public short getId() {
        return id;
    }

    /** @return The unique identifier string (e.g., "core:dirt"). */
    public String getName() {
        return name;
    }

    /** @return True if the block obstructs movement and physics. */
    public boolean isSolid() {
        return solid;
    }

    /** @return True if the block allows light to pass through and doesn't fully obscure vision. */
    public boolean isTransparent() {
        return transparent;
    }

    /** @return The light level emitted by the block (0-15). */
    public byte getLuminance() {
        return luminance;
    }

    /**
     * Gets the texture region for a specific face of the block.
     *
     * @param direction The direction of the face.
     * @return The TextureRef for that face, or null if not defined (though ideally all faces should be defined).
     */
    public TextureRef getTexture(Direction direction) {
        return textures.get(direction);
    }

    /**
     * Gets an unmodifiable view of the texture map for all faces.
     *
     * @return An unmodifiable {@code Map<Direction, TextureRef>}.
     */
    public Map<Direction, TextureRef> getTextures() {
        return textures; // Already unmodifiable
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlockProperties that = (BlockProperties) o;
        // Primarily identify by ID and name for registry consistency
        return id == that.id && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        // Primarily hash based on ID and name
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "BlockProperties{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", solid=" + solid +
               ", transparent=" + transparent +
               ", luminance=" + luminance +
               ", textures=" + textures.size() + " faces" +
               '}';
    }
}
