package draylar.goml.compat;

import draylar.goml.api.Claim;
import draylar.goml.api.event.ClaimEvents;
import net.minecraft.server.MinecraftServer;
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

        server.getWorlds().forEach(world ->
                CLAIM.get(world).getClaims().entries().forEach(entry ->
                        addClaim(entry.getValue())));
    }

    private void addClaim(Claim claim) {
        if (set == null) return;
        Box box = claim.getClaimBox().minecraftBox();
        AreaMarker marker = set.createAreaMarker(
                claim.getOrigin().toShortString(), // marker id
                claim.getType().getName().getString(), // marker label
                false, // parse as html
                "world", // TODO: world name // haven't found a way to turn world name into dynmap world name
                // config is probably just gonna need a map of these links
                new double[]{box.minX, box.maxX}, // x pos array
                new double[]{box.minZ, box.maxZ}, // z pos array
                false // persistent (requires persistent markerSet)
        );
        marker.setRangeY(box.minY, box.maxY);
        // setting the color to the same as the goggles would display sounds cool
        // just seems like a challenge with it just getting mapped to concrete from its hash
    }
}
