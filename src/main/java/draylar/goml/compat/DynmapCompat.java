package draylar.goml.compat;

import com.jamieswhiteshirt.rtree3i.Box;
import draylar.goml.api.event.ClaimEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.HashMap;
import java.util.UUID;

/**
 * Config option to automatically create visible marker label on a claim area marker (maybe visible on hover?)
 * Dye (or dye combination?) to color your claim on the map, icon equal to player's head
 * Augment to show/hide claim area on map
 * Force show/force hide/neutral config option
 *
 * For WIP:
 */
public class DynmapCompat {
    private static final String gomlMarkerSetId = "gomlMarkerSet";
    public static void init() {
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                final MarkerAPI markerApi = api.getMarkerAPI();
                if (markerApi.getMarkerSet(gomlMarkerSetId) == null) {
                    markerApi.createMarkerSet(gomlMarkerSetId, "GOML Claims", null, false);
                }

                ClaimEvents.CLAIM_CREATED.register(claim -> {
                    Box claimBox = claim.getClaimBox().toBox();
                    markerApi.getMarkerSet(gomlMarkerSetId).createAreaMarker(
                        claim.getWorld().toString() + "_" + claim.getOrigin().getX() + "_" + claim.getOrigin().getY() + "_" + claim.getOrigin().getZ(),
                        "Claim in " + claim.getWorld().getPath() + " at [" + claim.getOrigin().toShortString() + "]",
                        false,
                        "world", // TODO: get world name from ServerWorld from Claim
                        new double[]{claimBox.x1(), claimBox.x2()},
                        new double[]{claimBox.z1(), claimBox.z2()},
                        false
                    );
                    System.out.println(claimBox.x1() + ", " + claimBox.x2() + " - " + claimBox.z1() + ", " + claimBox.z2());
                });
                ClaimEvents.CLAIM_DESTROYED.register(claim -> {
                    markerApi.getMarkerSet(gomlMarkerSetId).findAreaMarker(claim.getWorld().toString() + "_" + claim.getOrigin().getX() + "_" + claim.getOrigin().getY() + "_" + claim.getOrigin().getZ()).deleteMarker();
                });
                ClaimEvents.CLAIM_RESIZED.register((claim, x, y) -> {
                    // markerApi.getMarkerSet(gomlMarkerSetId).findAreaMarker(claim.getWorld().toString() + "_" + claim.getOrigin().getX() + "_" + claim.getOrigin().getY() + "_" + claim.getOrigin().getZ());
                });
            }
        });
    }
}
