package draylar.goml.api.event;

import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import draylar.goml.api.PermissionReason;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ClaimEvents {

    /**
     * This callback is triggered when a player's permission is denied inside a claim.
     * Callback handlers can confirm the denial, pass, or veto the denial through {@link ActionResult}.
     */
    public static final Event<InteractionHandler> PERMISSION_DENIED = EventFactory.createArrayBacked(InteractionHandler.class,
            (listeners) -> (player, world, hand, pos, reason) -> {
                for (InteractionHandler event : listeners) {
                    ActionResult result = event.check(player, world, hand, pos, reason);

                    if (result != ActionResult.PASS) {
                        return result;
                    }
                }

                return ActionResult.PASS;
            }
    );

    public static final Event<GenericClaimEvent> CLAIM_CREATED = EventFactory.createArrayBacked(GenericClaimEvent.class,
            (listeners) -> (claim) -> {
                for (var event : listeners) {
                    event.onEvent(claim);
                }
            }
    );

    public static final Event<GenericClaimEvent> CLAIM_DESTROYED = EventFactory.createArrayBacked(GenericClaimEvent.class,
            (listeners) -> (claim) -> {
                for (var event : listeners) {
                    event.onEvent(claim);
                }
            }
    );

    public static final Event<ClaimResizedEvent> CLAIM_RESIZED = EventFactory.createArrayBacked(ClaimResizedEvent.class,
            (listeners) -> (claim, x, y) -> {
                for (var event : listeners) {
                    event.onResizeEvent(claim, x, y);
                }
            }
    );

    public static final Event<GenericClaimEvent> CLAIM_UPDATED = EventFactory.createArrayBacked(GenericClaimEvent.class,
        (listeners) -> (claim) -> {
            for (var event : listeners) {
                event.onEvent(claim);
            }
        }
    );

    public interface InteractionHandler {
        ActionResult check(PlayerEntity player, World world, Hand hand, BlockPos pos, PermissionReason reason);
    }

    public interface GenericClaimEvent {
        void onEvent(Claim claim);
    }

    public interface ClaimResizedEvent {
        void onResizeEvent(Claim claim, ClaimBox oldSize, ClaimBox newSize);
    }
}
