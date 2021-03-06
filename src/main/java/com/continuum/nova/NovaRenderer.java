package com.continuum.nova;

import com.continuum.nova.chunks.ChunkBuilder;
import com.continuum.nova.chunks.ChunkUpdateListener;
import com.continuum.nova.chunks.IGeometryFilter;
import com.continuum.nova.gui.NovaDraw;
import com.continuum.nova.interfaces.INovaDynamicTexture;
import com.continuum.nova.interfaces.INovaEntityRenderer;
import com.continuum.nova.interfaces.INovaTextureAtlasSprite;
import com.continuum.nova.interfaces.INovaTextureMap;
import com.continuum.nova.system.NovaNative;
import com.continuum.nova.system.NovaNative.window_size;
import com.continuum.nova.utils.Profiler;
import com.continuum.nova.utils.Utils;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.org.apache.xpath.internal.operations.Bool;
import glm.Glm;
import glm.vec._2.Vec2;
import glm.vec._3.i.Vec3i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.entity.Entity;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.LoaderExceptionModCrash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.continuum.nova.NovaConstants.*;
import static com.continuum.nova.utils.Utils.getImageData;

public class NovaRenderer implements IResourceManagerReloadListener {

    private static final Logger LOG = LogManager.getLogger(NovaRenderer.class);
    private BlockModelShapes shapes;

    private boolean firstLoad = true;

    private static final List<ResourceLocation> GUI_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();
    private TextureMap guiAtlas = new TextureMap("textures");
    private INovaTextureMap novaGuiAtlas = (INovaTextureMap) guiAtlas;
    private Map<ResourceLocation, TextureAtlasSprite> guiSpriteLocations = new HashMap<>();

    private static final List<ResourceLocation> BLOCK_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();
    private TextureMap blockAtlas = new TextureMap("textures");
    private Map<ResourceLocation, TextureAtlasSprite> blockSpriteLocations = new HashMap<>();

    private static final List<ResourceLocation> FONT_COLOR_TEXTURES_LOCATIONS = new ArrayList<>();
    private TextureMap fontAtlas = new TextureMap("textures");
    private Map<ResourceLocation, TextureAtlasSprite> fontSpriteLocations = new HashMap<>();

    private static final List<ResourceLocation> FREE_TEXTURES = new ArrayList<>();

    private int height;
    private int width;

    private boolean resized;
    private int scalefactor;

    private IResourceManager resourceManager;

    private ChunkUpdateListener chunkUpdateListener;

    private PriorityQueue<ChunkUpdateListener.BlockUpdateRange> chunksToUpdate;
    private Set<ChunkUpdateListener.BlockUpdateRange> updatedChunks = new HashSet<>();
    private World world;

    final private Executor chunkUpdateThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private ChunkBuilder chunkBuilder;
    private HashMap<String, IGeometryFilter> filterMap;
    private NovaNative _native;

    private static NovaRenderer instance;

    public static NovaRenderer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Tried to access NovaRenderer before it was created");
        }
        return instance;
    }

    public static void create() {
        if (instance != null) {
            throw new IllegalStateException("Instance already created");
        }
        instance = new NovaRenderer();
    }

    private NovaRenderer() {
        // I put these in Utils to make this class smaller
        Utils.initBlockTextureLocations(BLOCK_COLOR_TEXTURES_LOCATIONS);
        Utils.initGuiTextureLocations(GUI_COLOR_TEXTURES_LOCATIONS);
        Utils.initFontTextureLocations(FONT_COLOR_TEXTURES_LOCATIONS);
        Utils.initFreeTextures(FREE_TEXTURES);
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager resourceManager) {
        this.resourceManager = resourceManager;

        if (firstLoad) {
            firstLoad = false;
        }

        _native.reset_texture_manager();

        addGuiAtlas(resourceManager);
        addFontAtlas(resourceManager);
        addFreeTextures(resourceManager);
    }

    /**
     * Adds the textures that just hang out without a texture atlas
     *
     * @param resourceManager The IResoruceManager to get the textures from
     */
    private void addFreeTextures(IResourceManager resourceManager) {
        for (ResourceLocation loc : FREE_TEXTURES) {
            try {
                IResource texture = resourceManager.getResource(loc);
                BufferedInputStream in = new BufferedInputStream(texture.getInputStream());
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    loadTexture(loc, image);
                } else {
                    LOG.error("Free texture " + loc + " has no data!");
                }
            } catch (IOException e) {
                LOG.error("Could not load free texture " + loc, e);
            }
        }
    }

    private void addGuiAtlas(@Nonnull IResourceManager resourceManager) {
        novaGuiAtlas.createWhiteTexture(WHITE_TEXTURE_GUI_LOCATION);
        addAtlas(resourceManager, guiAtlas, GUI_COLOR_TEXTURES_LOCATIONS, guiSpriteLocations, GUI_ATLAS_NAME);
        LOG.debug("Created GUI atlas");
    }

    private void addFontAtlas(@Nonnull IResourceManager resourceManager) {
        addAtlas(resourceManager, fontAtlas, FONT_COLOR_TEXTURES_LOCATIONS, fontSpriteLocations, FONT_ATLAS_NAME);
        LOG.debug("Created font atlas");
    }

    public void addTerrainAtlas(@Nonnull TextureMap blockColorMap) {
        // Copy over the atlas
        NovaNative.mc_atlas_texture blockColorTexture = getFullImage(((INovaTextureMap) blockColorMap).getWidth(), ((INovaTextureMap) blockColorMap).getHeight(), ((INovaTextureMap) blockColorMap).getMapUploadedSprites().values());
        blockColorTexture.name = BLOCK_COLOR_ATLAS_NAME;
        _native.add_texture(blockColorTexture);

        // Copy over all the icon locations
        for (String spriteName : ((INovaTextureMap) blockColorMap).getMapUploadedSprites().keySet()) {
            TextureAtlasSprite sprite = blockColorMap.getAtlasSprite(spriteName);
            NovaNative.mc_texture_atlas_location location = new NovaNative.mc_texture_atlas_location(
                    sprite.getIconName(),
                    sprite.getMinU(),
                    sprite.getMinV(),
                    sprite.getMaxU(),
                    sprite.getMaxV()
            );

            _native.add_texture_location(location);
        }
    }

    private void addAtlas(@Nonnull IResourceManager resourceManager, TextureMap atlas, List<ResourceLocation> resources,
                          Map<ResourceLocation, TextureAtlasSprite> spriteLocations, String textureName) {
        atlas.loadSprites(resourceManager, textureMapIn -> resources.forEach(location -> {
            TextureAtlasSprite textureAtlasSprite = textureMapIn.registerSprite(location);
            spriteLocations.put(location, textureAtlasSprite);
        }));

        Optional<TextureAtlasSprite> whiteImage = ((INovaTextureMap) atlas).getWhiteImage();
        whiteImage.ifPresent(image -> spriteLocations.put(((INovaTextureAtlasSprite) image).getLocation(), image));

        NovaNative.mc_atlas_texture atlasTexture = getFullImage(((INovaTextureMap) atlas).getWidth(), ((INovaTextureMap) atlas).getHeight(), spriteLocations.values());
        atlasTexture.setName(textureName);

        LOG.info("Adding atlas texture {}", atlasTexture);
        _native.add_texture(atlasTexture);

        for (TextureAtlasSprite sprite : spriteLocations.values()) {
            NovaNative.mc_texture_atlas_location location = new NovaNative.mc_texture_atlas_location(
                    sprite.getIconName(),
                    sprite.getMinU(),
                    sprite.getMinV(),
                    sprite.getMaxU(),
                    sprite.getMaxV()
            );

            _native.add_texture_location(location);
        }
    }

    private NovaNative.mc_atlas_texture getFullImage(int atlasWidth, int atlasHeight, Collection<TextureAtlasSprite> sprites) {
        byte[] imageData = new byte[atlasWidth * atlasHeight * 4];

        for (TextureAtlasSprite sprite : sprites) {
            int startY = sprite.getOriginY() * atlasWidth * 4;
            int startPos = sprite.getOriginX() * 4 + startY;

            if (sprite.getFrameCount() > 0) {
                int[] data = sprite.getFrameTextureData(0)[0];
                for (int y = 0; y < sprite.getIconHeight(); y++) {
                    for (int x = 0; x < sprite.getIconWidth(); x++) {
                        // Reverse the order of the color channels
                        int pixel = data[y * sprite.getIconWidth() + x];

                        byte red = (byte) (pixel & 0xFF);
                        byte green = (byte) ((pixel >> 8) & 0xFF);
                        byte blue = (byte) ((pixel >> 16) & 0xFF);
                        byte alpha = (byte) ((pixel >> 24) & 0xFF);

                        int imageDataBasePos = startPos + x * 4 + y * atlasWidth * 4;
                        imageData[imageDataBasePos] = blue;
                        imageData[imageDataBasePos + 1] = green;
                        imageData[imageDataBasePos + 2] = red;
                        imageData[imageDataBasePos + 3] = alpha;
                    }
                }
            }
        }

        return new NovaNative.mc_atlas_texture(
                atlasWidth,
                atlasHeight,
                4,
                imageData
        );
    }

    public void preInit() {
        System.getProperties().setProperty("jna.library.path", System.getProperty("java.library.path"));
        System.getProperties().setProperty("jna.dump_memory", "false");
        String pid = ManagementFactory.getRuntimeMXBean().getName();
        LOG.info("PID: " + pid + " TID: " + Thread.currentThread().getId());
        try {
            installNative();
        } catch (IOException e) {
            throw new LoaderExceptionModCrash("Nova renderer failed to load native library", e);
        }
        _native.initialize();
        LOG.info("Native code initialized");
        updateWindowSize();

        // Moved here so that it's initialized after the native code is loaded
        chunksToUpdate = new PriorityQueue<>((range1, range2) -> {
            Vec3i range1Center = new Vec3i();
            Vec3i range2Center = new Vec3i();

            Glm.add(range1Center, range1.min, new Vec3i(8, 128, 8));
            Glm.add(range2Center, range2.min, new Vec3i(8, 128, 8));

            Entity player = Minecraft.getMinecraft().player;
            Vec2 playerPos = new Vec2(player.posX, player.posZ);
            float range1DistToPlayer = new Vec2().distance(new Vec2(range1Center.x, range1Center.z), playerPos);
            float range2DistToPlayer = new Vec2().distance(new Vec2(range2Center.x, range2Center.z), playerPos);

            return Float.compare(range1DistToPlayer, range2DistToPlayer);
        });
        chunkUpdateListener = new ChunkUpdateListener(chunksToUpdate);
    }

    private void installNative() throws IOException {
        if((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            LOG.info("Nova is very likely running in a development environment, trying to load native from run directory...");
            try {
                if (Platform.isWindows()) {
                    _native = (NovaNative) Native.loadLibrary("./nova-renderer.dll", NovaNative.class);
                } else {
                    _native = (NovaNative) Native.loadLibrary("./libnova-renderer.so", NovaNative.class);
                }
                LOG.info("Succeeded in loading nova from run directory.");
                return;
            } catch (Exception e) {
                LOG.warn("Failed to load nova from run directory", e);
            }
        }

        File novaConfigDir = new File("config/nova/");
        if(!novaConfigDir.exists() || !novaConfigDir.isDirectory()) {
            if(!novaConfigDir.mkdirs()) {
                throw new IOException("Failed to create directory " + novaConfigDir.getAbsolutePath());
            }
        }

        File toLoad;
        if(Platform.isWindows()) {
            if(Platform.is64Bit()) {
                toLoad = Native.extractFromResourcePath("/nova-renderer-x64.dll");
            } else {
                toLoad = Native.extractFromResourcePath("/nova-renderer-x32.dll");
            }
        } else {
            if(Platform.is64Bit()) {
                toLoad = Native.extractFromResourcePath("/libnova-renderer-x64.so");
            } else {
                toLoad = Native.extractFromResourcePath("/libnova-renderer-x32.so");
            }
        }

        _native = (NovaNative) Native.loadLibrary(toLoad.getAbsolutePath(), NovaNative.class);
    }

    private void updateWindowSize() {
        window_size size = _native.get_window_size();
        int oldHeight = height;
        int oldWidth = width;
        if (oldHeight != size.height || oldWidth != size.width) {
            resized = true;
        } else {
            resized = false;
        }
        height = size.height;
        width = size.width;

    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public boolean wasResized() {
        return resized;
    }

    public void updateCameraAndRender(float renderPartialTicks, Minecraft mc) {
        if (_native.should_close()) {
            mc.shutdown();
        }

        EntityRenderer entityRenderer = Minecraft.getMinecraft().entityRenderer;

        boolean shouldUpdateLightmap = ((INovaEntityRenderer) entityRenderer).isLightmapUpdateNeeded();
        ((INovaEntityRenderer) entityRenderer).updateLightmapNOVA(renderPartialTicks);
        if (shouldUpdateLightmap) {
            sendLightmapTexture(((INovaEntityRenderer) entityRenderer).getLightmapTexture());
        }


        Profiler.start("render_gui");
        if (mc.currentScreen != null) {

            NovaDraw.novaDrawScreen(mc.currentScreen, renderPartialTicks, filterMap);

        }
        Profiler.end("render_gui");

        Profiler.start("update_chunks");
        int numChunksUpdated = 0;
        while (!chunksToUpdate.isEmpty()) {
            ChunkUpdateListener.BlockUpdateRange range = chunksToUpdate.remove();
            // chunkBuilder.createMeshesForChunk(range);
            chunkUpdateThreadPool.execute(() -> chunkBuilder.createMeshesForChunk(range));
            updatedChunks.add(range);
            numChunksUpdated++;
            if (numChunksUpdated > 10) {
                break;
            }
        }
        Profiler.end("update_chunks");

        Profiler.start("update_player");
        EntityPlayerSP viewEntity = mc.player;
        if (viewEntity != null) {
            float pitch = viewEntity.rotationPitch;
            float yaw = viewEntity.rotationYaw;
            double x = viewEntity.posX;
            double y = viewEntity.posY + viewEntity.getEyeHeight();
            double z = viewEntity.posZ;
            _native.set_player_camera_transform(x, y, z, yaw, pitch);
        }
        Profiler.end("update_player");

        Profiler.start("execute_frame");
        _native.execute_frame();
        Profiler.end("execute_frame");

        Profiler.start("update_window");
        updateWindowSize();
        Profiler.end("update_window");
        int scalefactor = new ScaledResolution(mc).getScaleFactor() * 2;
        if (scalefactor != this.scalefactor) {
            _native.set_float_setting("scalefactor", scalefactor);
            this.scalefactor = scalefactor;
        }

        printProfilerData();
    }

    private void sendLightmapTexture(DynamicTexture lightmapTexture) {
        int[] data = lightmapTexture.getTextureData();
        _native.send_lightmap_texture(data, data.length, ((INovaDynamicTexture) lightmapTexture).getWidth(), ((INovaDynamicTexture) lightmapTexture).getHeight());
    }

    private void printProfilerData() {
        Profiler.logData();
    }

    public void setWorld(World world) {
        if (world != null) {
            world.addEventListener(chunkUpdateListener);
            this.world = world;
            chunksToUpdate.clear();

            if (chunkBuilder != null) {
                chunkBuilder.setWorld(world);
            }
        }
    }

    /**
     * Loads the specified texture, adding it to Minecraft as a texture outside of an atlas
     *
     * @param location The location of the texture
     * @param image    The texture itself
     */
    public void loadTexture(ResourceLocation location, BufferedImage image) {
        if (resourceManager == null) {
            LOG.error("Trying to load texture " + location + " but there's no resource manager");
            return;
        }

        byte[] imageData = getImageData(image);

        NovaNative.mc_atlas_texture tex = new NovaNative.mc_atlas_texture(image.getWidth(), image.getHeight(), 4, imageData);
        tex.setName(location.toString());
        _native.add_texture(tex);

        NovaNative.mc_texture_atlas_location loc = new NovaNative.mc_texture_atlas_location(location.toString(), 0, 0, 1, 1);
        _native.add_texture_location(loc);
    }

    public static String atlasTextureOfSprite(ResourceLocation texture) {
        ResourceLocation strippedLocation = new ResourceLocation(texture.getResourceDomain(), texture.getResourcePath().replace(".png", "").replace("textures/", ""));

        if (BLOCK_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation)) {
            return BLOCK_COLOR_ATLAS_NAME;
        } else if (GUI_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation) || texture == WHITE_TEXTURE_GUI_LOCATION) {
            return GUI_ATLAS_NAME;
        } else if (FONT_COLOR_TEXTURES_LOCATIONS.contains(strippedLocation)) {
            return FONT_ATLAS_NAME;
        }

        return texture.toString();
    }

    public void loadShaderpack(String shaderpackName, BlockColors blockColors) {
        Profiler.start("load_shaderpack");
        _native.set_string_setting("loadedShaderpack", shaderpackName);

        String filters = _native.get_materials_and_filters();
        String[] filtersSplit = filters.split("\n");
        Profiler.end("load_shaderpack");

        if (filtersSplit.length < 2 || filtersSplit.length % 2 != 0) {
            throw new IllegalStateException("Must have a POT number of filters and shader names");
        }

        Profiler.start("build_filters");
        LOG.debug("Filters: '{}'", String.join(", ", filtersSplit));
        LOG.debug("Received {} shaders with filters", filtersSplit.length);

        filterMap = new HashMap<>();
        for (int i = 0; i < filtersSplit.length; i += 2) {
            String filterName = filtersSplit[i];
            IGeometryFilter filter = IGeometryFilter.parseFilterString(filtersSplit[i + 1]);
            filterMap.put(filterName, filter);
        }
        Profiler.end("build_filters");

        Profiler.start("new_chunk_builder");
        chunkBuilder = new ChunkBuilder(filterMap, world, blockColors);

        chunksToUpdate.addAll(updatedChunks);
        updatedChunks.clear();
        Profiler.end("new_chunk_builder");
    }

    public NovaNative getNative() {
        return _native;
    }

    public Logger getLog() {
        return LOG;
    }
}


