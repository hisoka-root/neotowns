package net.neotowns.handler;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.UUID;

public final class FakePlayerOwnerResolver {

    private static final List<String> KNOWN_PREFIXES = List.of(
        "[Mekanism]", "[BuildCraft]", "[RFTools]", "[ImmersiveEngineering]",
        "[Thermal]", "[Create]", "[EnderIO]", "[Botania]", "[AE2]"
    );

    private FakePlayerOwnerResolver() {}

    public static UUID resolve(FakePlayer fp) {
        String name = fp.getGameProfile().getName();

        for (String prefix : KNOWN_PREFIXES) {
            if (name.startsWith(prefix)) {
                String uuidPart = name.substring(prefix.length()).trim();
                try { return UUID.fromString(uuidPart); } catch (IllegalArgumentException ignored) {}
            }
        }

        CompoundTag tags = fp.getPersistentData();
        if (tags.hasUUID("Owner")) return tags.getUUID("Owner");
        if (tags.hasUUID("ownerUUID")) return tags.getUUID("ownerUUID");

        return null;
    }
}
