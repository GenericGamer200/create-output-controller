package com.wizard.outputcontroller.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import com.wizard.outputcontroller.*;
import com.wizard.outputcontroller.OutputControllerBlockEntity.Rule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class OutputControllerScreen extends Screen {
    private static final int W = 250, MAX_ROWS = OutputControllerBlockEntity.MAX_RULES;

    private static class Row { int side = -1; String off = "0", on = "0"; }

    private final BlockPos pos;
    private int tab = 0; // 0 = Default, 1 = Customize
    private int left, top;
    private int openRow = -1; // which row's dropdown is open, -1 = none
    private String defaultText = "0";
    private final List<Row> rows = new ArrayList<>();
    private EditBox defaultBox;
    private final List<EditBox> offBoxes = new ArrayList<>(), onBoxes = new ArrayList<>();

    public OutputControllerScreen(BlockPos pos) {
        super(Component.translatable("gui.outputcontroller.title"));
        this.pos = pos;
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(pos) instanceof OutputControllerBlockEntity be) {
            defaultText = fmt(be.getDefaultOutput());
            for (Rule r : be.getRules()) {
                Row row = new Row();
                row.side = r.side(); row.off = fmt(r.offValue()); row.on = fmt(r.onValue());
                rows.add(row);
            }
        }
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static double parse(String s) {
        try {
            if (s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.")) return 0;
            return Math.round(Double.parseDouble(s) * 1e5) / 1e5;
        } catch (NumberFormatException e) { return 0; }
    }

    private EditBox numberBox(int x, int y, int w, String text) {
        EditBox b = new EditBox(font, x, y, w, 18, Component.empty());
        b.setMaxLength(14);
        b.setFilter(s -> s.matches("-?\\d*(\\.\\d{0,5})?")); // negative allowed, max 5 decimals
        b.setValue(text);
        addRenderableWidget(b);
        return b;
    }

    private void collect() {
        if (tab == 0) { if (defaultBox != null) defaultText = defaultBox.getValue(); }
        else for (int i = 0; i < rows.size() && i < offBoxes.size(); i++) {
            rows.get(i).off = offBoxes.get(i).getValue();
            rows.get(i).on = onBoxes.get(i).getValue();
        }
    }

    private void switchTab(int t) { collect(); tab = t; openRow = -1; rebuildWidgets(); }

@Override
protected void init() {
    offBoxes.clear(); onBoxes.clear(); defaultBox = null;
    left = (width - W) / 2;
    top = Math.max(10, height / 2 - 100);

    Button b0 = Button.builder(Component.translatable("gui.outputcontroller.tab.default"), b -> switchTab(0))
            .bounds(left, top, 70, 20).build();
    Button b1 = Button.builder(Component.translatable("gui.outputcontroller.tab.customize"), b -> switchTab(1))
            .bounds(left + 74, top, 70, 20).build();
    b0.active = tab != 0; b1.active = tab != 1;
    addRenderableWidget(b0); addRenderableWidget(b1);

    // Done button now sits in the top row, always visible
    addRenderableWidget(Button.builder(Component.translatable("gui.outputcontroller.done"), b -> save())
            .bounds(left + W - 70, top, 70, 20).build());

    if (tab == 0) {
        defaultBox = numberBox(left + 70, top + 50, 120, defaultText);
        return;
    }

    // Customize tab. While a dropdown is open, rows below it are hidden.
    int visibleRows = openRow >= 0 ? openRow + 1 : rows.size();
    for (int i = 0; i < visibleRows; i++) {
        final int idx = i;
        Row row = rows.get(i);
        int y = top + 52 + i * 24;
        addRenderableWidget(Button.builder(sideName(row.side).copy().append(" v"), b -> {
            collect();
            openRow = (openRow == idx) ? -1 : idx;
            rebuildWidgets();
        }).bounds(left, y, 66, 18).build());
        offBoxes.add(numberBox(left + 70, y, 72, row.off));
        onBoxes.add(numberBox(left + 146, y, 72, row.on));
        addRenderableWidget(Button.builder(Component.literal("X"), b -> {
            collect(); rows.remove(idx); openRow = -1; rebuildWidgets();
        }).bounds(left + 222, y, 20, 18).build());
    }

    if (openRow >= 0) {
        // the dropdown list, added last so it draws on top
        int y0 = top + 52 + openRow * 24 + 20;
        for (int s = -1; s <= 5; s++) {
            final int side = s;
            addRenderableWidget(Button.builder(sideName(s), b -> {
                collect();
                rows.get(openRow).side = side;
                openRow = -1;
                rebuildWidgets();
            }).bounds(left, y0 + (s + 1) * 16, 66, 16).build());
        }
    } else if (rows.size() < MAX_ROWS) {
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            collect(); rows.add(new Row()); rebuildWidgets();
        }).bounds(left, top + 52 + rows.size() * 24, 20, 18).build());
    }
}



    private static Component sideName(int side) {
        return Component.translatable("gui.outputcontroller.side." + (side < 0 ? "all" : Direction.from3DDataValue(side).getName()));
    }

    private void save() {
        collect();
        List<Rule> out = new ArrayList<>();
        for (Row r : rows) out.add(new Rule(r.side, parse(r.off), parse(r.on)));
        PacketDistributor.sendToServer(new SaveConfigPayload(pos, parse(defaultText), out));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int left = (width - W) / 2, top = height / 2 - 90;
        if (tab == 0) {
            g.drawString(font, Component.translatable("gui.outputcontroller.output"), left, top + 50, 0xFFFFFF);
        } else {
            g.drawString(font, Component.translatable("gui.outputcontroller.side"), left, top + 32, 0xAAAAAA);
            g.drawString(font, Component.translatable("gui.outputcontroller.no_signal"), left + 70, top + 32, 0xAAAAAA);
            g.drawString(font, Component.translatable("gui.outputcontroller.signal"), left + 146, top + 32, 0xAAAAAA);
        }
    }
}