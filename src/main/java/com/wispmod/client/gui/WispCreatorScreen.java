package com.wispmod.client.gui;

import com.wispmod.data.WispDefinition;
import com.wispmod.data.WispDefinitionManager;
import com.wispmod.network.WispPayloads;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The central GUI: browse existing Wisps on the left, edit the selected one (or a brand new,
 * unsaved one) on the right. All mutations go through {@link WispPayloads.RequestEditPayload}
 * to the server — this screen never writes to {@link WispDefinitionManager} directly, since
 * the server is authoritative (see WispMod#handleEditRequest).
 */
public class WispCreatorScreen extends Screen {

    private static final int LIST_WIDTH = 140;
    private static final int PADDING = 8;

    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private TextFieldWidget nameField;
    private TextFieldWidget boxNameField;
    private TextFieldWidget colorField;
    private TextFieldWidget boxColorField;
    private TextFieldWidget textureField;
    private TextFieldWidget boxTextureField;

    private String editingId; // internalId being edited, or null for "new, unsaved"
    private String statusMessage = "";

    public WispCreatorScreen() {
        super(Text.literal("Wisp Creator"));
    }

    @Override
    protected void init() {
        rebuildListButtons();

        int rightX = this.x() + LIST_WIDTH + PADDING * 2;
        int fieldWidth = Math.max(160, this.width - rightX - PADDING);
        int y = 40;

        nameField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20, Text.literal("Name")));
        nameField.setMaxLength(32);
        y += 26;

        colorField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20, Text.literal("#RRGGBB")));
        colorField.setMaxLength(7);
        y += 26;

        boxNameField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20, Text.literal("Box name")));
        boxNameField.setMaxLength(32);
        y += 26;

        boxColorField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20, Text.literal("#RRGGBB")));
        boxColorField.setMaxLength(7);
        y += 26;

        textureField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20,
                Text.literal("textures/verity/custom/yourfile.png")));
        textureField.setMaxLength(256);
        y += 26;

        boxTextureField = addDrawableChild(new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, 20,
                Text.literal("textures/verity/custom/yourfile_box.png")));
        boxTextureField.setMaxLength(256);
        y += 34;

        int btnW = (fieldWidth - 12) / 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> onSave())
                .dimensions(rightX, y, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> loadIntoFields(currentDefinitionOrBlank()))
                .dimensions(rightX + btnW + 4, y, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Duplicate"), b -> onDuplicate())
                .dimensions(rightX + (btnW + 4) * 2, y, btnW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> onDelete())
                .dimensions(rightX + (btnW + 4) * 3, y, btnW, 20).build());

        loadIntoFields(currentDefinitionOrBlank());
    }

    private int x() { return PADDING; }

    private void rebuildListButtons() {
        listButtons.forEach(this::remove);
        listButtons.clear();

        int listY = 24;
        ButtonWidget createNew = addDrawableChild(ButtonWidget.builder(Text.literal("+ Create New"), b -> {
            editingId = null;
            loadIntoFields(blankDefinition());
        }).dimensions(x(), listY, LIST_WIDTH, 20).build());
        listButtons.add(createNew);
        listY += 24;

        for (WispDefinition def : WispDefinitionManager.getAll()) {
            if (def.isDeleted()) continue;
            String id = def.getInternalId();
            ButtonWidget btn = addDrawableChild(ButtonWidget.builder(Text.literal(def.getDisplayName()), b -> {
                editingId = id;
                loadIntoFields(def);
            }).dimensions(x(), listY, LIST_WIDTH, 20).build());
            listButtons.add(btn);
            listY += 24;
        }
    }

    private WispDefinition blankDefinition() {
        return new WispDefinition(WispDefinition.newInternalId(), "New Wisp", "#66CCFF", "New Wisp Box", "#336699");
    }

    private WispDefinition currentDefinitionOrBlank() {
        if (editingId == null) return blankDefinition();
        return WispDefinitionManager.get(editingId).orElse(blankDefinition());
    }

    private void loadIntoFields(WispDefinition def) {
        nameField.setText(def.getDisplayName());
        colorField.setText(def.getColorHex());
        boxNameField.setText(def.getBoxDisplayName());
        boxColorField.setText(def.getBoxColorHex());
        textureField.setText(def.getTextureRef());
        boxTextureField.setText(def.getBoxTextureRef());
        statusMessage = "";
    }

    private WispDefinition buildDefinitionFromFields(String internalId) {
        WispDefinition def = new WispDefinition(internalId, nameField.getText(), colorField.getText(),
                boxNameField.getText(), boxColorField.getText());
        def.setTextureRef(textureField.getText());
        def.setBoxTextureRef(boxTextureField.getText());
        return def;
    }

    private void onSave() {
        if (!WispDefinition.isValidDisplayName(nameField.getText())) {
            statusMessage = "Name must be 1-32 characters.";
            return;
        }
        if (!WispDefinition.isValidHexColor(colorField.getText())) {
            statusMessage = "Color must look like #RRGGBB.";
            return;
        }
        if (!WispDefinition.isValidHexColor(boxColorField.getText())) {
            statusMessage = "Box color must look like #RRGGBB.";
            return;
        }
        if (!WispDefinition.isValidTextureRef(textureField.getText())
                || !WispDefinition.isValidTextureRef(boxTextureField.getText())) {
            statusMessage = "Texture path looks invalid.";
            return;
        }

        boolean isNew = editingId == null;
        WispDefinition def = buildDefinitionFromFields(isNew ? WispDefinition.newInternalId() : editingId);
        var payload = WispPayloads.RequestEditPayload.createOrEdit(
                isNew ? WispPayloads.Action.CREATE : WispPayloads.Action.EDIT, def);
        ClientPlayNetworking.send(payload);
        editingId = def.getInternalId();
        statusMessage = "Saved (waiting for server confirmation)...";
        // The list refreshes next time SyncDefinitionsPayload arrives and applies to the manager;
        // call rebuildListButtons() from a tick hook if you want it to refresh live without reopening.
    }

    private void onDuplicate() {
        if (editingId == null) { statusMessage = "Save this Wisp first, then duplicate it."; return; }
        ClientPlayNetworking.send(WispPayloads.RequestEditPayload.createOrEdit(
                WispPayloads.Action.DUPLICATE, currentDefinitionOrBlank()));
        statusMessage = "Duplicating...";
    }

    private void onDelete() {
        if (editingId == null) { statusMessage = "Nothing to delete."; return; }
        ClientPlayNetworking.send(WispPayloads.RequestEditPayload.delete(editingId));
        statusMessage = "Delete requested...";
        editingId = null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawText(this.textRenderer, "Wisps", x(), 8, 0xFFFFFF, true);

        int rightX = x() + LIST_WIDTH + PADDING * 2;
        context.drawText(this.textRenderer, "Name", rightX, 30, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Color", rightX, 56, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Box name", rightX, 82, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Box color", rightX, 108, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Texture path", rightX, 134, 0xAAAAAA, false);
        context.drawText(this.textRenderer, "Box texture path", rightX, 160, 0xAAAAAA, false);

        // Live color swatches next to the hex fields
        drawSwatch(context, rightX + colorField.getWidth() + 6, 66, colorField.getText());
        drawSwatch(context, rightX + boxColorField.getWidth() + 6, 118, boxColorField.getText());

        if (!statusMessage.isEmpty()) {
            context.drawText(this.textRenderer, statusMessage, rightX, 230, 0xFFFF55, false);
        }
    }

    private void drawSwatch(DrawContext context, int x, int y, String hex) {
        int color = WispDefinition.isValidHexColor(hex)
                ? (0xFF000000 | Integer.parseInt(hex.substring(1), 16))
                : 0xFF555555;
        context.fill(x, y, x + 12, y + 12, color);
    }

    @Override
    public boolean shouldPause() { return false; }
}
