package draylar.goml.compat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimUtils;
import draylar.goml.api.event.ClaimEvents;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

import static draylar.goml.GetOffMyLawn.CLAIM;

public class DynmapCompat {
    private static final String gomlMarkerSetId = "gomlMarkerSet";

    // From https://lospec.com/palette-list/minecraft-concrete (matches block order so matches goggles).
    private static final int[] COLORS = new int[]{0xcfd5d6, 0xe06101, 0xa9309f, 0x2489c7, 0xf1af15, 0x5ea918, 0xd5658f, 0x373a3e, 0x7d7d73, 0x157788, 0x64209c, 0x2d2f8f, 0x603c20, 0x495b24, 0x8e2121, 0x080a0f};

    /**
     * Store all players uuids and names to update existing claims when a player changes its name.
     * This value gets reset each time the server stops, but it really doesn't matter.
     */
    private static final HashMap<UUID, String> playerNames = new HashMap<>();

    public static void init(final MinecraftServer server) {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                final MarkerAPI markerApi = api.getMarkerAPI();
                // Create GOML claims marker set if it's not already there.
                if (markerApi.getMarkerSet(gomlMarkerSetId) == null) {
                    markerApi.createMarkerSet(gomlMarkerSetId, "GOML Claims", null, true);
                }
                server.getWorlds().forEach(world -> CLAIM.get(world).getClaims().values().filter(claim -> getClaimMarker(claim, markerApi) == null).forEach(claim -> renderClaimArea(claim, server, markerApi)));
                ClaimEvents.CLAIM_CREATED.register(claim -> renderClaimArea(claim, server, markerApi));
                ClaimEvents.CLAIM_RESIZED.register((claim, x, y) -> resizeClaimArea(claim, server, markerApi));
                ClaimEvents.CLAIM_UPDATED.register(claim -> updateClaimArea(claim, server, markerApi));
                ClaimEvents.CLAIM_DESTROYED.register(claim -> deleteClaimArea(claim, markerApi));
                ServerPlayConnectionEvents.JOIN.register((handler, sender, minecraftServer) -> updateClaimAreasOfPlayer(handler.player, minecraftServer, markerApi));
            }
        });
    }

    private static void renderClaimArea(Claim claim, MinecraftServer server, MarkerAPI markerApi) {
        ServerWorld serverWorld = claim.getWorldInstance(server);
        String worldName = "world";
        if (serverWorld != null) {
            worldName = serverWorld.worldProperties.getLevelName();
        }
        ClaimCorners corners = getClaimCorners(claim);
        AreaMarker marker = markerApi.getMarkerSet(gomlMarkerSetId).createAreaMarker(getClaimId(claim), getClaimLabel(claim, server), true, worldName, corners.x, corners.z, true);
        // marker.setRangeY(corners.y[0], corners.y[1]);
        int color = COLORS[(claim.getOrigin().hashCode() & 0xFFFF) % COLORS.length];
        marker.setFillStyle(0.25, color);
        marker.setLineStyle(2, 1, color);
    }

    private static void resizeClaimArea(Claim claim, MinecraftServer server, MarkerAPI markerApi) {
        ClaimCorners corners = getClaimCorners(claim);
        handleClaimAreaUpdate(claim, server, markerApi, claimArea -> claimArea.setCornerLocations(corners.x, corners.z));
    }

    private static void updateClaimArea(Claim claim, MinecraftServer server, MarkerAPI markerApi) {
        handleClaimAreaUpdate(claim, server, markerApi, claimArea -> claimArea.setLabel(getClaimLabel(claim, server), true));
    }

    private static void deleteClaimArea(Claim claim, MarkerAPI markerApi) {
        AreaMarker claimArea = getClaimMarker(claim, markerApi);
        if (claimArea != null) {
            claimArea.deleteMarker();
        }
    }

    private static void updateClaimAreasOfPlayer(ServerPlayerEntity player, MinecraftServer server, MarkerAPI markerApi) {
        UUID uuid = player.getUuid();
        String name = player.getName().getString();
        if (!playerNames.containsKey(uuid) || !playerNames.get(uuid).equals(name)) {
            playerNames.put(uuid, name);
            ClaimUtils.getClaimsOwnedBy(player.getWorld(), uuid).forEach(entry -> handleClaimAreaUpdate(entry.getValue(), server, markerApi, claimArea -> updateClaimAreaPlayerInfo(claimArea, uuid, name)));
            ClaimUtils.getClaimsTrusted(player.getWorld(), uuid).forEach(entry -> handleClaimAreaUpdate(entry.getValue(), server, markerApi, claimArea -> updateClaimAreaPlayerInfo(claimArea, uuid, name)));
        }
    }

    private static void updateClaimAreaPlayerInfo(AreaMarker claimArea, UUID uuid, String name) {
        String uuidDiv = getValueDiv(uuid.toString());
        // Need to update the label only if previously it contained the player info as uuid.
        if (claimArea.getLabel().contains(uuidDiv)) {
            claimArea.setLabel(claimArea.getLabel().replace(uuidDiv, getPlayerDiv(name)), true);
        }
    }

    private static void handleClaimAreaUpdate(Claim claim, MinecraftServer server, MarkerAPI markerApi, Consumer<AreaMarker> handler) {
        AreaMarker claimArea = getClaimMarker(claim, markerApi);
        if (claimArea == null) {
            renderClaimArea(claim, server, markerApi);
        } else {
            handler.accept(claimArea);
        }
    }

    private static String getClaimId(Claim claim) {
        return claim.getWorld().toString() + " - " + claim.getOrigin().toShortString();
    }

    private static @Nullable AreaMarker getClaimMarker(Claim claim, MarkerAPI markerApi) {
        return markerApi.getMarkerSet(gomlMarkerSetId).findAreaMarker(getClaimId(claim));
    }

    private static String getClaimLabel(Claim claim, MinecraftServer server) {
        return getLabelLine(getLabelKey("claim_type"), getValueDiv(getHeadImage(claim.getType()) + claim.getType().getName().getString()), false) +
            getPlayers(getLabelKey("owners"), claim.getOwners(), server) +
            getPlayers(getLabelKey("trusted"), claim.getTrusted(), server) +
            getAugments(claim);
    }

    private static String getPlayers(Text key, Set<UUID> players, MinecraftServer server) {
        return players.isEmpty() ? "" : "<br>" + getLabelLine(key, players.stream().map(uuid -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                String name = player.getName().getString();
                playerNames.put(uuid, name);
                return getPlayerDiv(name);
            }

            // attempt to get player from server userCache
            Optional<GameProfile> optionalProfile = server.getUserCache().getByUuid(uuid);
            if (optionalProfile.isPresent()) {
                GameProfile profile = optionalProfile.get();
                String name = profile.getName();
                playerNames.put(uuid, name);
                return getPlayerDiv(name);
            }

            return getValueDiv(uuid.toString());
        }).reduce("", (prev, curr) -> prev + curr), true);
    }

    private static String getAugments(Claim claim) {
        return claim.hasAugment() ?
            "<br>" + getLabelLine(getLabelKey("augments"), claim.getAugments().values().stream().map(augment -> getValueDiv(
                    getHeadImage((PolymerHeadBlock) augment) + augment.getAugmentName().getString()
                )
            ).reduce("", (prev, curr) -> prev + curr), true) :
            "";
    }

    private static String getLabelLine(Text key, String value, boolean wrap) {
        return "<div style=\"display:flex;align-items:center;flex-wrap:" + (wrap ? "" : "no") + "wrap;gap:2%;text-wrap:nowrap\"><b>" + key.getString() + ":</b>" + value + "</div>";
    }

    private static String getPlayerDiv(String name) {
        return getValueDiv("<img src=\"tiles/faces/16x16/" + name + ".png\">" + name);
    }

    private static String getValueDiv(String content) {
        return "<div style=\"display:flex;align-items:center;gap:2%;flex-grow:1;text-wrap:nowrap\">" + content + "</div>";
    }

    private static String getHeadImage(PolymerHeadBlock polymerHeadBlock) {
        String src = getHeadUrl(polymerHeadBlock);
        return "<div style=\"min-width:16px;width:16px;height:16px;overflow:hidden;background-image:url(" + src + ");background-repeat:no-repeat;background-position:-16px -16px\"><img src=\"" + src +
            "\" style=\"width:128px;height:128px;margin-top:-16px;margin-left:-48px\"></div>";
    }

    private static String getHeadUrl(PolymerHeadBlock polymerHeadBlock) {
        return new Gson().fromJson(new String(Base64.getDecoder().decode(polymerHeadBlock.getPolymerSkinValue(null, null, null))), JsonObject.class)
            .getAsJsonObject("textures")
            .getAsJsonObject("SKIN")
            .getAsJsonPrimitive("url")
            .getAsString();
    }

    private static Text getLabelKey(String key) {
        return Text.translatable("text.goml.dynmap.label." + key);
    }

    private static ClaimCorners getClaimCorners(Claim claim) {
        Box claimBox = claim.getClaimBox().minecraftBox();
        return new ClaimCorners(new double[]{claimBox.minX, claimBox.maxX}, new double[]{claimBox.minY, claimBox.maxY}, new double[]{claimBox.minZ, claimBox.maxZ});
    }

    private record ClaimCorners(double[] x, double[] y, double[] z) {}
}
