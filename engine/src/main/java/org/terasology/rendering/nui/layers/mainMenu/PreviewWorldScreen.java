/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.nui.layers.mainMenu;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetType;
import org.terasology.asset.AssetUri;
import org.terasology.asset.Assets;
import org.terasology.config.Config;
import org.terasology.engine.module.ModuleManager;
import org.terasology.math.Rect2i;
import org.terasology.math.TeraMath;
import org.terasology.registry.CoreRegistry;
import org.terasology.registry.In;
import org.terasology.rendering.assets.texture.Texture;
import org.terasology.rendering.assets.texture.TextureData;
import org.terasology.rendering.nui.Color;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.WidgetUtil;
import org.terasology.rendering.nui.databinding.Binding;
import org.terasology.rendering.nui.databinding.DefaultBinding;
import org.terasology.rendering.nui.databinding.ReadOnlyBinding;
import org.terasology.rendering.nui.widgets.ActivateEventListener;
import org.terasology.rendering.nui.widgets.UIButton;
import org.terasology.rendering.nui.widgets.UIDropdown;
import org.terasology.rendering.nui.widgets.UIImage;
import org.terasology.rendering.nui.widgets.UILabel;
import org.terasology.rendering.nui.widgets.UISlider;
import org.terasology.rendering.nui.widgets.UIText;
import org.terasology.world.generator.WorldGenerator;
import org.terasology.world.generator.WorldGenerator2DPreview;
import org.terasology.world.generator.internal.WorldGeneratorInfo;
import org.terasology.world.generator.internal.WorldGeneratorManager;
import org.terasology.world.generator.plugin.TempWorldGeneratorPluginLibrary;
import org.terasology.world.generator.plugin.WorldGeneratorPluginLibrary;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * @author Immortius
 */
public class PreviewWorldScreen extends CoreScreenLayer {

    private static final Logger logger = LoggerFactory.getLogger(PreviewWorldScreen.class);

    @In
    private ModuleManager moduleManager;

    @In
    private WorldGeneratorManager worldGeneratorManager;

    @In
    private Config config;

    private int imageSize = 128;

    private WorldGenerator2DPreview previewGenerator;

    private SeedBinding seedBinding = new SeedBinding();

    private UILabel errorLabel;
    private UIImage previewImage;
    private UISlider zoomSlider;
    private UIButton applyButton;
    private UIDropdown<String> layerDropdown;
    private PreviewSettings currentSettings;

    @Override
    public void onOpened() {
        super.onOpened();

        CoreRegistry.put(WorldGeneratorPluginLibrary.class, new TempWorldGeneratorPluginLibrary());
        WorldGeneratorInfo info = worldGeneratorManager.getWorldGeneratorInfo(config.getWorldGeneration().getDefaultGenerator());

        try {
            WorldGenerator worldGenerator = worldGeneratorManager.createGenerator(info.getUri());
            seedBinding.setWorldGenerator(worldGenerator);

            if (worldGenerator instanceof WorldGenerator2DPreview) {
                previewGenerator = (WorldGenerator2DPreview) worldGenerator;
            } else {
                logger.info(info.getUri().toString() + " does not support a 2d preview");
            }
        } catch (Exception e) {
            // if errors happen, don't enable this feature
            seedBinding = new SeedBinding();
            previewGenerator = null;
            logger.error("Unable to load world generator: " + info.getUri().toString() + " for a 2d preview");
        }
    }

    @Override
    public void initialise() {
        zoomSlider = find("zoomSlider", UISlider.class);
        if (zoomSlider != null) {
            zoomSlider.setMinimum(1.0f);
            zoomSlider.setRange(99.f);
            zoomSlider.setIncrement(1.0f);
            zoomSlider.setValue(10f);
            zoomSlider.setPrecision(0);
        }

        applyButton = find("apply", UIButton.class);
        if (applyButton != null) {
            applyButton.setEnabled(false);
            applyButton.subscribe(new ActivateEventListener() {
                @Override
                public void onActivated(UIWidget widget) {
                    updatePreview();
                }
            });
        }

        errorLabel = find("error", UILabel.class);
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }

        previewImage = find("preview", UIImage.class);

        UIText seed = find("seed", UIText.class);
        if (seed != null) {
            seed.bindText(seedBinding);
        }

        layerDropdown = find("display", UIDropdown.class);
        layerDropdown.bindOptions(new ReadOnlyBinding<List<String>>() {
            @Override
            public List<String> get() {
                if (previewGenerator != null) {
                    return Lists.newArrayList(previewGenerator.getLayers());
                } else {
                    return Lists.newArrayList();
                }
            }
        });
        layerDropdown.bindSelection(new Binding<String>() {
            String selection;

            @Override
            public String get() {
                if (selection == null && layerDropdown.getOptions().size() > 0) {
                    // select the first one in the list
                    selection = layerDropdown.getOptions().get(0);
                }
                return selection;
            }

            @Override
            public void set(String value) {
                selection = value;
            }
        });


        WidgetUtil.trySubscribe(this, "close", new ActivateEventListener() {
            @Override
            public void onActivated(UIWidget button) {
                getManager().popScreen();
            }
        });
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        if (previewGenerator != null) {
            PreviewSettings newSettings = new PreviewSettings(layerDropdown.getSelection(), TeraMath.floorToInt(zoomSlider.getValue()), seedBinding.get());
            if (currentSettings == null || !currentSettings.equals(newSettings)) {
                boolean firstTime = currentSettings == null;
                currentSettings = newSettings;
                if (applyButton != null && !firstTime) {
                    applyButton.setEnabled(true);
                } else {
                    updatePreview();
                }
            }
        }
    }

    @Override
    public boolean isLowerLayerVisible() {
        return false;
    }

    public void bindSeed(Binding<String> binding) {
        seedBinding.setExternalBinding(binding);
    }

    public String getSeed() {
        return seedBinding.get();
    }

    public void setSeed(String val) {
        seedBinding.set(val);
    }

    private void updatePreview() {
        previewImage.setVisible(false);
        errorLabel.setVisible(false);

        Callable<ByteBufferResult> operation = new Callable<ByteBufferResult>() {
            @Override
            public ByteBufferResult call() throws InterruptedException {
                try {
                    ByteBuffer buf = createByteBuffer(imageSize, imageSize, currentSettings.zoom, currentSettings.layer);
                    return new ByteBufferResult(true, buf, null);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception ex) {
                    return new ByteBufferResult(false, null, ex);
                }
            }
        };

        final NUIManager manager = CoreRegistry.get(NUIManager.class);
        final WaitPopup<ByteBufferResult> popup = manager.pushScreen(WaitPopup.ASSET_URI, WaitPopup.class);
        popup.setMessage("Updating Preview", "Please wait ...");
        popup.onSuccess(new Function<ByteBufferResult, Void>() {
            @Override
            public Void apply(ByteBufferResult byteBufferResult) {
                if (byteBufferResult.success) {
                    previewImage.setImage(createTexture(imageSize, imageSize, byteBufferResult.buf));
                    previewImage.setVisible(true);
                    if (applyButton != null) {
                        applyButton.setEnabled(false);
                    }
                } else {
                    errorLabel.setText("Sorry: could not generate 2d preview :-(");
                    errorLabel.setVisible(true);
                    logger.error("Error generating a 2d preview for " + layerDropdown.getSelection(), byteBufferResult.exception);
                }
                return null;
            }
        });

        popup.startOperation(operation, true);
    }

    private Texture createTexture(int width, int height, ByteBuffer buf) {
        ByteBuffer[] data = new ByteBuffer[]{buf};
        AssetUri uri = new AssetUri(AssetType.TEXTURE, "engine:terrainPreview");
        TextureData texData = new TextureData(width, height, data, Texture.WrapMode.CLAMP, Texture.FilterMode.LINEAR);

        return Assets.generateAsset(uri, texData, Texture.class);
    }

    private ByteBuffer createByteBuffer(int width, int height, int scale, String layerName) throws InterruptedException {
        int size = 4 * width * height;
        final int offX = -width / 2;
        final int offY = -height / 2;
        ByteBuffer buf = ByteBuffer.allocateDirect(size);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int px = (x + offX) * scale;
                int py = (y + offY) * scale;
                Rect2i area = Rect2i.createFromMinAndSize(px, py, scale, scale);
                Color c = previewGenerator.get(layerName, area);
                c.addToBuffer(buf);
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
        }
        buf.flip();
        return buf;
    }

    private static class SeedBinding implements Binding<String> {

        private Binding<String> externalBinding = new DefaultBinding<>("");
        private WorldGenerator worldGenerator;

        @Override
        public String get() {
            return externalBinding.get();
        }

        @Override
        public void set(String value) {
            externalBinding.set(value);
            if (worldGenerator != null) {
                worldGenerator.setWorldSeed(value);
            }
        }

        public Binding<String> getExternalBinding() {
            return externalBinding;
        }

        public void setExternalBinding(Binding<String> externalBinding) {
            this.externalBinding = externalBinding;
            if (worldGenerator != null) {
                worldGenerator.setWorldSeed(get());
            }
        }

        public void setWorldGenerator(WorldGenerator worldGenerator) {
            this.worldGenerator = worldGenerator;
            worldGenerator.setWorldSeed(get());
        }
    }

    private static class PreviewSettings {
        private String layer;
        private int zoom;
        private String seed;

        public PreviewSettings(String layer, int zoom, String seed) {
            this.layer = layer;
            this.zoom = zoom;
            this.seed = seed;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof PreviewSettings) {
                PreviewSettings other = (PreviewSettings) obj;
                return Objects.equals(other.layer, layer) && other.zoom == zoom && Objects.equals(other.seed, seed);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(layer, zoom, seed);
        }
    }

    private static class ByteBufferResult {
        public boolean success;
        public ByteBuffer buf;
        public Exception exception;

        private ByteBufferResult(boolean success, ByteBuffer buf, Exception exception) {
            this.success = success;
            this.buf = buf;
            this.exception = exception;
        }
    }
}


