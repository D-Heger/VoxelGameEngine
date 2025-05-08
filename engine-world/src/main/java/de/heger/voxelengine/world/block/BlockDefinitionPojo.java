package de.heger.voxelengine.world.block;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Plain Old Java Object (POJO) representing the structure of a block definition JSON file.
 * Used by Jackson for deserialization.
 *
 * Based on Subtask P3-T4.4 and docs/block_registry_design.md section 10.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore future properties like "hardness"
public class BlockDefinitionPojo {

    @JsonProperty(required = true)
    public String name;

    @JsonProperty(defaultValue = "true") // Default solid to true if missing
    public boolean solid = true;

    @JsonProperty(defaultValue = "false") // Default transparent to false if missing
    public boolean transparent = false;

    @JsonProperty(defaultValue = "0") // Default luminance to 0 if missing
    public byte luminance = 0;

    @JsonProperty(required = true)
    public Map<String, String> textures; // Key: direction name (e.g., "north"), Value: texture name (e.g., "core:block/dirt")

    // Default constructor required by Jackson
    public BlockDefinitionPojo() {}

    // Optional: Add validation logic here or in the loading process
}