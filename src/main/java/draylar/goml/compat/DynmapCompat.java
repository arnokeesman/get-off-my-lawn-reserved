package draylar.goml.compat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import draylar.goml.api.Claim;
import draylar.goml.api.event.ClaimEvents;
import eu.pb4.polymer.core.api.block.PolymerHeadBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

public class DynmapCompat {
    private static final String gomlMarkerSetId = "gomlMarkerSet";

    // From https://lospec.com/palette-list/minecraft-concrete (matches block order so matches goggles).
    private static final int[] COLORS = new int[]{0xcfd5d6, 0xe06101, 0xa9309f, 0x2489c7, 0xf1af15, 0x5ea918, 0xd5658f, 0x373a3e, 0x7d7d73, 0x157788, 0x64209c, 0x2d2f8f, 0x603c20, 0x495b24, 0x8e2121, 0x080a0f};

    public static void init(MinecraftServer server) {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                final MarkerAPI markerApi = api.getMarkerAPI();
                // Create GOML claims marker set if it's not already there.
                if (markerApi.getMarkerSet(gomlMarkerSetId) == null) {
                    markerApi.createMarkerSet(gomlMarkerSetId, "GOML Claims", null, true);
                }
                // Render new claims.
                ClaimEvents.CLAIM_CREATED.register(claim -> {
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
                });
                // Update resized claims.
                ClaimEvents.CLAIM_RESIZED.register((claim, x, y) -> {
                    ClaimCorners corners = getClaimCorners(claim);
                    getClaimMarker(claim, markerApi).setCornerLocations(corners.x, corners.z);
                    // marker.setRangeY(corners.y[0], corners.y[1]);
                });
                // Update the label of existing claims.
                ClaimEvents.CLAIM_UPDATED.register(claim -> getClaimMarker(claim, markerApi).setLabel(getClaimLabel(claim, server), true));
                // Remove destroyed claims.
                ClaimEvents.CLAIM_DESTROYED.register(claim -> getClaimMarker(claim, markerApi).deleteMarker());
            }
        });
    }

    private static String getClaimId(Claim claim) {
        return claim.getWorld().toString() + " - " + claim.getOrigin().toShortString();
    }

    private static AreaMarker getClaimMarker(Claim claim, MarkerAPI markerApi) {
        return markerApi.getMarkerSet(gomlMarkerSetId).findAreaMarker(getClaimId(claim));
    }

    private static String getClaimLabel(Claim claim, MinecraftServer server) {
        return getLabelLine(getLabelKey("claim_type"), getValueDiv(getHeadImage(claim.getType()) + claim.getType().getName().getString())) + // TODO: Shift name to account for icon
            getPlayers(getLabelKey("owners"), claim.getOwners(), server) +
            getPlayers(getLabelKey("trusted"), claim.getTrusted(), server) +
            getAugments(claim);
    }

    private static String getPlayers(Text key, Set<UUID> players, MinecraftServer server) {
        return players.isEmpty() ? "" : "<br>" + getLabelLine(key, String.join(";", players.stream().map(uuid -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            return getValueDiv((player == null ? uuid.toString() : "<img src=\"tiles/faces/16x16/" + player.getName().getString() + ".png\">" + player.getName().getString()));
        }).toList()));
    }

    private static String getAugments(Claim claim) {
        return claim.hasAugment() ?
            "<br>" + getLabelLine(getLabelKey("augments"), String.join(";", claim.getAugments().values().stream().map(augment -> getValueDiv(
                    getHeadImage((PolymerHeadBlock) augment) + augment.getAugmentName().getString() // TODO: Shift name to account for icon
                )
            ).toList())) :
            "";
    }

    private static String getValueDiv(String content) {
        return "<div style=\"display:flex;align-items:center;gap:2%\">" + content + "</div>";
    }

    private static String getLabelLine(Text key, String value) {
        return "<div style=\"display:flex;align-items:center;flex-wrap:wrap;gap:2%\"><b>" + key.getString() + ":</b>" + value + "</div>";
    }

    private static String getHeadImage(PolymerHeadBlock polymerHeadBlock) {
        String src = getHeadUrl(polymerHeadBlock);
        String div = "<div style=\"width:16px;height:16px;overflow:hidden;position:absolute\">";
        String style = "width:128px;height:128px;margin-top:-16px;margin-left:-";
        return div + "<img src=\"" + src + "\" style=\"" + style + "16px\"></div>" + div + "<img src=\"" + src + "\" style=\"" + style + "80px\"></div>";
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

    private record ClaimCorners(double[] x, double[] y, double[] z) {
    }
}
