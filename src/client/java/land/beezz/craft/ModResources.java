package land.beezz.craft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Loads the mod's GUI textures from the JAR itself; Fabric API's resource loader is not a dependency. */
final class ModResources {
    private static final Map<ResourceLocation, DynamicTexture> TEXTURES = new HashMap<>();

    private ModResources() {}

    static void texture(ResourceLocation id) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        DynamicTexture ours = TEXTURES.get(id);
        if (ours != null && manager.getTexture(id) == ours) return;
        String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
        try (InputStream in = ModResources.class.getResourceAsStream(path)) {
            if (in == null) {
                BeezzCraft.LOG.warn("BeezzCraft: texture not found in the JAR: {}", path);
                return;
            }
            DynamicTexture texture = new DynamicTexture(NativeImage.read(in));
            manager.register(id, texture);
            TEXTURES.put(id, texture);
        } catch (IOException e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot load texture {}: {}", path, e.toString());
        }
    }
}
