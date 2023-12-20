package draylar.goml.compat;

import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import draylar.goml.api.event.ClaimEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import static draylar.goml.GetOffMyLawn.CLAIM;

public class DynmapCompat extends DynmapCommonAPIListener {
    private MarkerSet set;
    private final MinecraftServer server;
    private static final int[] COLORS = new int[]{0xcfd5d6, 0xe06101, 0xa9309f, 0x2489c7, 0xf1af15, 0x5ea918, 0xd5658f, 0x373a3e, 0x7d7d73, 0x157788, 0x64209c, 0x2d2f8f, 0x603c20, 0x495b24, 0x8e2121, 0x080a0f};
    // from https://lospec.com/palette-list/minecraft-concrete (matches block order so matches goggles)

    public DynmapCompat(MinecraftServer server) {
        this.server = server;
    }

    public static void init(MinecraftServer server) {
        DynmapCommonAPIListener listener = new DynmapCompat(server);
        DynmapCommonAPIListener.register(listener);
    }

    @Override
    public void apiEnabled(DynmapCommonAPI api) {
        MarkerAPI markerAPI = api.getMarkerAPI();
        set = markerAPI.createMarkerSet("goml", "Get Off My Lawn", null, false);

        ClaimEvents.CLAIM_CREATED.register(this::addClaim);
        ClaimEvents.CLAIM_RESIZED.register(this::updateClaim);
        ClaimEvents.CLAIM_DESTROYED.register(this::removeClaim);

        server.getWorlds().forEach(world ->
                CLAIM.get(world).getClaims().entries().forEach(entry ->
                        addClaim(entry.getValue())));
    }

    private void addClaim(Claim claim) {
        if (set == null) return;
        AreaMarker marker = createMarker(claim);
        int color = COLORS[(claim.getOrigin().hashCode() & 0xFFFF) % COLORS.length];
        marker.setFillStyle(0.35, color);
        marker.setLineStyle(3, 0.8, color);
        // I don't believe adding y to the marker is worth it as it removes fill color and makes placement less clear
    }

    private AreaMarker createMarker(Claim claim) {
        String levelName = getLevelName(claim);
        Box box = claim.getClaimBox().minecraftBox();
        return set.createAreaMarker(
                markerId(claim, levelName),
                claim.getType().getName().getString(), // marker label
                false, // parse as html
                levelName,
                new double[]{box.minX, box.maxX},
                new double[]{box.minZ, box.maxZ},
                false // persistent (requires persistent markerSet)
        );
    }

    private void updateClaim(Claim claim, ClaimBox oldBox, ClaimBox newBox) {
        if (set == null) return;
        AreaMarker marker = getMarker(claim);
        Box box = newBox.minecraftBox();
        marker.setCornerLocations(new double[]{box.minX, box.maxX}, new double[]{box.minZ, box.maxZ});
        marker.setLabel(claim.getType().getName().getString());
    }

    private void removeClaim(Claim claim) {
        if (set == null) return;
        AreaMarker marker = getMarker(claim);
        marker.deleteMarker();
    }

    private String getLevelName(Claim claim) {
        ServerWorld world = claim.getWorldInstance(server);
        return world.worldProperties.getLevelName();
    }

    private AreaMarker getMarker(Claim claim) {
        String levelName = getLevelName(claim);
        return set.findAreaMarker(markerId(claim, levelName));
    }

    private static String markerId(Claim claim, String levelName) {
        return String.format("%s: %s", levelName, claim.getOrigin().toShortString());
    }
}
