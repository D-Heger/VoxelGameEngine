package de.heger.voxelengine.world.block;

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