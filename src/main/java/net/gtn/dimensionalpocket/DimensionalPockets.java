package net.gtn.dimensionalpocket;

import java.util.Map;
import com.mojang.serialization.Codec;

import net.gtn.dimensionalpocket.common.CommonProxy;
import net.gtn.dimensionalpocket.common.ModBlockEntities;
import net.gtn.dimensionalpocket.common.ModBlocks;
import net.gtn.dimensionalpocket.common.ModItems;
import net.gtn.dimensionalpocket.common.ModTabs;
import net.gtn.dimensionalpocket.common.core.ChunkGeneratorPocket;
import net.gtn.dimensionalpocket.common.core.ChunkLoaderHandler;
import net.gtn.dimensionalpocket.common.core.DPConfig;
import net.gtn.dimensionalpocket.common.core.pocket.PocketRegistry;
import net.gtn.dimensionalpocket.common.lib.Reference;
import net.gtn.dimensionalpocket.util.ModLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 1.20.1 主类。
 *
 * <p>1.7.10 对应物 {@code DimensionalPockets}：{@code @Instance} → {@link #INSTANCE}，
 * {@code @SidedProxy} → {@link #PROXY}（客户端侧由 S5 的 {@code ClientProxy} 继承），
 * {@code @Controller + ConfigHandler} → {@link DPConfig}（{@code ForgeConfigSpec}），
 * {@code NetworkDispatcher} / {@code NetworkRegistry.registerGuiHandler} / DPAnalytics / VersionChecker
 * 按移植决策整体删除（本 mod 不需要任何网络包，指南书是纯客户端 Screen）。
 */
@Mod(DimensionalPockets.MODID)
@Mod.EventBusSubscriber(modid = DimensionalPockets.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DimensionalPockets {

    /** 1.7.10: {@code Reference.MOD_ID}，值 {@code dimensionalpockets}。 */
    public static final String MODID = Reference.MOD_ID;

    /** 1.7.10: {@code @Instance(Reference.MOD_ID) public static DimensionalPockets instance}。 */
    public static DimensionalPockets INSTANCE;

    /**
     * 服务端代理，同时是 {@code EntityJoinLevelEvent}（上游 {@code EntityJoinWorldEvent}）的宿主。
     *
     * <p>1.7.10 是 {@code MinecraftForge.EVENT_BUS.register(proxy)}；1.20.1 无 {@code @SidedProxy}，
     * 因此这里显式建一个实例挂到 Forge 总线上。
     */
    public static CommonProxy PROXY;

    /**
     * 1.20.1 用 Codec 取代 1.7.10 的 {@code IChunkProvider} 工厂：数据包
     * {@code data/dimensionalpockets/dimension/pocket.json} 里的 {@code generator.type}
     * 正是这里注册的 {@code dimensionalpockets:pocket}。不注册它，维度 JSON 解析时找不到
     * generator，世界直接加载失败。
     */
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    public DimensionalPockets(FMLJavaModLoadingContext context) {
        INSTANCE = this;

        IEventBus modBus = context.getModEventBus();

        // 注册顺序硬约束：
        //   1. BLOCKS 必须先挂，BLOCK_ENTITIES 的 Builder 里才能 ModBlocks.X.get()；
        //   2. ModBlocks.ITEMS（BlockItem）必须在 BLOCKS 之后。
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModTabs.TABS.register(modBus);
        CHUNK_GENERATORS.register("pocket", () -> ChunkGeneratorPocket.CODEC);
        CHUNK_GENERATORS.register(modBus);

        context.registerConfig(ModConfig.Type.COMMON, DPConfig.SPEC);

        modBus.addListener(this::commonSetup);

        PROXY = new CommonProxy();
        MinecraftForge.EVENT_BUS.register(PROXY);

        ModLog.step("mod", "construct", "mod={} ver={}", MODID, Reference.VERSION);
    }

    /**
     * 1.7.10: {@code preInit} / {@code init} / {@code postInit} 里与世界无关的部分。
     *
     * <p>{@code FMLCommonSetupEvent} 是并行触发的，碰注册表 / ModList / 全局单例的代码必须包进
     * {@code event.enqueueWork}。
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModLog.banner(Map.of());
            ModLog.step("mod", "common_setup", "done mod={} ver={}", MODID, ModLog.modVersion(MODID));
        });
    }

    // ------------------------------------------------------------------
    // Forge 总线：服务器生命周期
    // 1.7.10: @EventHandler FMLServerAboutToStart / FMLServerStarting / FMLServerStopping
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // 必须在方块实体从 NBT 里读出来之前加载，否则 getOrCreatePocket 会凭空造出一批空口袋。
        ModLog.step("mod", "server_about_to_start", "loading_pocket_registry");
        PocketRegistry.loadData();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PocketRegistry.initChunkLoading();
        PocketRegistry.validatePocketConnectors();
        ModLog.step("mod", "server_started", "chunk_loading_and_connectors_initialised");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PocketRegistry.saveData();
        ChunkLoaderHandler.clearTicketMap();
        ModLog.step("mod", "server_stopping", "pocket_registry_saved tickets_cleared");
    }
}
