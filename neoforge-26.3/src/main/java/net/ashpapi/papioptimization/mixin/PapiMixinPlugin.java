package net.ashpapi.papioptimization.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PapiMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/MixinPlugin");

    private static final Set<String> APPLIED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SKIPPED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final CopyOnWriteArrayList<String> DISABLE_REASONS = new CopyOnWriteArrayList<>();

    private String mixinPrefix = "";
    private final Set<String> disabledMixins = new HashSet<>();
    private boolean disableLightingPackage;

    public static int getActiveMixinCount() {
        return APPLIED_MIXINS.size();
    }

    public static int getDisabledMixinCount() {
        return SKIPPED_MIXINS.size();
    }

    public static List<String> getDisableReasons() {
        return Collections.unmodifiableList(DISABLE_REASONS);
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPrefix = (mixinPackage != null && !mixinPackage.isEmpty()) ? mixinPackage + "." : "";
        this.disabledMixins.clear();
        this.disableLightingPackage = false;
        APPLIED_MIXINS.clear();
        SKIPPED_MIXINS.clear();
        DISABLE_REASONS.clear();

        PapiConfig.ensureLoaded();

        Set<String> reasons = new LinkedHashSet<>();

        LoadingModList modList;
        try {
            FMLLoader loader = FMLLoader.getCurrentOrNull();
            modList = loader != null ? loader.getLoadingModList() : null;
        } catch (Throwable ignored) {
            modList = null;
        }

        boolean hasSodium = modList != null && hasAny(modList, "sodium", "embeddium", "rubidium");
        boolean hasImmediatelyFast = modList != null && hasAny(modList, "immediatelyfast");
        boolean hasShaders = modList != null && hasAny(modList, "iris", "oculus");
        boolean hasVitrail = modList != null && hasAny(modList, "vitrail", "vulkanmod");
        boolean hasEbe = modList != null && hasAny(modList, "enhancedblockentities");
        boolean hasLighting = modList != null && hasAny(modList, "scalablelux", "starlight", "moonrise");
        boolean hasC2me = modList != null && hasAny(modList, "c2me", "c2me_base", "c2me-base");
        boolean hasSoundPhysics = modList != null && hasAny(modList, "sound_physics_remastered");
        boolean hasSkinLayers = modList != null && hasAny(modList, "skinlayers3d");
        boolean hasPapirius = modList != null && hasAny(modList, "papirius", "voxy");

        boolean cfgEbe = PapiConfig.ebe();
        boolean cfgEbeSodium = PapiConfig.ebeSodiumAdapter();
        boolean cfgEbeVitrail = PapiConfig.ebeVitrailAdapter();

        boolean allowVanillaEbe = cfgEbe && !hasSodium && !hasVitrail && !hasEbe && !hasShaders;
        boolean allowSodiumEbe = cfgEbe && cfgEbeSodium && hasSodium && !hasVitrail && !hasEbe && !hasShaders;
        boolean allowVitrailEbe = cfgEbe && cfgEbeVitrail && hasVitrail && !hasEbe && !hasShaders;

        if (!allowVanillaEbe) {
            this.disabledMixins.add("render.SectionCompilerEbeMixin");
            this.disabledMixins.add("render.SectionCompilerResultsEbeMixin");
            this.disabledMixins.add("render.CompiledSectionMeshEbeMixin");
            this.disabledMixins.add("render.RenderSectionEbeCommitMixin");
        }
        if (!allowSodiumEbe) {
            this.disabledMixins.add("render.SodiumEbeMixin");
        }
        if (!allowVitrailEbe) {
            this.disabledMixins.add("render.VitrailEbeMixin");
        }
        if (!allowSodiumEbe && !allowVitrailEbe) {
            this.disabledMixins.add("render.SodiumEbeCommitMixin");
        }
        if (!allowVanillaEbe && !allowSodiumEbe && !allowVitrailEbe) {
            this.disabledMixins.add("client.SpriteSourceListEbeMixin");
            this.disabledMixins.add("client.BlockColorsEbeMixin");
            this.disabledMixins.add("render.BlockEntityRenderDispatcherEbeMixin");
        }
        if (!hasVitrail) {
            this.disabledMixins.add("render.VitrailShadowPassMixin");
        }

        if (hasSodium) {
            reasons.add("mod:sodium");
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("biome.ClientLevelBiomeBlendMixin");
            this.disabledMixins.add("biome.FastBlockTintCacheMixin");
            this.disabledMixins.add("particle.SingleQuadParticleMixin");
        }
        if (hasImmediatelyFast) {
            reasons.add("mod:immediatelyfast");
            disableGuiBatchingGroup();
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("client.ByteBufferBuilderMixin");
            this.disabledMixins.add("client.FontSplitterFastMixin");
            this.disabledMixins.add("client.UnihexProviderFastMixin");
            this.disabledMixins.add("map.MapRendererMixin");
            this.disabledMixins.add("map.MapInstanceTextureMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
        }
        if (hasShaders) {
            reasons.add("mod:shaders");
            this.disabledMixins.add("client.GuiRenderStateMixin");
            this.disabledMixins.add("client.GuiRenderStateNodeMixin");
            this.disabledMixins.add("client.GuiRendererBatchMixin");
            this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.LivingEntityRendererLodMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
            this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
            this.disabledMixins.add("weather.WeatherEffectRendererMixin");
            this.disabledMixins.add("client.CameraMixin");
        }
        if (hasVitrail) {
            reasons.add("mod:vitrail");
            this.disabledMixins.add("client.VertexConsumer3DFastMixin");
            this.disabledMixins.add("client.VertexConsumerFastMixin");
            this.disabledMixins.add("client.ByteBufferBuilderMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
            this.disabledMixins.add("render.ModelMixin");
            this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
            this.disabledMixins.add("render.LivingEntityRendererLodMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
            this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
            this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
            this.disabledMixins.add("render.CloudRendererMixin");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
            this.disabledMixins.add("weather.ClientLevelWeatherMixin");
            this.disabledMixins.add("weather.WeatherEffectRendererMixin");
            this.disabledMixins.add("client.CameraMixin");
        }
        if (hasPapirius) {
            reasons.add("mod:papirius/voxy");
            this.disabledMixins.add("render.FogRendererMixin");
            this.disabledMixins.add("render.SkyRendererMixin");
        }
        if (hasSkinLayers) {
            reasons.add("mod:skinlayers3d");
            this.disabledMixins.add("render.LivingEntityRendererLodMixin");
            this.disabledMixins.add("render.ModelPartMixin");
            this.disabledMixins.add("render.ModelPartCubeMixin");
        }
        if (hasLighting) {
            reasons.add("mod:lighting");
            this.disableLightingPackage = true;
        }
        if (hasC2me) {
            reasons.add("mod:c2me");
            disableC2meGroup();
        }
        if (hasSoundPhysics) {
            reasons.add("mod:sound_physics_remastered");
            this.disabledMixins.add("sound.SoundEngineMixin");
        }

        List<String> redundant = new ArrayList<>();
        if (modList != null) {
            if (hasAny(modList, "lithium", "radium", "canary")) {
                redundant.add("lithium");
                reasons.add("mod:lithium");
                disableLithiumGroup();
            }
            if (hasAny(modList, "alternate-current", "alternate_current")) {
                redundant.add("alternate-current");
                reasons.add("mod:alternate-current");
                this.disabledMixins.add("redstone.DefaultRedstoneWireEvaluatorMixin");
            }
            if (hasAny(modList, "ferritecore")) {
                redundant.add("ferritecore");
                reasons.add("mod:ferritecore");
                disableFerriteCoreGroup();
            }
            if (hasAny(modList, "lazydfu")) {
                redundant.add("lazydfu");
                reasons.add("mod:lazydfu");
                this.disabledMixins.add("startup.DataFixersMixin");
                this.disabledMixins.add("startup.DataFixerUpperMixin");
            }
            if (hasAny(modList, "modernfix")) {
                redundant.add("modernfix");
                reasons.add("mod:modernfix");
                disableModernFixGroup();
            }
            if (hasAny(modList, "entityculling")) {
                redundant.add("entityculling");
                reasons.add("mod:entityculling");
                disableEntityCullingGroup();
            }
            if (hasAny(modList, "moreculling")) {
                redundant.add("moreculling");
                reasons.add("mod:moreculling");
                disableMoreCullingGroup();
            }
            if (hasEbe) {
                redundant.add("enhancedblockentities");
                reasons.add("mod:enhancedblockentities");
                disableEbeGroup();
            }
            if (hasAny(modList, "krypton", "krypton_fnp", "kryptonfoxified", "krypton_foxified", "pluto")) {
                redundant.add("krypton");
                reasons.add("mod:krypton");
                disableKryptonGroup();
            }
            if (hasAny(modList, "fastsuite", "fastbench", "fastworkbench")) {
                redundant.add("fastsuite/fastbench");
                reasons.add("mod:fastsuite/fastbench");
                this.disabledMixins.add("crafting.RecipeManagerFastMixin");
                this.disabledMixins.add("crafting.RecipeMapMixin");
                this.disabledMixins.add("crafting.AbstractCraftingMenuMixin");
                this.disabledMixins.add("crafting.CraftingMenuCacheMixin");
                this.disabledMixins.add("redstone.CrafterBlockFastMixin");
            }
            if (hasAny(modList, "fastfurnace")) {
                redundant.add("fastfurnace");
                reasons.add("mod:fastfurnace");
                this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
            }
            if (hasAny(modList, "clumps")) {
                redundant.add("clumps");
                reasons.add("mod:clumps");
                this.disabledMixins.add("entity.ExperienceOrbFastMixin");
            }
            if (hasAny(modList, "servercore")) {
                redundant.add("servercore");
                reasons.add("mod:servercore");
                disableServerCoreGroup();
            }
            if (hasAny(modList, "noisium", "noisiumed")) {
                redundant.add("noisium");
                reasons.add("mod:noisium");
                disableNoisiumGroup();
            }
            if (hasAny(modList, "badoptimizations")) {
                redundant.add("badoptimizations");
                reasons.add("mod:badoptimizations");
                disableBadOptimizationsGroup();
            }
            if (hasAny(modList, "fastmap")) {
                redundant.add("fastmap");
                reasons.add("mod:fastmap");
                this.disabledMixins.add("map.MapItemSavedDataMixin");
                this.disabledMixins.add("map.MapItemUpdateMixin");
                this.disabledMixins.add("map.MapRendererMixin");
                this.disabledMixins.add("map.MapInstanceTextureMixin");
                this.disabledMixins.add("map.ServerEntityMapTrackingMixin");
            }
        }

        if (!PapiConfig.entityCulling()) {
            reasons.add("config:entityCulling");
            disableEntityCullingGroup();
        }
        if (!PapiConfig.particleCulling()) {
            reasons.add("config:particleCulling");
            this.disabledMixins.add("particle.ParticleCullingMixin");
            this.disabledMixins.add("particle.QuadParticleGroupCullingMixin");
            this.disabledMixins.add("particle.CampfireSmokeParticleMixin");
        }
        if (!cfgEbe) {
            reasons.add("config:ebe");
            disableEbeGroup();
        } else {
            if (!cfgEbeSodium) {
                reasons.add("config:ebeSodiumAdapter");
            }
            if (!cfgEbeVitrail) {
                reasons.add("config:ebeVitrailAdapter");
            }
        }
        if (!PapiConfig.moreCulling()) {
            reasons.add("config:moreCulling");
            disableMoreCullingGroup();
        }
        if (!PapiConfig.guiBatching()) {
            reasons.add("config:guiBatching");
            disableGuiBatchingGroup();
        }
        if (!PapiConfig.badOptimizations()) {
            reasons.add("config:badOptimizations");
            disableBadOptimizationsGroup();
        }
        if (!PapiConfig.alternateCurrent()) {
            reasons.add("config:alternateCurrent");
            this.disabledMixins.add("redstone.DefaultRedstoneWireEvaluatorMixin");
        }
        if (!PapiConfig.lithiumDeep()) {
            reasons.add("config:lithiumDeep");
            disableLithiumGroup();
        }
        if (!PapiConfig.noisiumC2me()) {
            reasons.add("config:noisiumC2me");
            disableNoisiumGroup();
            disableC2meGroup();
        }
        if (!PapiConfig.fastFurnace()) {
            reasons.add("config:fastFurnace");
            this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
        }
        if (!PapiConfig.clumps()) {
            reasons.add("config:clumps");
            this.disabledMixins.add("entity.ExperienceOrbFastMixin");
        }
        if (!PapiConfig.serverCoreMspt()) {
            reasons.add("config:serverCoreMspt");
            disableServerCoreGroup();
        }
        if (!PapiConfig.ferriteCore()) {
            reasons.add("config:ferriteCore");
            disableFerriteCoreGroup();
        }
        if (!PapiConfig.modernFix()) {
            reasons.add("config:modernFix");
            disableModernFixGroup();
        }
        if (!PapiConfig.krypton()) {
            reasons.add("config:krypton");
            disableKryptonGroup();
        }

        DISABLE_REASONS.addAll(reasons);

        if (!redundant.isEmpty()) {
            LOGGER.info(
                    "Detected redundant optimization mod(s) {}; Gluon yielded overlapping mixins safely (these mods are unnecessary alongside Gluon).",
                    redundant
            );
        }
    }

    private void disableEntityCullingGroup() {
        this.disabledMixins.add("render.EntityRenderDispatcherMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherMixin");
        this.disabledMixins.add("render.LivingEntityRendererLodMixin");
        this.disabledMixins.add("render.BlockEntityRendererMixin");
    }

    private void disableEbeGroup() {
        this.disabledMixins.add("blockentity.StaticLidBlockEntityMixin");
        this.disabledMixins.add("blockentity.ShulkerBoxSleepMixin");
        this.disabledMixins.add("render.StaticBlockEntityRenderMixin");
        this.disabledMixins.add("client.SpriteSourceListEbeMixin");
        this.disabledMixins.add("client.BlockColorsEbeMixin");
        this.disabledMixins.add("render.SectionCompilerEbeMixin");
        this.disabledMixins.add("render.SectionCompilerResultsEbeMixin");
        this.disabledMixins.add("render.CompiledSectionMeshEbeMixin");
        this.disabledMixins.add("render.RenderSectionEbeCommitMixin");
        this.disabledMixins.add("render.SodiumEbeMixin");
        this.disabledMixins.add("render.SodiumEbeCommitMixin");
        this.disabledMixins.add("render.VitrailEbeMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherEbeMixin");
    }

    private void disableMoreCullingGroup() {
        this.disabledMixins.add("render.LeavesBlockMixin");
        this.disabledMixins.add("render.MangroveRootsBlockMixin");
        this.disabledMixins.add("render.MoreBlockCullingMixin");
        this.disabledMixins.add("render.PowderSnowBlockMixin");
        this.disabledMixins.add("render.CloudRendererMixin");
        this.disabledMixins.add("weather.WeatherEffectRendererMixin");
        this.disabledMixins.add("render.ItemFrameRendererMixin");
        this.disabledMixins.add("render.PaintingRendererMixin");
        this.disabledMixins.add("render.AbstractSignRendererMixin");
        this.disabledMixins.add("render.BeaconRendererMixin");
        this.disabledMixins.add("render.BlockEntityRendererMixin");
    }

    private void disableGuiBatchingGroup() {
        this.disabledMixins.add("client.FontMixin");
        this.disabledMixins.add("client.GuiRenderStateMixin");
        this.disabledMixins.add("client.GuiRenderStateNodeMixin");
        this.disabledMixins.add("client.GuiRendererBatchMixin");
        this.disabledMixins.add("render.RenderTypeFeatureRendererGroupMixin");
        this.disabledMixins.add("render.RenderTypeFeatureRendererStateMixin");
        this.disabledMixins.add("render.TextFeatureRendererBatchMixin");
    }

    private void disableBadOptimizationsGroup() {
        this.disabledMixins.add("render.LightmapRenderStateExtractorMixin");
        this.disabledMixins.add("render.FogRendererMixin");
        this.disabledMixins.add("render.SkyRendererMixin");
        this.disabledMixins.add("render.EntityRenderDispatcherMixin");
        this.disabledMixins.add("render.BlockEntityRenderDispatcherMixin");
        this.disabledMixins.add("render.EntityRendererShadowFastMixin");
        this.disabledMixins.add("client.DebugRendererMixin");
        this.disabledMixins.add("client.ToastManagerMixin");
        this.disabledMixins.add("client.ClientTelemetryStubMixin");
        this.disabledMixins.add("particle.ParticleEngineMixin");
        this.disabledMixins.add("worldgen.EnvironmentAttributeProbeMixin");
    }

    private void disableLithiumGroup() {
        this.disabledMixins.add("block.ComposterZeroAllocMixin");
        this.disabledMixins.add("block.LeavesDecayOptimizationMixin");
        this.disabledMixins.add("blockentity.FastHopperMixin");
        this.disabledMixins.add("blockentity.FastBrewingStandMixin");
        this.disabledMixins.add("blockentity.FastFurnaceSleepMixin");
        this.disabledMixins.add("biome.BiomeManagerMixin");
        this.disabledMixins.add("chunk.ServerChunkCacheMixin");
        this.disabledMixins.add("chunk.PalettedContainerCountMixin");
        this.disabledMixins.add("chunk.SimpleBitStorageMixin");
        this.disabledMixins.add("chunk.ZeroBitStorageMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackedEntityMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackingStateMixin");
        this.disabledMixins.add("entity.GoalFlagMixin");
        this.disabledMixins.add("entity.GoalSelectorFastMixin");
        this.disabledMixins.add("entity.WrappedGoalMixin");
        this.disabledMixins.add("entity.BrainFastMixin");
        this.disabledMixins.add("entity.BehaviorMemoryCacheMixin");
        this.disabledMixins.add("entity.GateBehaviorFastMixin");
        this.disabledMixins.add("entity.EntitySectionStorageMixin");
        this.disabledMixins.add("entity.LevelEntityQueryMixin");
        this.disabledMixins.add("entity.ItemEntityMergeMixin");
        this.disabledMixins.add("entity.LivingEntityEquipmentEpochMixin");
        this.disabledMixins.add("entity.EntityFastFluidMixin");
        this.disabledMixins.add("entity.EntityFluidInteractionMixin");
        this.disabledMixins.add("explosion.ServerExplosionMixin");
        this.disabledMixins.add("math.MthMixin");
        this.disabledMixins.add("pathfinding.BinaryHeapFastMixin");
        this.disabledMixins.add("pathfinding.BlockStateBasePathTypeMixin");
        this.disabledMixins.add("pathfinding.NodeEvaluatorAccessor");
        this.disabledMixins.add("pathfinding.PathFinderMixin");
        this.disabledMixins.add("pathfinding.PathNavigationMixin");
        this.disabledMixins.add("pathfinding.WalkNodeEvaluatorFastMixin");
        this.disabledMixins.add("physics.BlockCollisionsMixin");
        this.disabledMixins.add("physics.CubePointRangeMixin");
        this.disabledMixins.add("physics.EntityCollideMixin");
        this.disabledMixins.add("physics.ShapesCollideMixin");
        this.disabledMixins.add("physics.VoxelShapeCacheMixin");
        this.disabledMixins.add("physics.WorldBorderFastMixin");
        this.disabledMixins.add("poi.PoiManagerMixin");
        this.disabledMixins.add("poi.PoiRecordFastMixin");
        this.disabledMixins.add("poi.PoiSectionFastMixin");
        this.disabledMixins.add("poi.SectionStorageAccessor");
        this.disabledMixins.add("piston.PistonBaseBlockMixin");
        this.disabledMixins.add("piston.PistonMovingBlockEntityMixin");
        this.disabledMixins.add("piston.PistonStructureResolverMixin");
        this.disabledMixins.add("piston.ServerLevelBlockEventsMixin");
        this.disabledMixins.add("redstone.ComparatorBlockOptimizationMixin");
        this.disabledMixins.add("sculk.GameEventDispatcherMixin");
        this.disabledMixins.add("sculk.EuclideanGameEventListenerRegistryMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.FlowingFluidMixin");
        this.disabledMixins.add("worldgen.NaturalSpawnerMixin");
        this.disabledMixins.add("worldgen.ServerLevelRandomTickMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
    }

    private void disableC2meGroup() {
        this.disabledMixins.add("threading.UtilWorkerPriorityMixin");
        this.disabledMixins.add("worldgen.MinecraftServerSpawnMixin");
        this.disabledMixins.add("chunk.ServerChunkCacheMixin");
        this.disabledMixins.add("chunk.ChunkMapAutosaveOptimizerMixin");
        this.disabledMixins.add("lighting.ThreadedLevelLightEngineMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.MaterialRuleContextMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
        this.disabledMixins.add("worldgen.JigsawPlacementMixin");
        this.disabledMixins.add("worldgen.JigsawPlacementPlacerMixin");
        this.disabledMixins.add("worldgen.JigsawReplacementProcessorMixin");
        this.disabledMixins.add("worldgen.SinglePoolElementMixin");
        this.disabledMixins.add("worldgen.StructurePiecesBuilderMixin");
    }

    private void disableNoisiumGroup() {
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.PalettedContainerNoisiumMixin");
        this.disabledMixins.add("worldgen.AquiferNoiseBasedMixin");
        this.disabledMixins.add("worldgen.LevelChunkSectionMixin");
        this.disabledMixins.add("worldgen.MaterialRuleContextMixin");
        this.disabledMixins.add("worldgen.PerlinNoiseFastMixin");
        this.disabledMixins.add("worldgen.SimplexNoiseFastMixin");
        this.disabledMixins.add("worldgen.HeightmapMixin");
        this.disabledMixins.add("biome.BiomeManagerMixin");
    }

    private void disableServerCoreGroup() {
        this.disabledMixins.add("worldgen.LocalMobCapFastMixin");
        this.disabledMixins.add("worldgen.NaturalSpawnerMixin");
        this.disabledMixins.add("entity.MobDespawnFastPathMixin");
        this.disabledMixins.add("entity.MobAiThrottlerMixin");
        this.disabledMixins.add("entity.villager.VillagerLobotomyMixin");
        this.disabledMixins.add("entity.ArmorStandOptimizationMixin");
        this.disabledMixins.add("entity.LivingEntityArmorStandTickMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackedEntityMixin");
        this.disabledMixins.add("chunk.ChunkMapTrackingStateMixin");
        this.disabledMixins.add("chunk.ChunkMapAutosaveOptimizerMixin");
    }

    private void disableFerriteCoreGroup() {
        this.disabledMixins.add("memory.StateHolderRadixMixin");
        this.disabledMixins.add("memory.StateDefinitionRadixMixin");
        this.disabledMixins.add("memory.BlockBehaviourCacheDeduplicationMixin");
        this.disabledMixins.add("memory.BlockStateCacheDeduplicationMixin");
        this.disabledMixins.add("memory.PropertyInternMixin");
        this.disabledMixins.add("memory.WallBlockShapeDeduplicationMixin");
        this.disabledMixins.add("core.PatchedDataComponentMapMixin");
        this.disabledMixins.add("chunk.PalettedContainerCountMixin");
        this.disabledMixins.add("client.QuadCollectionBuilderDeduplicationMixin");
        this.disabledMixins.add("startup.TagEntryCacheMixin");
        this.disabledMixins.add("startup.TagOrElementLocationCacheMixin");
    }

    private void disableModernFixGroup() {
        this.disabledMixins.add("startup.DataFixersMixin");
        this.disabledMixins.add("startup.DataFixerUpperMixin");
        this.disabledMixins.add("startup.FilePackResourcesMixin");
        this.disabledMixins.add("startup.PathPackResourcesMixin");
        this.disabledMixins.add("startup.TagEntryCacheMixin");
        this.disabledMixins.add("startup.TagOrElementLocationCacheMixin");
        this.disabledMixins.add("client.ModelManagerMemoryMixin");
        this.disabledMixins.add("client.QuadCollectionBuilderDeduplicationMixin");
        this.disabledMixins.add("client.SessionSearchTreesMixin");
        this.disabledMixins.add("memory.EntityTickListLeakMixin");
        this.disabledMixins.add("memory.PropertyInternMixin");
        this.disabledMixins.add("crafting.RecipeManagerFastMixin");
        this.disabledMixins.add("crafting.RecipeMapMixin");
        this.disabledMixins.add("worldgen.NoiseBasedChunkGeneratorMixin");
        this.disabledMixins.add("worldgen.MinecraftServerSpawnMixin");
    }

    private void disableKryptonGroup() {
        this.disabledMixins.add("network.CompressionEncoderMixin");
        this.disabledMixins.add("network.CompressionDecoderMixin");
        this.disabledMixins.add("network.ConnectionFlushConsolidationMixin");
        this.disabledMixins.add("network.Varint21LengthFieldPrependerMixin");
        this.disabledMixins.add("network.Varint21FrameDecoderMixin");
        this.disabledMixins.add("network.VarIntMixin");
        this.disabledMixins.add("network.VarLongMixin");
        this.disabledMixins.add("network.Utf8StringMixin");
    }

    private static boolean hasAny(LoadingModList modList, String... modIds) {
        for (String id : modIds) {
            if (modList.getModFileById(id) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String rel = (!this.mixinPrefix.isEmpty() && mixinClassName.startsWith(this.mixinPrefix))
                ? mixinClassName.substring(this.mixinPrefix.length())
                : mixinClassName;
        boolean apply = !(this.disableLightingPackage && rel.startsWith("lighting."))
                && !this.disabledMixins.contains(rel);
        if (apply) {
            APPLIED_MIXINS.add(rel);
            SKIPPED_MIXINS.remove(rel);
        } else {
            SKIPPED_MIXINS.add(rel);
            APPLIED_MIXINS.remove(rel);
        }
        return apply;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
