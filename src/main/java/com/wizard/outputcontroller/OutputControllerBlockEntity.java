package com.wizard.outputcontroller;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class OutputControllerBlockEntity extends SplitShaftBlockEntity {

    /** side: -1 = all sides, otherwise Direction.get3DDataValue() */
    public record Rule(int side, double offValue, double onValue) {}

    public static final int MAX_RULES = 6;
    private static final double MAX_RPM = 256;

    private double defaultOutput = 0;
    private final List<Rule> rules = new ArrayList<>();
    private double target = 0;

    public OutputControllerBlockEntity(BlockPos pos, BlockState state) {
    super(OutputControllerMod.OUTPUT_CONTROLLER_BE.get(), pos, state);
}

    public double getDefaultOutput() { return defaultOutput; }
    public List<Rule> getRules() { return List.copyOf(rules); }

    /** Called on the server when the player presses Done in the GUI. */
    public void setConfig(double def, List<Rule> newRules) {
        defaultOutput = Double.isFinite(def) ? Mth.clamp(def, -MAX_RPM, MAX_RPM) : 0;
        rules.clear();
        for (Rule r : newRules) {
            if (rules.size() >= MAX_RULES) break;
            if (r.side() < -1 || r.side() > 5) continue;
            if (!Double.isFinite(r.offValue()) || !Double.isFinite(r.onValue())) continue;
            rules.add(new Rule(r.side(),
                    Mth.clamp(r.offValue(), -MAX_RPM, MAX_RPM), Mth.clamp(r.onValue(), -MAX_RPM, MAX_RPM)));
        }
        if (level != null && !level.isClientSide) {
            refreshTarget(true);
            setChanged();
            sendData(); // sync to clients so the GUI shows current values
        }
    }

    public void onRedstoneChanged() {
        if (level != null && !level.isClientSide) refreshTarget(false);
    }

    /**
     * RULE LOGIC (change here if you want different behaviour):
     * - No rules -> the Default tab value is output.
     * - Rules exist -> first rule (top to bottom) that has redstone gives its "Signal" value.
     *   If none has redstone, the first rule's "No signal" value is used.
     */
    private double computeTarget() {
        if (rules.isEmpty()) return defaultOutput;
        for (Rule r : rules) if (isActive(r)) return r.onValue();
        return rules.get(0).offValue();
    }

    private boolean isActive(Rule r) {
        if (level == null) return false;
        if (r.side() < 0) {
            for (Direction d : Direction.values()) if (signal(d) > 0) return true;
            return false;
        }
        return signal(Direction.from3DDataValue(r.side())) > 0;
    }

    private int signal(Direction d) {
        return level.getSignal(worldPosition.relative(d), d);
    }

    private void refreshTarget(boolean force) {
        double t = computeTarget();
        if (force || t != target) {
            target = t;
            updateKinetics();
        }
    }

    private void updateKinetics() {
        if (hasNetwork()) getOrCreateNetwork().remove(this);
        RotationPropagator.handleRemoved(level, worldPosition, this);
        removeSource();
        attachKinetics();
        if (hasNetwork()) getOrCreateNetwork().add(this);
    }

    @Override
    public void initialize() {
        if (level != null && !level.isClientSide) target = computeTarget();
        super.initialize();
    }

    /** Ratio between input speed and the speed sent out of the given face. */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face == getSourceFacing()) return 1;   // towards the input: unchanged
        float in = getTheoreticalSpeed();
        if (in == 0) return 1;
        double t = Mth.clamp(target, -MAX_RPM, MAX_RPM);
        if (t == 0) return 1e-6f;   // 0 means "no custom ratio" in Create, so use a near-zero ratio instead
        return (float) (t / in);
    }

@Override
public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
    boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking); // keeps Create's speed/stress info

    Direction face = com.wizard.outputcontroller.client.ClientHooks.getLookedFace(worldPosition);
    if (face == null || face.getAxis() == getBlockState().getValue(BlockStateProperties.AXIS)) return added;

    int sideIdx = face.get3DDataValue();
    boolean found = false;
    for (Rule r : rules) {
        if (r.side() != sideIdx && r.side() != -1) continue;
        if (!found) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Output Controller (" + face.getName() + ")").withStyle(ChatFormatting.GOLD));
            found = true;
        }
        int sig = signal(face);
        String scope = r.side() == -1 ? " [all sides rule]" : "";
        tooltip.add(Component.literal(" Input: " + (sig > 0 ? "signal " + sig : "no signal") + scope)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  Output, no signal: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(fmt(r.offValue())).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("  Output, signal: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(fmt(r.onValue())).withStyle(ChatFormatting.AQUA)));
    }
    return added || found;
}

private static String fmt(double v) {
    return java.math.BigDecimal.valueOf(v).setScale(5, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
}

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("DefaultOutput", defaultOutput);
        ListTag list = new ListTag();
        for (Rule r : rules) {
            CompoundTag t = new CompoundTag();
            t.putInt("Side", r.side());
            t.putDouble("Off", r.offValue());
            t.putDouble("On", r.onValue());
            list.add(t);
        }
        tag.put("Rules", list);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        defaultOutput = tag.getDouble("DefaultOutput");
        rules.clear();
        ListTag list = tag.getList("Rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            rules.add(new Rule(t.getInt("Side"), t.getDouble("Off"), t.getDouble("On")));
        }
    }
}