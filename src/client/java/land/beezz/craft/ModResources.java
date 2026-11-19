package land.beezz.craft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Loads the mod's GUI textures from the JAR itself. Fabric Loader does not mount mod resources without
 * fabric-resource-loader (Fabric API), so a zero-dependency mod has to register its textures by hand.
 * <p>
 * Reflection keeps the same file valid on 1.21.x (ResourceLocation, DynamicTexture(NativeImage)) and 26.x
 * (Identifier, DynamicTexture(Supplier, NativeImage)).
 */
final class ModResources {
    private static final Map<Object, Object> TEXTURES = new HashMap<>();

    private ModResources() {}

    static void texture(Object id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Object manager = mc.getTextureManager();
            Object ours = TEXTURES.get(id);
            Method getTexture = manager.getClass().getMethod("getTexture", id.getClass());
            if (ours != null && getTexture.invoke(manager, id) == ours) return;

            String name = id.toString();
            int colon = name.indexOf(':');
            String path = "/assets/" + name.substring(0, colon) + "/" + name.substring(colon + 1);
            try (InputStream in = ModResources.class.getResourceAsStream(path)) {
                if (in == null) {
                    BeezzCraft.LOG.warn("BeezzCraft: texture not found in the JAR: {}", path);
                    return;
                }
                NativeImage image = NativeImage.read(in);
                Class<?> type = Class.forName("net.minecraft.client.renderer.texture.DynamicTexture");
                Object texture;
                try {
                    Constructor<?> ctor = type.getConstructor(Supplier.class, NativeImage.class);
                    texture = ctor.newInstance((Supplier<String>) () -> "beezzcraft:" + name, image);
                } catch (NoSuchMethodException e) {
                    texture = type.getConstructor(NativeImage.class).newInstance(image);
                }
                Class<?> base = Class.forName("net.minecraft.client.renderer.texture.AbstractTexture");
                Method register = manager.getClass().getMethod("register", id.getClass(), base);
                register.invoke(manager, id, texture);
                TEXTURES.put(id, texture);
            }
        } catch (ReflectiveOperationException | IOException e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot load texture {}: {}", id, e.toString());
        }
    }
}
