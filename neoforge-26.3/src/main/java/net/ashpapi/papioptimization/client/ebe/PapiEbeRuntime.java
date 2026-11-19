package net.ashpapi.papioptimization.client.ebe;

import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.model.monster.shulker.ShulkerModel;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.model.object.bell.BellModel;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.model.object.skull.DragonHeadModel;
import net.minecraft.client.model.object.skull.PiglinHeadModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotPattern;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiEbeRuntime {

    private static final double BANNER_DYNAMIC_ENTER_DIST_SQ = 20.0 * 20.0;
    private static final double BANNER_DYNAMIC_EXIT_DIST_SQ = 26.0 * 26.0;
    private static final int MAX_POT_CACHE_SIZE = 512;

    private static volatile boolean sectionCompilerHooked = false;
    private static volatile boolean atlasPatched = false;
    private static volatile String activeBackend = "disabled";

    private static volatile double camX;
    private static volatile double camY;
    private static volatile double camZ;
    private static volatile boolean camValid = false;
    private static volatile long lastClientGameTime = 0L;

    private static final ConcurrentHashMap<BlockState, EpochModel> MODEL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PotModelKey, EpochModel> POT_MODEL_CACHE = new ConcurrentHashMap<>();
    private static final Object CACHE_LOCK = new Object();
    private static final AtomicInteger RELOAD_EPOCH = new AtomicInteger(1);
    private static final BlockStateModel MISSING_SENTINEL = new SingleVariant(new SimpleBakedPart(Collections.emptyList(), null, 0));

    private record PotModelKey(Direction facing, PotDecorations decorations) {}

    private record EpochModel(int epoch, BlockStateModel model) {}

    private PapiEbeRuntime() {}

    public static void markSectionCompilerHooked() {
        markSectionCompilerHooked("vanilla");
    }

    public static void markSectionCompilerHooked(String backend) {
        activeBackend = backend;
        sectionCompilerHooked = true;
    }

    public static void markAtlasPatched() {
        atlasPatched = true;
    }

    public static void disable() {
        sectionCompilerHooked = false;
        activeBackend = "disabled";
    }

    public static boolean isActive() {
        return sectionCompilerHooked && atlasPatched;
    }

    public static String getActiveBackend() {
        return isActive() ? activeBackend : "disabled";
    }

    public static void updateFrameContext(double x, double y, double z, long gameTime) {
        camX = x;
        camY = y;
        camZ = z;
        camValid = true;
        lastClientGameTime = gameTime;
    }

    public static void resetFrameContext() {
        camValid = false;
        lastClientGameTime = 0L;
    }

    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            RELOAD_EPOCH.incrementAndGet();
            MODEL_CACHE.clear();
            POT_MODEL_CACHE.clear();
        }
        PapiSodiumEbeBridge.clearPending();
        resetFrameContext();
    }

    public static boolean isBakeableBlock(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.ENDER_CHEST
                || block == Blocks.BELL
                || block == Blocks.DECORATED_POT) {
            return true;
        }
        if (block instanceof CopperChestBlock || block instanceof ShulkerBoxBlock) {
            return block.getClass().getName().startsWith("net.minecraft.");
        }
        if (block instanceof AbstractSkullBlock skull) {
            return skull.getType() != SkullBlock.Types.PLAYER
                    && block.getClass().getName().startsWith("net.minecraft.");
        }
        if (block instanceof AbstractBannerBlock) {
            return block.getClass().getName().startsWith("net.minecraft.");
        }
        return false;
    }

    public static boolean isDynamicNow(BlockEntity be, BlockState state, @Nullable BlockGetter blockGetter) {
        if (be instanceof ChestBlockEntity chest) {
            if (chest.getOpenNess(0.0F) > 0.0F || chest.getOpenNess(1.0F) > 0.0F) {
                return true;
            }
            if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    BlockGetter getter = blockGetter != null ? blockGetter : chest.getLevel();
                    if (getter != null) {
                        BlockPos otherPos = chest.getBlockPos().relative(ChestBlock.getConnectedDirection(state));
                        BlockEntity otherBe = getter.getBlockEntity(otherPos);
                        if (otherBe instanceof ChestBlockEntity otherChest) {
                            if (otherChest.getOpenNess(0.0F) > 0.0F || otherChest.getOpenNess(1.0F) > 0.0F) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
        if (be instanceof EnderChestBlockEntity enderChest) {
            return enderChest.getOpenNess(0.0F) > 0.0F || enderChest.getOpenNess(1.0F) > 0.0F;
        }
        if (be instanceof ShulkerBoxBlockEntity shulker) {
            return shulker.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED
                    || shulker.getProgress(0.0F) > 0.0F
                    || shulker.getProgress(1.0F) > 0.0F;
        }
        if (be instanceof BellBlockEntity bell) {
            return bell.shaking || bell.ticks > 0;
        }
        if (be instanceof DecoratedPotBlockEntity pot) {
            DecoratedPotBlockEntity.WobbleStyle wobble = pot.lastWobbleStyle;
            if (wobble == null) {
                return false;
            }
            long now = (blockGetter instanceof Level lvl) ? lvl.getGameTime() : lastClientGameTime;
            long elapsed = now - pot.wobbleStartedAtTick;
            return elapsed >= 0L && elapsed < wobble.duration;
        }
        if (be instanceof SkullBlockEntity skull) {
            if (state.hasProperty(AbstractSkullBlock.POWERED) && state.getValue(AbstractSkullBlock.POWERED)) {
                return true;
            }
            return skull.getAnimation(0.0F) > 0.0F || skull.getAnimation(1.0F) > 0.0F;
        }
        if (be instanceof BannerBlockEntity banner) {
            if (!banner.getPatterns().layers().isEmpty()) {
                return true;
            }
            if (!camValid) {
                return true;
            }
            BlockPos pos = be.getBlockPos();
            double dx = (pos.getX() + 0.5) - camX;
            double dy = (pos.getY() + 0.5) - camY;
            double dz = (pos.getZ() + 0.5) - camZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            boolean wasDynamic = be instanceof PapiEbeDynamicTracker tracker && tracker.papi$wasEbeDynamic();
            return wasDynamic ? distSq < BANNER_DYNAMIC_EXIT_DIST_SQ : distSq < BANNER_DYNAMIC_ENTER_DIST_SQ;
        }
        return true;
    }

    public static boolean shouldSkipDynamicRender(BlockEntity be, ClientLevel level, boolean hasCrumblingOverlay) {
        if (!isActive()) {
            return false;
        }
        BlockState state = be.getBlockState();
        if (!isBakeableBlock(state)) {
            return false;
        }
        BlockStateModel baked = getStaticModel(state, be);
        if (baked == null) {
            return false;
        }
        boolean dynamic = hasCrumblingOverlay || isDynamicNow(be, state, level);
        PapiEbeDynamicTracker tracker = (be instanceof PapiEbeDynamicTracker) ? (PapiEbeDynamicTracker) be : null;
        if (tracker == null) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (dynamic != tracker.papi$wasEbeDynamic()) {
            tracker.papi$setEbeDynamic(dynamic);
            requestRebuild(level, be.getBlockPos(), state);
            tracker.papi$setEbeCloseGraceUntil(gameTime + 2L);
            return dynamic && tracker.papi$isEbeBaked();
        }
        if (dynamic) {
            if (tracker.papi$isEbeBaked()) {
                if (gameTime >= tracker.papi$getEbeCloseGraceUntil()) {
                    requestRebuild(level, be.getBlockPos(), state);
                    tracker.papi$setEbeCloseGraceUntil(gameTime + 2L);
                    return false;
                }
                return true;
            }
            return false;
        }
        if (!tracker.papi$isEbeBaked()) {
            if (gameTime >= tracker.papi$getEbeCloseGraceUntil()) {
                requestRebuild(level, be.getBlockPos(), state);
                tracker.papi$setEbeCloseGraceUntil(gameTime + 2L);
            }
            return false;
        }
        return true;
    }

    private static void requestRebuild(ClientLevel level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                BlockPos otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.getBlock() instanceof ChestBlock) {
                    level.sendBlockUpdated(otherPos, otherState, otherState, Block.UPDATE_IMMEDIATE);
                }
            }
        }
    }

    public static @Nullable BlockStateModel getStaticModel(BlockState state) {
        return getStaticModel(state, null);
    }

    public static @Nullable BlockStateModel getStaticModel(BlockState state, @Nullable BlockEntity be) {
        int epoch = RELOAD_EPOCH.get();
        if (state.getBlock() instanceof DecoratedPotBlock) {
            PotDecorations decorations = (be instanceof DecoratedPotBlockEntity pot && pot.getDecorations() != null)
                    ? pot.getDecorations()
                    : PotDecorations.EMPTY;
            Direction facing = state.hasProperty(DecoratedPotBlock.HORIZONTAL_FACING)
                    ? state.getValue(DecoratedPotBlock.HORIZONTAL_FACING)
                    : Direction.NORTH;
            PotModelKey key = new PotModelKey(facing, decorations);
            EpochModel cached = POT_MODEL_CACHE.get(key);
            if (cached != null && cached.epoch() == epoch) {
                return cached.model() == MISSING_SENTINEL ? null : cached.model();
            }
            TextureAtlas blocksAtlas = getBlocksAtlas();
            if (blocksAtlas == null) {
                return null;
            }
            BlockStateModel built = buildDecoratedPotModel(facing, decorations, blocksAtlas);
            synchronized (CACHE_LOCK) {
                if (RELOAD_EPOCH.get() != epoch) {
                    return null;
                }
                if (POT_MODEL_CACHE.size() >= MAX_POT_CACHE_SIZE) {
                    POT_MODEL_CACHE.clear();
                }
                POT_MODEL_CACHE.put(key, new EpochModel(epoch, built != null ? built : MISSING_SENTINEL));
            }
            return built;
        }
        EpochModel cached = MODEL_CACHE.get(state);
        if (cached != null && cached.epoch() == epoch) {
            return cached.model() == MISSING_SENTINEL ? null : cached.model();
        }
        BlockStateModel built = buildStaticModel(state);
        synchronized (CACHE_LOCK) {
            if (RELOAD_EPOCH.get() != epoch) {
                return null;
            }
            MODEL_CACHE.put(state, new EpochModel(epoch, built != null ? built : MISSING_SENTINEL));
        }
        return built;
    }

    private static @Nullable TextureAtlas getBlocksAtlas() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getAtlasManager() == null) {
            return null;
        }
        try {
            return mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable BlockStateModel buildStaticModel(BlockState state) {
        TextureAtlas blocksAtlas = getBlocksAtlas();
        if (blocksAtlas == null) {
            return null;
        }
        Block block = state.getBlock();
        if (block instanceof ChestBlock) {
            return buildChestModel(state, blocksAtlas);
        }
        if (block instanceof EnderChestBlock) {
            return buildEnderChestModel(state, blocksAtlas);
        }
        if (block instanceof ShulkerBoxBlock shulker) {
            return buildShulkerModel(state, shulker, blocksAtlas);
        }
        if (block instanceof BellBlock) {
            return buildBellModel(blocksAtlas);
        }
        if (block instanceof AbstractSkullBlock skullBlock) {
            return buildSkullModel(state, skullBlock, blocksAtlas);
        }
        if (block instanceof AbstractBannerBlock bannerBlock) {
            return buildBannerModel(state, bannerBlock, blocksAtlas);
        }
        return null;
    }

    private static @Nullable BlockStateModel buildChestModel(BlockState state, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(ChestBlock.FACING);
        ChestType type = state.getValue(ChestBlock.TYPE);
        Block block = state.getBlock();
        SpriteId spriteId;
        if (block instanceof CopperChestBlock copper) {
            spriteId = Sheets.CHEST_COPPER.pick(copper.getState()).select(type);
        } else if (ChestRenderer.xmasTextures()) {
            spriteId = Sheets.CHEST_CHRISTMAS.select(type);
        } else if (block instanceof TrappedChestBlock) {
            spriteId = Sheets.CHEST_TRAPPED.select(type);
        } else {
            spriteId = Sheets.CHEST_REGULAR.select(type);
        }
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root;
        if (type == ChestType.LEFT) {
            root = ChestModel.createDoubleBodyLeftLayer().bakeRoot();
        } else if (type == ChestType.RIGHT) {
            root = ChestModel.createDoubleBodyRightLayer().bakeRoot();
        } else {
            root = ChestModel.createSingleBodyLayer().bakeRoot();
        }
        new ChestModel(root).setupAnim(0.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(ChestRenderer.modelTransformation(facing));
        return bakeSinglePart(root, poseStack, sprite, Transparency.NONE, -1);
    }

    private static @Nullable BlockStateModel buildEnderChestModel(BlockState state, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(EnderChestBlock.FACING);
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, Sheets.ENDER_CHEST_LOCATION.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = ChestModel.createSingleBodyLayer().bakeRoot();
        new ChestModel(root).setupAnim(0.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(ChestRenderer.modelTransformation(facing));
        return bakeSinglePart(root, poseStack, sprite, Transparency.NONE, -1);
    }

    private static @Nullable BlockStateModel buildShulkerModel(BlockState state, ShulkerBoxBlock block, TextureAtlas blocksAtlas) {
        Direction facing = state.getValue(ShulkerBoxBlock.FACING);
        DyeColor color = block.getColor();
        SpriteId spriteId = color == null
                ? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
                : Sheets.SHULKER_TEXTURE_LOCATION.get(color.getId());
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = ShulkerModel.createBoxLayer().bakeRoot();
        PoseStack poseStack = new PoseStack();
        Matrix4f mat = new Matrix4f()
                .translation(0.5F, 0.5F, 0.5F)
                .scale(0.9995F)
                .rotate(facing.getRotation())
                .scale(1.0F, -1.0F, -1.0F)
                .translate(0.0F, -1.0F, 0.0F);
        poseStack.mulPose(mat);
        return bakeSinglePart(root, poseStack, sprite, Transparency.NONE, -1);
    }

    private static @Nullable BlockStateModel buildBellModel(TextureAtlas blocksAtlas) {
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, BellRenderer.BELL_TEXTURE.texture());
        if (sprite == null) {
            return null;
        }
        ModelPart root = BellModel.createBodyLayer().bakeRoot();
        new BellModel(root).setupAnim(new BellModel.State(0.0F, null));
        PoseStack poseStack = new PoseStack();
        return bakeSinglePart(root, poseStack, sprite, Transparency.NONE, -1);
    }

    private static @Nullable BlockStateModel buildDecoratedPotModel(
            Direction facing,
            PotDecorations decorations,
            TextureAtlas blocksAtlas
    ) {
        TextureAtlasSprite baseSprite = lookupSprite(blocksAtlas, Sheets.DECORATED_POT_BASE.texture());
        TextureAtlasSprite blankSide = lookupSprite(blocksAtlas, Sheets.DECORATED_POT_SIDE.texture());
        if (baseSprite == null || blankSide == null) {
            return null;
        }
        TextureAtlasSprite frontSprite = resolvePotSideSprite(decorations.front(), blocksAtlas, blankSide);
        TextureAtlasSprite backSprite = resolvePotSideSprite(decorations.back(), blocksAtlas, blankSide);
        TextureAtlasSprite leftSprite = resolvePotSideSprite(decorations.left(), blocksAtlas, blankSide);
        TextureAtlasSprite rightSprite = resolvePotSideSprite(decorations.right(), blocksAtlas, blankSide);

        ModelPart baseRoot = DecoratedPotRenderer.createBaseLayer().bakeRoot();
        ModelPart sidesRoot = DecoratedPotRenderer.createSidesLayer().bakeRoot();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(DecoratedPotRenderer.modelTransformation(facing));

        List<BakedQuad> quads = new ArrayList<>();
        appendQuads(baseRoot.getChild("neck"), poseStack, baseSprite, Transparency.NONE, -1, quads);
        appendQuads(baseRoot.getChild("top"), poseStack, baseSprite, Transparency.NONE, -1, quads);
        appendQuads(baseRoot.getChild("bottom"), poseStack, baseSprite, Transparency.NONE, -1, quads);
        appendQuads(sidesRoot.getChild("front"), poseStack, frontSprite, Transparency.NONE, -1, quads);
        appendQuads(sidesRoot.getChild("back"), poseStack, backSprite, Transparency.NONE, -1, quads);
        appendQuads(sidesRoot.getChild("left"), poseStack, leftSprite, Transparency.NONE, -1, quads);
        appendQuads(sidesRoot.getChild("right"), poseStack, rightSprite, Transparency.NONE, -1, quads);

        return finalizeModel(quads, baseSprite);
    }

    private static TextureAtlasSprite resolvePotSideSprite(
            Optional<ItemStackTemplate> optSherd,
            TextureAtlas blocksAtlas,
            TextureAtlasSprite fallback
    ) {
        if (optSherd.isEmpty()) {
            return fallback;
        }
        Holder<DecoratedPotPattern> holder = optSherd.get().get(DataComponents.PROVIDES_POTTERY_PATTERN);
        if (holder == null) {
            return fallback;
        }
        SpriteId spriteId = Sheets.DECORATED_POT_MAPPER.apply(holder.value().assetId());
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteId.texture());
        return sprite != null ? sprite : fallback;
    }

    private static @Nullable BlockStateModel buildSkullModel(
            BlockState state,
            AbstractSkullBlock block,
            TextureAtlas blocksAtlas
    ) {
        if (!(block.getType() instanceof SkullBlock.Types type) || type == SkullBlock.Types.PLAYER) {
            return null;
        }
        Identifier skinPath = SkullBlockRenderer.SKIN_BY_TYPE.get(type);
        if (skinPath == null) {
            return null;
        }
        Identifier spriteLoc = stripTexturePath(skinPath);
        TextureAtlasSprite sprite = lookupSprite(blocksAtlas, spriteLoc);
        if (sprite == null) {
            return null;
        }

        ModelPart root;
        SkullModelBase model;
        Transparency transparency = Transparency.TRANSPARENT;
        switch (type) {
            case SKELETON, WITHER_SKELETON, CREEPER -> {
                root = SkullModel.createMobHeadLayer().bakeRoot();
                model = new SkullModel(root);
                transparency = Transparency.NONE;
            }
            case ZOMBIE -> {
                root = SkullModel.createHumanoidHeadLayer().bakeRoot();
                model = new SkullModel(root);
            }
            case PIGLIN -> {
                root = LayerDefinition.create(PiglinHeadModel.createHeadModel(), 64, 64).bakeRoot();
                model = new PiglinHeadModel(root);
            }
            case DRAGON -> {
                root = DragonHeadModel.createHeadLayer().bakeRoot();
                model = new DragonHeadModel(root);
            }
            default -> {
                return null;
            }
        }

        SkullModelBase.State animState = new SkullModelBase.State();
        animState.animationPos = 0.0F;
        animState.yRot = 0.0F;
        model.setupAnim(animState);

        PoseStack poseStack = new PoseStack();
        if (block instanceof WallSkullBlock) {
            poseStack.mulPose(SkullBlockRenderer.TRANSFORMATIONS.wallTransformation(state.getValue(WallSkullBlock.FACING)));
        } else if (state.hasProperty(SkullBlock.ROTATION)) {
            poseStack.mulPose(SkullBlockRenderer.TRANSFORMATIONS.freeTransformations(state.getValue(SkullBlock.ROTATION)));
        } else {
            return null;
        }
        return bakeSinglePart(root, poseStack, sprite, transparency, -1);
    }

    private static @Nullable BlockStateModel buildBannerModel(
            BlockState state,
            AbstractBannerBlock block,
            TextureAtlas blocksAtlas
    ) {
        TextureAtlasSprite baseSprite = lookupSprite(blocksAtlas, Sheets.BANNER_BASE.texture());
        TextureAtlasSprite flagSprite = lookupSprite(blocksAtlas, Sheets.BANNER_PATTERN_BASE.texture());
        if (baseSprite == null || flagSprite == null) {
            return null;
        }
        boolean standing = block instanceof BannerBlock;
        PoseStack poseStack = new PoseStack();
        if (standing) {
            poseStack.mulPose(BannerRenderer.TRANSFORMATIONS.freeTransformations(state.getValue(BannerBlock.ROTATION)));
        } else if (block instanceof WallBannerBlock) {
            poseStack.mulPose(BannerRenderer.TRANSFORMATIONS.wallTransformation(state.getValue(WallBannerBlock.FACING)));
        } else {
            return null;
        }

        ModelPart bodyRoot = BannerModel.createBodyLayer(standing).bakeRoot();
        ModelPart flagRoot = BannerFlagModel.createFlagLayer(standing).bakeRoot();
        new BannerFlagModel(flagRoot).setupAnim(0.0F);

        int tintIndex = block.getColor().getId();
        List<BakedQuad> quads = new ArrayList<>();
        appendQuads(bodyRoot, poseStack, baseSprite, Transparency.NONE, -1, quads);
        appendQuads(flagRoot, poseStack, flagSprite, Transparency.NONE, tintIndex, quads);
        return finalizeModel(quads, baseSprite);
    }

    private static Identifier stripTexturePath(Identifier textureLoc) {
        String path = textureLoc.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return Identifier.fromNamespaceAndPath(textureLoc.getNamespace(), path);
    }

    private static @Nullable TextureAtlasSprite lookupSprite(TextureAtlas atlas, Identifier textureId) {
        TextureAtlasSprite sprite = atlas.getSprite(textureId);
        if (sprite == null || sprite == atlas.missingSprite()) {
            return null;
        }
        return sprite;
    }

    private static BlockStateModel bakeSinglePart(
            ModelPart root,
            PoseStack poseStack,
            TextureAtlasSprite sprite,
            Transparency transparency,
            int tintIndex
    ) {
        List<BakedQuad> quads = new ArrayList<>();
        appendQuads(root, poseStack, sprite, transparency, tintIndex, quads);
        return finalizeModel(quads, sprite);
    }

    private static void appendQuads(
            ModelPart root,
            PoseStack poseStack,
            TextureAtlasSprite sprite,
            Transparency transparency,
            int tintIndex,
            List<BakedQuad> quads
    ) {
        Material.Baked material = new Material.Baked(sprite, false);
        BakedQuad.MaterialInfo matInfo = BakedQuad.MaterialInfo.of(material, transparency, tintIndex, null, 0, true);
        Vector3f tmpNormal = new Vector3f();

        root.visit(poseStack, (pose, path, idx, cube) -> {
            Matrix4f posMat = pose.pose();
            Matrix3f normMat = pose.normal();
            for (ModelPart.Polygon poly : cube.polygons) {
                ModelPart.Vertex[] verts = poly.vertices();
                if (verts.length < 4) {
                    continue;
                }
                Vector3f p0 = posMat.transformPosition(verts[0].worldX(), verts[0].worldY(), verts[0].worldZ(), new Vector3f());
                Vector3f p1 = posMat.transformPosition(verts[1].worldX(), verts[1].worldY(), verts[1].worldZ(), new Vector3f());
                Vector3f p2 = posMat.transformPosition(verts[2].worldX(), verts[2].worldY(), verts[2].worldZ(), new Vector3f());
                Vector3f p3 = posMat.transformPosition(verts[3].worldX(), verts[3].worldY(), verts[3].worldZ(), new Vector3f());

                tmpNormal.set(poly.normal()).mul(normMat);
                Direction dir = Direction.getApproximateNearest(tmpNormal.x, tmpNormal.y, tmpNormal.z);

                long uv0 = UVPair.pack(sprite.getU(verts[0].u()), sprite.getV(verts[0].v()));
                long uv1 = UVPair.pack(sprite.getU(verts[1].u()), sprite.getV(verts[1].v()));
                long uv2 = UVPair.pack(sprite.getU(verts[2].u()), sprite.getV(verts[2].v()));
                long uv3 = UVPair.pack(sprite.getU(verts[3].u()), sprite.getV(verts[3].v()));

                quads.add(new BakedQuad(p0, p1, p2, p3, uv0, uv1, uv2, uv3, dir, matInfo));
            }
        });
    }

    private static BlockStateModel finalizeModel(List<BakedQuad> quads, TextureAtlasSprite particleSprite) {
        int flags = 0;
        for (int i = 0; i < quads.size(); i++) {
            flags |= quads.get(i).materialInfo().flags();
        }
        return new SingleVariant(new SimpleBakedPart(List.copyOf(quads), new Material.Baked(particleSprite, false), flags));
    }

    private static final class SimpleBakedPart implements BlockStateModelPart {

        private final List<BakedQuad> quads;
        private final Material.@Nullable Baked particleMaterial;
        private final int materialFlags;

        SimpleBakedPart(List<BakedQuad> quads, Material.@Nullable Baked particleMaterial, int materialFlags) {
            this.quads = quads;
            this.particleMaterial = particleMaterial;
            this.materialFlags = materialFlags;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? this.quads : Collections.emptyList();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public Material.Baked particleMaterial() {
            return this.particleMaterial;
        }

        @Override
        public int materialFlags() {
            return this.materialFlags;
        }
    }
}
