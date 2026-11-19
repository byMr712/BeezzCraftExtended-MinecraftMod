package land.beezz.craft;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric entrypoint. The NeoForge JAR excludes this class and uses
 * {@code land.beezz.craft.BeezzCraftNeoForge} instead.
 */
public class BeezzCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BeezzCraft.init();
    }
}
