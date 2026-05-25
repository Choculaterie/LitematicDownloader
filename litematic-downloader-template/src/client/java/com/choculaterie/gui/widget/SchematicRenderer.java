package com.choculaterie.gui.widget;

import com.choculaterie.util.LitematicParser;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

public class SchematicRenderer implements AutoCloseable {

    public static final int MAX_3D_BLOCKS = 20_000;
    private static final int LAYER_COUNT = 3;
    private static final float FOV = 70f;
    private static final float NEAR = 0.05f;
    private static final float FAR = 4096f;
    private static final float MOVE_SPEED = 0.35f;

    private volatile MeshData[] pendingMeshes;
    private volatile ByteBufferBuilder[] pendingAllocators;
    private volatile boolean hasPending = false;
    private volatile boolean buildingMesh = false;

    private final GpuBuffer[] vertexBuffers = new GpuBuffer[LAYER_COUNT];
    private final int[] vertexCounts = new int[LAYER_COUNT];

    private GpuBuffer projectionBuffer;
    private TextureTarget framebuffer;

    private float rotationY = 135f;
    private float rotationX = 30f;
    private float distance = 20f;
    private float fitDistance = 20f;
    private float targetX, targetY, targetZ;
    private int panX, panY;

    private boolean cameraChanged = true;
    private volatile boolean empty = true;

    private long lastFrameNanos = System.nanoTime();
    private static Field keyMappingKeyField;

    public boolean isEmpty() {
        return empty;
    }

    public boolean isBuilding() {
        return buildingMesh;
    }

    public void setBlocks(List<LitematicParser.BlockData> blockData) {
        empty = blockData.isEmpty();
        if (empty) return;

        int count = Math.min(blockData.size(), MAX_3D_BLOCKS);
        int maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i < count; i++) {
            LitematicParser.BlockData bd = blockData.get(i);
            if (bd.x > maxX) maxX = bd.x;
            if (bd.y > maxY) maxY = bd.y;
            if (bd.z > maxZ) maxZ = bd.z;
        }
        targetX = (maxX + 1) * 0.5f;
        targetY = (maxY + 1) * 0.5f;
        targetZ = (maxZ + 1) * 0.5f;
        float maxSide = Math.max(maxX + 1, Math.max(maxY + 1, maxZ + 1));
        distance = maxSide * 1.5f + 5f;
        fitDistance = distance;

        buildingMesh = true;
        final int finalCount = count;
        new Thread(() -> buildMesh(blockData, finalCount), "Schematic-3D-Build").start();
    }

    private void buildMesh(List<LitematicParser.BlockData> blockData, int count) {
        try {
            Minecraft mc = Minecraft.getInstance();
            BlockStateModelSet modelSet = mc.getModelManager().getBlockStateModelSet();
            BlockColors blockColors = mc.getBlockColors();
            RandomSource random = RandomSource.create();

            ByteBufferBuilder[] allocators = new ByteBufferBuilder[LAYER_COUNT];
            BufferBuilder[] builders = new BufferBuilder[LAYER_COUNT];
            boolean[] used = new boolean[LAYER_COUNT];
            for (int i = 0; i < LAYER_COUNT; i++) {
                allocators[i] = new ByteBufferBuilder(Math.max(8192, count * 128));
                builders[i] = new BufferBuilder(allocators[i], VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            }

            List<BlockStateModelPart> parts = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                LitematicParser.BlockData bd = blockData.get(i);
                try {
                    Identifier id = Identifier.tryParse(bd.blockId);
                    if (id == null) continue;
                    var ref = BuiltInRegistries.BLOCK.get(id);
                    if (ref.isEmpty()) continue;
                    Block block = ref.get().value();
                    BlockState state = block.defaultBlockState();
                    if (state.is(Blocks.AIR)) continue;
                    state = applyProperties(state, block, bd.properties);

                    BlockStateModel model = modelSet.get(state);
                    random.setSeed(state.getSeed(BlockPos.ZERO));

                    parts.clear();
                    model.collectParts(random, parts);

                    for (BlockStateModelPart part : parts) {
                        emitQuads(builders, used, blockColors, state, bd.x, bd.y, bd.z, part, null);
                        for (Direction dir : Direction.values()) {
                            emitQuads(builders, used, blockColors, state, bd.x, bd.y, bd.z, part, dir);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            MeshData[] meshes = new MeshData[LAYER_COUNT];
            boolean any = false;
            for (int i = 0; i < LAYER_COUNT; i++) {
                if (!used[i]) continue;
                MeshData mesh = builders[i].build();
                if (mesh != null && mesh.drawState().vertexCount() > 0) {
                    meshes[i] = mesh;
                    any = true;
                } else if (mesh != null) {
                    mesh.close();
                }
            }

            if (any) {
                pendingMeshes = meshes;
                pendingAllocators = allocators;
                hasPending = true;
            } else {
                for (ByteBufferBuilder allocator : allocators) allocator.close();
                empty = true;
            }
        } catch (Exception e) {
            empty = true;
        } finally {
            buildingMesh = false;
        }
    }

    private static BlockState applyProperties(BlockState state, Block block, Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) return state;
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = definition.getProperty(entry.getKey());
            if (property != null) {
                state = withProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    private static void emitQuads(BufferBuilder[] builders, boolean[] used, BlockColors blockColors,
                                   BlockState state, float bx, float by, float bz,
                                   BlockStateModelPart part, Direction dir) {
        List<BakedQuad> quads = part.getQuads(dir);
        if (quads == null || quads.isEmpty()) return;

        for (BakedQuad quad : quads) {
            BakedQuad.MaterialInfo info = quad.materialInfo();
            ChunkSectionLayer layer = info.layer();
            int layerIdx = layer == null ? 0 : layer.ordinal();
            if (layerIdx < 0 || layerIdx >= LAYER_COUNT) layerIdx = 0;
            BufferBuilder builder = builders[layerIdx];
            used[layerIdx] = true;

            int color = computeColor(blockColors, state, quad, info);

            for (int v = 0; v < 4; v++) {
                float px = bx + quad.position(v).x();
                float py = by + quad.position(v).y();
                float pz = bz + quad.position(v).z();

                long packedUV = quad.packedUV(v);
                float u = Float.intBitsToFloat((int) (packedUV >> 32));
                float vf = Float.intBitsToFloat((int) packedUV);

                builder.addVertex(px, py, pz)
                       .setColor(color)
                       .setUv(u, vf)
                       .setUv2(0xF0, 0xF0);
            }
        }
    }

    private static int computeColor(BlockColors blockColors, BlockState state, BakedQuad quad, BakedQuad.MaterialInfo info) {
        int r = 255, g = 255, b = 255;
        int tintIndex = info.tintIndex();
        if (tintIndex >= 0) {
            try {
                BlockTintSource source = blockColors.getTintSource(state, tintIndex);
                if (source != null) {
                    int tint = source.color(state);
                    r = (tint >> 16) & 0xFF;
                    g = (tint >> 8) & 0xFF;
                    b = tint & 0xFF;
                }
            } catch (Exception ignored) {
            }
        }
        float shading = info.shade() ? getDirectionShading(quad.direction()) : 1.0f;
        r = Math.min(255, (int) (r * shading));
        g = Math.min(255, (int) (g * shading));
        b = Math.min(255, (int) (b * shading));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float getDirectionShading(Direction dir) {
        if (dir == null) return 1.0f;
        return switch (dir) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.6f;
        };
    }

    private static RenderPipeline pipelineForLayer(int layerIdx) {
        return switch (layerIdx) {
            case 1 -> RenderPipelines.CUTOUT_BLOCK;
            case 2 -> RenderPipelines.TRANSLUCENT_BLOCK;
            default -> RenderPipelines.SOLID_BLOCK;
        };
    }

    private void uploadPendingMesh() {
        MeshData[] meshes = pendingMeshes;
        ByteBufferBuilder[] allocators = pendingAllocators;
        pendingMeshes = null;
        pendingAllocators = null;
        hasPending = false;

        for (int i = 0; i < LAYER_COUNT; i++) {
            if (vertexBuffers[i] != null) {
                vertexBuffers[i].close();
                vertexBuffers[i] = null;
            }
            vertexCounts[i] = 0;
        }

        if (meshes == null) return;

        try {
            for (int i = 0; i < LAYER_COUNT; i++) {
                MeshData mesh = meshes[i];
                if (mesh == null) continue;
                ByteBuffer vb = mesh.vertexBuffer();
                if (vb != null && vb.remaining() > 0) {
                    final int layer = i;
                    vertexBuffers[i] = RenderSystem.getDevice().createBuffer(
                        () -> "Schematic Vertex Buffer " + layer,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        vb
                    );
                    vertexCounts[i] = mesh.drawState().vertexCount();
                }
            }
            cameraChanged = true;
        } finally {
            for (MeshData mesh : meshes) {
                if (mesh != null) mesh.close();
            }
            if (allocators != null) {
                for (ByteBufferBuilder allocator : allocators) {
                    if (allocator != null) allocator.close();
                }
            }
        }
    }

    private boolean hasContent() {
        for (GpuBuffer buffer : vertexBuffers) {
            if (buffer != null) return true;
        }
        return false;
    }

    public void fitToPanel(int panelW, int panelH) {
        panX = 0;
        panY = 0;
        cameraChanged = true;
    }

    public void render(GuiGraphicsExtractor ctx, int viewX, int viewY, int viewW, int viewH, int mouseX, int mouseY) {
        if (hasPending) {
            uploadPendingMesh();
        }

        boolean focused = mouseX >= viewX && mouseX < viewX + viewW
                && mouseY >= viewY && mouseY < viewY + viewH;
        handleMovement(focused);

        if (!hasContent()) return;

        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int fbW = Math.max(1, (int) (viewW * scale));
        int fbH = Math.max(1, (int) (viewH * scale));

        boolean fbResized = false;
        if (framebuffer == null) {
            framebuffer = new TextureTarget("SchematicPreview", fbW, fbH, true);
            fbResized = true;
        } else if (framebuffer.width != fbW || framebuffer.height != fbH) {
            framebuffer.resize(fbW, fbH);
            fbResized = true;
        }

        if (cameraChanged || fbResized) {
            cameraChanged = false;
            renderToFramebuffer(mc, fbW, fbH, framebuffer, 0xFF161616);
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        ctx.blit(framebuffer.getColorTextureView(), sampler, viewX, viewY, viewX + viewW, viewY + viewH, 0f, 1f, 1f, 0f);
    }

    private void handleMovement(boolean focused) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        if (!focused) return;
        if (dt < 0f) dt = 0f;
        if (dt > 0.1f) dt = 0.1f;

        Minecraft mc = Minecraft.getInstance();
        boolean forward = isKeyDown(mc, mc.options.keyUp);
        boolean back = isKeyDown(mc, mc.options.keyDown);
        boolean left = isKeyDown(mc, mc.options.keyLeft);
        boolean right = isKeyDown(mc, mc.options.keyRight);
        boolean up = isKeyDown(mc, mc.options.keyJump);
        boolean down = isKeyDown(mc, mc.options.keyShift);
        if (!(forward || back || left || right || up || down)) return;

        float yaw = (float) Math.toRadians(rotationY);
        float pitch = (float) Math.toRadians(rotationX);
        float cosPitch = (float) Math.cos(pitch);
        float fx = cosPitch * (float) Math.sin(yaw);
        float fy = -(float) Math.sin(pitch);
        float fz = -cosPitch * (float) Math.cos(yaw);
        float rx = (float) Math.cos(yaw);
        float rz = (float) Math.sin(yaw);

        float mx = 0, my = 0, mz = 0;
        if (forward) { mx += fx; my += fy; mz += fz; }
        if (back)    { mx -= fx; my -= fy; mz -= fz; }
        if (right)   { mx += rx; mz += rz; }
        if (left)    { mx -= rx; mz -= rz; }
        if (up)   my += 1f;
        if (down) my -= 1f;
        if (mx == 0 && my == 0 && mz == 0) return;

        float speed = Math.max(4f, distance) * MOVE_SPEED * dt;
        targetX += mx * speed;
        targetY += my * speed;
        targetZ += mz * speed;
        cameraChanged = true;
    }

    private static boolean isKeyDown(Minecraft mc, KeyMapping mapping) {
        try {
            if (keyMappingKeyField == null) {
                keyMappingKeyField = KeyMapping.class.getDeclaredField("key");
                keyMappingKeyField.setAccessible(true);
            }
            InputConstants.Key key = (InputConstants.Key) keyMappingKeyField.get(mapping);
            if (key == null) return false;
            return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
        } catch (Exception e) {
            return false;
        }
    }

    private void renderToFramebuffer(Minecraft mc, int fbW, int fbH, TextureTarget target, int clearColor) {
        float aspect = (float) fbW / fbH;
        Matrix4f projMat = new Matrix4f().perspective((float) Math.toRadians(FOV), aspect, NEAR, FAR);

        if (projectionBuffer == null) {
            projectionBuffer = RenderSystem.getDevice().createBuffer(
                () -> "Schematic Projection",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                64L
            );
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer matBuf = stack.malloc(64);
            projMat.get(0, matBuf);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(projectionBuffer.slice(), matBuf);
        }

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.slice(), ProjectionType.PERSPECTIVE);

        Matrix4f mvMatrix = new Matrix4f()
            .translate(panX * 0.02f, -panY * 0.02f, -distance)
            .rotateX((float) Math.toRadians(rotationX))
            .rotateY((float) Math.toRadians(rotationY))
            .translate(-targetX, -targetY, -targetZ);

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
            mvMatrix,
            new Vector4f(1f, 1f, 1f, 1f),
            new Vector3f(0f, 0f, 0f),
            new Matrix4f()
        );

        int maxIdx = 0;
        for (int count : vertexCounts) {
            int idx = (count / 4) * 6;
            if (idx > maxIdx) maxIdx = idx;
        }
        RenderSystem.AutoStorageIndexBuffer seqIdx = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuf = seqIdx.getBuffer(maxIdx);

        GpuTextureView atlasView = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        GpuTextureView lightmapView = mc.gameRenderer.lightmap();
        GpuSampler atlasSampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
        GpuSampler lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Schematic Preview",
                target.getColorTextureView(), OptionalInt.of(clearColor),
                target.getDepthTextureView(), OptionalDouble.of(1.0))) {
            for (int i = 0; i < LAYER_COUNT; i++) {
                GpuBuffer vertexBuffer = vertexBuffers[i];
                if (vertexBuffer == null || vertexCounts[i] == 0) continue;
                int idxCount = (vertexCounts[i] / 4) * 6;

                pass.setPipeline(pipelineForLayer(i));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", atlasView, atlasSampler);
                pass.bindTexture("Sampler2", lightmapView, lightSampler);
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuf, seqIdx.type());
                pass.drawIndexed(0, 0, idxCount, 1);
            }
        }

        RenderSystem.restoreProjectionMatrix();
    }

    public void onDrag(double dx, double dy, int button) {
        if (button == 0) {
            rotationY = ((rotationY + (float) (dx * 0.5)) % 360f + 360f) % 360f;
            rotationX = Math.max(-90f, Math.min(90f, rotationX + (float) (dy * 0.5)));
        } else {
            panX += (int) dx;
            panY += (int) dy;
        }
        cameraChanged = true;
    }

    public void onScroll(double amount) {
        distance = (float) Math.max(1.0, Math.min(500.0, distance * (amount > 0 ? 0.9 : 1.1)));
        cameraChanged = true;
    }

    public void reset() {
        rotationY = 135f;
        rotationX = 30f;
        distance = 20f;
        panX = 0;
        panY = 0;
        cameraChanged = true;
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public float getDistance() {
        return distance;
    }

    public float getFitDistance() {
        return fitDistance;
    }

    public void setRotationX(float value) {
        rotationX = Math.max(-90f, Math.min(90f, value));
        cameraChanged = true;
    }

    public void setRotationY(float value) {
        rotationY = Math.max(0f, Math.min(360f, value));
        cameraChanged = true;
    }

    public void setDistance(float value) {
        distance = Math.max(1f, Math.min(500f, value));
        cameraChanged = true;
    }

    public void setAutoIsometric() {
        rotationX = 30f;
        rotationY = 45f;
        distance = fitDistance;
        panX = 0;
        panY = 0;
        cameraChanged = true;
    }

    public void exportRender(File outputDir, String baseName, int resolution, boolean transparentBackground,
                             Consumer<File> onSuccess, Consumer<String> onError) {
        if (hasPending) {
            uploadPendingMesh();
        }
        if (!hasContent()) {
            onError.accept("Nothing to render");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        TextureTarget target = null;
        GpuBuffer readbackBuffer = null;
        try {
            target = new TextureTarget("SchematicExport", resolution, resolution, true);
            int clearColor = transparentBackground ? 0x00000000 : 0xFF161616;
            renderToFramebuffer(mc, resolution, resolution, target, clearColor);

            GpuTexture colorTexture = target.getColorTexture();
            int pixelSize = colorTexture.getFormat().pixelSize();
            readbackBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Schematic Export Readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    (long) resolution * resolution * pixelSize);

            final TextureTarget exportTarget = target;
            final GpuBuffer buffer = readbackBuffer;
            final CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    colorTexture, buffer, 0L,
                    () -> {
                        try {
                            File outFile = new File(outputDir, baseName + "_"
                                    + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()) + ".png");
                            Files.createDirectories(outputDir.toPath());
                            writeBufferToPng(encoder, buffer, resolution, pixelSize, outFile);
                            onSuccess.accept(outFile);
                        } catch (Exception e) {
                            onError.accept(e.getMessage() == null ? "Failed to write image" : e.getMessage());
                        } finally {
                            buffer.close();
                            exportTarget.destroyBuffers();
                        }
                    },
                    0);
        } catch (Throwable t) {
            if (readbackBuffer != null) {
                readbackBuffer.close();
            }
            if (target != null) {
                target.destroyBuffers();
            }
            onError.accept(t.getMessage() == null ? "Render failed" : t.getMessage());
        }
    }

    private static void writeBufferToPng(CommandEncoder encoder, GpuBuffer buffer, int size, int pixelSize, File outFile)
            throws IOException {
        NativeImage image = new NativeImage(size, size, false);
        try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
            ByteBuffer data = view.data();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int value = data.getInt((x + y * size) * pixelSize);
                    image.setPixelABGR(x, size - 1 - y, value);
                }
            }
        }
        try {
            image.writeToFile(outFile.toPath());
        } finally {
            image.close();
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (vertexBuffers[i] != null) {
                vertexBuffers[i].close();
                vertexBuffers[i] = null;
            }
            vertexCounts[i] = 0;
        }
        if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
        if (framebuffer != null) {
            framebuffer.destroyBuffers();
            framebuffer = null;
        }
        MeshData[] meshes = pendingMeshes;
        if (meshes != null) {
            for (MeshData mesh : meshes) {
                if (mesh != null) mesh.close();
            }
            pendingMeshes = null;
        }
        ByteBufferBuilder[] allocators = pendingAllocators;
        if (allocators != null) {
            for (ByteBufferBuilder allocator : allocators) {
                if (allocator != null) allocator.close();
            }
            pendingAllocators = null;
        }
    }
}
