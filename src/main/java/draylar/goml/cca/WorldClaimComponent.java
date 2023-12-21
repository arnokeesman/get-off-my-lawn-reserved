package draylar.goml.cca;

import com.jamieswhiteshirt.rtree3i.ConfigurationBuilder;
import com.jamieswhiteshirt.rtree3i.RTreeMap;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class WorldClaimComponent implements ClaimComponent {

    private RTreeMap<ClaimBox, Claim> claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::toBox);
    private final World world;

    public WorldClaimComponent(World world) {
        this.world = world;
    }

    @Override
    public RTreeMap<ClaimBox, Claim> getClaims() {
        return claims;
    }

    @Override
    public void add(Claim info) {
        this.claims = this.claims.put(info.getClaimBox(), info);
    }

    @Override
    public void remove(Claim info) {
        this.claims = this.claims.remove(info.getClaimBox());
    }

    @Override
    public void readFromNbt(NbtCompound tag) {
        this.claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::rtree3iBox);
        var world = this.world.getRegistryKey().getValue();

        var version = tag.getInt("Version");
        NbtList nbtList = tag.getList("Claims", NbtType.COMPOUND);

        if (version == 0) {
            nbtList.forEach(child -> {
                NbtCompound childCompound = (NbtCompound) child;
                ClaimBox box = boxFromTag((NbtCompound) childCompound.get("Box"));
                if (box != null) {
                    Claim claimInfo = Claim.fromNbt(this.world.getServer(), (NbtCompound) childCompound.get("Info"), version);
                    claimInfo.internal_setWorld(world);
                    claimInfo.internal_setClaimBox(box);
                    if (this.world instanceof ServerWorld world1) {
                        claimInfo.internal_updateChunkCount(world1);
                    }
                    claimInfo.internal_enableUpdates();
                    add(claimInfo);
                }
            });
        } else {
            nbtList.forEach(child -> {
                Claim claimInfo = Claim.fromNbt(this.world.getServer(), (NbtCompound) child, version);
                claimInfo.internal_setWorld(world);
                if (this.world instanceof ServerWorld world1) {
                    claimInfo.internal_updateChunkCount(world1);
                }
                claimInfo.internal_enableUpdates();
                add(claimInfo);
            });
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag) {
        NbtList nbtListClaims = new NbtList();
        tag.putInt("Version", 1);

        claims.values().forEach(claim -> nbtListClaims.add(claim.asNbt()));

        tag.put("Claims", nbtListClaims);
    }

    @Nullable
    @Deprecated
    public ClaimBox boxFromTag(NbtCompound tag) {
        return ClaimBox.readNbt(tag, 0);
    }
}
