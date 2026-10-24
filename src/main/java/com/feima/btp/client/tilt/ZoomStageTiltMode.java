package com.feima.btp.client.tilt;

import com.feima.btp.config.BTPConfig;
import com.feima.btp.util.BTPTipsHelper;
import com.feima.btp.util.InputHelper;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.network.message.ClientMessagePlayerZoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

import static com.feima.btp.client.BTPEventHandler.HOLD_TILT_KEY;
import static com.feima.btp.client.BTPEventHandler.TOGGLE_TILT_KEY;

/**
 * V 键虚拟档位模式。
 *
 * <p>把"据枪"作为倍率循环的最后一档：
 * <pre>
 *     1x → 2x → ... → N-1 → [据枪档] → 1x
 * </pre>
 *
 * <p><b>两个独立概念</b>：
 * <ul>
 *   <li><b>据枪档</b>（{@link #zoomStageSelected}）：持久状态，只在按 V 时切换</li>
 *   <li><b>据枪姿态</b>：仅在据枪档且右键按下时生效</li>
 * </ul>
 *
 * <p><b>提示语义</b>：
 * <ul>
 *   <li>进入据枪档 → 显示「战术据枪」（{@code message.btp.tilt_on}）</li>
 *   <li>退出据枪档 → 显示回到的第一档倍率（{@code message.btp.zoom_hint}，如 "1x"）；
 *       机瞄枪固定显示 "1x"，单档瞄具显示其唯一倍率</li>
 *   <li>普通倍率切换 → 显示下一档倍率（{@code message.btp.zoom_hint}）</li>
 * </ul>
 *
 * <p><b>进入据枪档的 aim 要求</b>：只要处于"最后一档"（含机瞄、单档瞄具、
 * 多档瞄具的最后一档），按 V 就进入据枪档，<b>不要求瞄准</b>。
 * 这是 ZOOM_STAGE 模式的核心语义——"据枪"就是倍率循环的最后一档，
 * 与玩家是否开镜无关。
 *
 * <p>Hold 键由 {@link TiltController} 独立处理，本类不参与。
 */
public final class ZoomStageTiltMode implements TiltMode {

    public static final ZoomStageTiltMode INSTANCE = new ZoomStageTiltMode();

    /** 是否处于"据枪档"。 */
    private boolean zoomStageSelected = false;

    private ZoomStageTiltMode() {}

    @Override
    public String id() {
        return "zoom_stage";
    }

    // ============================================================
    // 事件处理
    // ============================================================

    @Override
    public void onKeyInput(Minecraft mc, LocalPlayer player, int keyCode, int action) {
        // V 键由 ZoomKeyMixin 拦截
    }

    @Override
    public boolean onMouseInput(Minecraft mc, LocalPlayer player, int button, int action) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;

        // Hold 键激活期间，右键不接管
        if (TiltController.isHoldActive()) return false;

        // 只有在据枪档时接管右键
        if (!zoomStageSelected) return false;

        if (action == GLFW.GLFW_PRESS) {
            if (!TiltController.isTilting()) {
                TiltController.enterTilt(false);
            }
            return true;
        }
        if (action == GLFW.GLFW_RELEASE) {
            if (TiltController.isTilting()) {
                TiltController.exitTilt();
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClientTick(Minecraft mc, LocalPlayer player) {
        while (TOGGLE_TILT_KEY.consumeClick()) {}
        while (HOLD_TILT_KEY.consumeClick()) {}

        // Hold 键激活期间跳过
        if (TiltController.isHoldActive()) {
            return;
        }

        // GUI 打开：强制退出据枪档
        if (mc.screen != null) {
            if (zoomStageSelected || TiltController.isTilting()) {
                forceReset();
            }
            return;
        }

        // 切到非枪物品：强制退出据枪档
        if (zoomStageSelected && !TiltController.isHoldingGun(player)) {
            forceReset();
            return;
        }

        // 据枪档下：每 tick 强制压住瞄准
        if (zoomStageSelected) {
            AimKey.AIM_KEY.setDown(false);
            IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
            if (op.isAim()) {
                op.aim(false);
            }
        }
    }

    @Override
    public void reset() {
        zoomStageSelected = false;
    }

    @Override
    public void onHoldKeyReleased(LocalPlayer player) {
        // 据枪档仍选中且右键仍按着 → 恢复据枪姿态
        if (zoomStageSelected && InputHelper.isPhysicalRightPressed()) {
            if (TiltController.isHoldingGun(player)) {
                TiltController.enterTilt(false);
            }
        }
    }

    // ============================================================
    // V 键入口（由 ZoomKeyMixin 调用）
    // ============================================================

    public boolean tryHandleZoomAsTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return false;
        if (mc.screen != null) return false;
        if (!TiltController.isHoldingGun(player)) return false;

        // 情况 1：已在据枪档 → 退出据枪档，回到第一档倍率
        if (zoomStageSelected) {
            exitZoomStage(player);
            float firstZoom = getFirstZoom(player);
            if (firstZoom >= 0) {
                BTPTipsHelper.sendZoomHintMessage(player, firstZoom);
            }
            return true;
        }

        // 情况 2：不在据枪档 → 必须处于最后一档
        // FIX: 不再要求瞄准。机瞄 / 单档瞄具 / 多档瞄具的最后一档，
        //      按 V 的语义就是"进入据枪档"，与是否开镜无关。
        if (!isAtLastZoomStage(player)) return false;

        enterZoomStage(player);
        // 进入据枪档显示「战术据枪」（message.btp.tilt_on）
        BTPTipsHelper.sendTiltOnMessage(player);
        return true;
    }

    // ============================================================
    // 倍率查询（供提示使用）
    // ============================================================

    /**
     * 获取"第一档"倍率——即退出据枪档后玩家会回到的倍率。
     *
     * <p>TaCZ 服务端处理 zoom 包时把 zoomNumber 从 N-1 wrap 到 0，
     * 因此退出据枪档后总是回到 {@code zooms[0]}。
     *
     * <p><b>机瞄枪特例</b>：无瞄具时固定返回 {@code 1.0f}，显示"1 倍"，
     * 而非 TaCZ 的 {@code ironZoom}。ironZoom 是视角缩放系数（渲染层面），
     * 玩家通常不把它理解为"倍率"，显示 1.0 更符合直觉。
     *
     * <p><b>为什么不直接调 {@code iGun.getAimingZoom}</b>：据枪档退出时
     * zoom 包是异步发送的，客户端本地 NBT 在服务端响应前仍为 N-1，
     * 此时调用 {@code getAimingZoom} 会得到最后一档倍率，而非第一档。
     * 所以必须手动计算。
     *
     * @return 第一档倍率；返回 -1 表示无法确定（无枪 / 瞄具索引缺失等）
     */
    public static float getFirstZoom(LocalPlayer player) {
        if (player == null) return -1f;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return -1f;

        ResourceLocation scopeId = iGun.getAttachmentId(gun, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            // 机瞄枪固定显示 1 倍，不显示 ironZoom
            return 1.0f;
        }

        Optional<ClientAttachmentIndex> idx = TimelessAPI.getClientAttachmentIndex(scopeId);
        if (idx.isEmpty()) return -1f;
        float[] zooms = idx.get().getZoom();
        if (zooms == null || zooms.length == 0) return -1f;
        return zooms[0];
    }

    /**
     * 预测按 V 后切换到的下一档倍率值。返回 -1 表示当前无法切换倍率。
     *
     * <p><b>无法切换倍率的情形</b>：
     * <ul>
     *   <li>无瞄具（机瞄）——TaCZ 不读 zoomNumber，无档位可切</li>
     *   <li>内置瞄具——TaCZ {@code getAimingZoom} 对 builtin 固定读 0 号档</li>
     *   <li>单档瞄具——只有一档，无法循环</li>
     * </ul>
     */
    public static float getNextZoom(LocalPlayer player) {
        if (player == null) return -1f;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return -1f;

        ResourceLocation scopeId = iGun.getAttachmentId(gun, AttachmentType.SCOPE);
        boolean builtin = false;
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
            builtin = true;
        }
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return -1f; // 无瞄具
        }
        if (builtin) {
            return -1f; // 内置瞄具固定 0 号档，不切换
        }

        Optional<ClientAttachmentIndex> idx = TimelessAPI.getClientAttachmentIndex(scopeId);
        if (idx.isEmpty()) return -1f;
        float[] zooms = idx.get().getZoom();
        if (zooms == null || zooms.length <= 1) {
            return -1f; // 单档瞄具，无循环
        }

        int current = AttachmentItemDataAccessor.getZoomNumberFromTag(
                iGun.getAttachmentTag(gun, AttachmentType.SCOPE));
        int next = (current + 1) % zooms.length;
        return zooms[next];
    }

    /**
     * 显示"下一档倍率"提示。无法切换倍率时不显示。
     *
     * <p>由 {@code ZoomKeyMixin} 在确认本次 V 键会实际发包时调用。
     */
    public static void showNextZoomHint(LocalPlayer player) {
        float nextZoom = getNextZoom(player);
        if (nextZoom < 0) return;
        BTPTipsHelper.sendZoomHintMessage(player, nextZoom);
    }

    // ============================================================
    // 内部逻辑
    // ============================================================

    private boolean isAtLastZoomStage(LocalPlayer player) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) return false;

        ResourceLocation scopeId = iGun.getAttachmentId(gun, AttachmentType.SCOPE);
        boolean builtin = false;
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(gun, AttachmentType.SCOPE);
            builtin = true;
        }
        // 无瞄具 → 机瞄，视为最后一档
        if (DefaultAssets.isEmptyAttachmentId(scopeId)) {
            return true;
        }

        Optional<ClientAttachmentIndex> idx = TimelessAPI.getClientAttachmentIndex(scopeId);
        if (idx.isEmpty()) return false;
        float[] zooms = idx.get().getZoom();
        // 单档瞄具 → 视为最后一档
        if (zooms == null || zooms.length <= 1) {
            return true;
        }

        int current = builtin
                ? 0
                : AttachmentItemDataAccessor.getZoomNumberFromTag(
                        iGun.getAttachmentTag(gun, AttachmentType.SCOPE));
        return (current % zooms.length) == zooms.length - 1;
    }

    private void enterZoomStage(LocalPlayer player) {
        zoomStageSelected = true;
        if (InputHelper.isPhysicalRightPressed()) {
            TiltController.enterTilt(false);
        }
    }

    /**
     * 退出据枪档，并把 TaCZ 的 zoom 档位从最后一档 wrap 回第一档。
     *
     * <p><b>顺序契约</b>：据枪档期间 {@link #onClientTick} 每 tick 强制
     * {@code aim(false)}，而 TaCZ 服务端处理 {@code ClientMessagePlayerZoom}
     * 时要求玩家处于瞄准状态。因此在发包前必须临时进入瞄准态，
     * 否则包被服务端忽略，zoom 档位停留在最后一档。
     */
    private void exitZoomStage(LocalPlayer player) {
        zoomStageSelected = false;

        if (TiltController.isTilting()) {
            TiltController.exitTilt();
        }

        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        boolean wasAim = op.isAim();

        if (!wasAim) {
            op.aim(true);
            AimKey.AIM_KEY.setDown(true);
        }

        // 通过 TaCZ 通道发 zoom 包，服务端把 zoomNumber 从 N-1 wrap 到 0。
        com.tacz.guns.network.NetworkHandler.CHANNEL
                .sendToServer(new ClientMessagePlayerZoom());

        if (!wasAim && !InputHelper.isPhysicalRightPressed()) {
            op.aim(false);
            AimKey.AIM_KEY.setDown(false);
        }
    }

    private void forceReset() {
        zoomStageSelected = false;
        TiltController.forceReset();
    }
}