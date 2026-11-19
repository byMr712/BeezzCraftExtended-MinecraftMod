package land.beezz.craft.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("keyMappings")
    KeyMapping[] beezzcraft$keyMappings();

    @Mutable
    @Accessor("keyMappings")
    void beezzcraft$setKeyMappings(KeyMapping[] mappings);
}
