package land.beezz.craft;

import net.neoforged.fml.common.Mod;

/** NeoForge entrypoint. The Fabric entrypoint is excluded from the NeoForge JAR. */
@Mod("beezzcraft")
public class BeezzCraftNeoForge {

    public BeezzCraftNeoForge() {
        BeezzCraft.init();
    }
}
