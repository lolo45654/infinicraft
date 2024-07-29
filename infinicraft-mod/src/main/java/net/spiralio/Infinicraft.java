package net.spiralio;

import com.google.common.io.CharStreams;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.spiralio.blocks.InfinicrafterBlock;
import net.spiralio.blocks.entity.InfinicrafterBlockEntity;
import net.spiralio.blocks.screen.InfinicrafterScreenHandler;
import net.spiralio.items.InfiniteItem;
import net.spiralio.util.GeneratedItem;
import net.spiralio.util.GsonBodyHandler;
import net.spiralio.util.JsonHandler;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Infinicraft implements ModInitializer {

  private static Infinicraft INSTANCE;

  public static final Logger LOGGER = LoggerFactory.getLogger("infinicraft");
  public static final net.spiralio.InfinicraftConfig CONFIG = net.spiralio.InfinicraftConfig.createAndLoad();

  // the "infinite" item that takes any texture
  public static final Item INFINITE_ITEM = new InfiniteItem(
    new FabricItemSettings()
  );

  // infinicrafter block
  public static final Block INFINICRAFTER_BLOCK = new InfinicrafterBlock(
    FabricBlockSettings.create().strength(4.0f)
  );

  // infinicrafter block entity
  public static final BlockEntityType<InfinicrafterBlockEntity> INFINICRAFTER_ENTITY = Registry.register(
    Registries.BLOCK_ENTITY_TYPE,
    new Identifier("infinicrafter"),
    FabricBlockEntityTypeBuilder
      .create(InfinicrafterBlockEntity::new, INFINICRAFTER_BLOCK)
      .build()
  );

  // infinicrafter screen handler
  public static final ScreenHandlerType<InfinicrafterScreenHandler> INFINICRAFTER_SCREEN_HANDLER = Registry.register(
    Registries.SCREEN_HANDLER,
    new Identifier("infinicrafter"),
    new ExtendedScreenHandlerType<>(InfinicrafterScreenHandler::new)
  );

  private ExecutorService sdTalkerExecutor;

  private LinkedBlockingQueue<StableDiffusionRequest> requests = new LinkedBlockingQueue<>();

  public record StableDiffusionRequest(
    String itemColor,
    String itemDescription,
    BiConsumer<StableDiffusionResponse, Throwable> callback
  ) {}

  public record StableDiffusionResponse(
    @SerializedName("success") boolean isSuccess,
    @SerializedName("image") int@Nullable[] image
  ) {}

  public static CompletableFuture<StableDiffusionResponse> makeStableDiffusionRequest(
    GeneratedItem itemData
  ) {
    if (INSTANCE == null) {
      throw new Error("Infinicraft was not initialized yet!");
    }

    var task = new CompletableFuture<StableDiffusionResponse>();

    try {
      INSTANCE.requests.put(
        new StableDiffusionRequest(
          itemData.getColor(),
          itemData.getName() + " - " + itemData.getDescription(),
          (response, ex) -> {
            if (ex != null) {
              task.completeExceptionally(ex);
            } else {
              task.complete(response);
            }
          }
        )
      );
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    return task;
  }

  // building blocks (infinitum)
  public static final Block INFINITUM_BLOCK = new Block(
    FabricBlockSettings.create().strength(4.0f)
  );
  public static final Block SMOOTH_INFINITUM_BLOCK = new Block(
    FabricBlockSettings.create().strength(4.0f)
  );
  public static final Block INFINITUM_COLUMN = new Block(
    FabricBlockSettings.create().strength(4.0f)
  );

  // materials
  public static final Item INFINITUM = new Item(new FabricItemSettings());

  @Override
  public void onInitialize() {
    INSTANCE = this;

    // Runs as soon as Minecraft loads
    LOGGER.info("Infinicraft is initializing!");

    try {
      var infinicraftDir = FabricLoader
        .getInstance()
        .getConfigDir()
        .resolve("infinicraft");
      Files.createDirectories(infinicraftDir);

      if (!Files.exists(infinicraftDir.resolve("items.json"))) {
        Files.writeString(infinicraftDir.resolve("items.json"), "[]");
      }

      if (!Files.exists(infinicraftDir.resolve("recipes.json"))) {
        Files.writeString(infinicraftDir.resolve("recipes.json"), "[]");
      }
    } catch (IOException e) {
      LOGGER.error("Failed to create default config for infinicraft", e);
    }

    // Register item, blocks, and block items
    Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "infinite"),
      INFINITE_ITEM
    );
    Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "infinitum"),
      INFINITUM
    );
    Registry.register(
      Registries.BLOCK,
      new Identifier("infinicraft", "infinicrafter"),
      INFINICRAFTER_BLOCK
    );
    Item infinicrafter_blockitem = Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "infinicrafter"),
      new BlockItem(INFINICRAFTER_BLOCK, new FabricItemSettings())
    );

    Registry.register(
      Registries.BLOCK,
      new Identifier("infinicraft", "infinitum_block"),
      INFINITUM_BLOCK
    );
    Registry.register(
      Registries.BLOCK,
      new Identifier("infinicraft", "smooth_infinitum_block"),
      SMOOTH_INFINITUM_BLOCK
    );
    Registry.register(
      Registries.BLOCK,
      new Identifier("infinicraft", "infinitum_column"),
      INFINITUM_COLUMN
    );

    Item infinitum_blockitem = Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "infinitum_block"),
      new BlockItem(INFINITUM_BLOCK, new FabricItemSettings())
    );
    Item smooth_infinitum_blockitem = Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "smooth_infinitum_block"),
      new BlockItem(SMOOTH_INFINITUM_BLOCK, new FabricItemSettings())
    );
    Item infinitum_column_blockitem = Registry.register(
      Registries.ITEM,
      new Identifier("infinicraft", "infinitum_column"),
      new BlockItem(INFINITUM_COLUMN, new FabricItemSettings())
    );

    ServerLifecycleEvents.SERVER_STARTED.register(server -> {
      sdTalkerExecutor =
              Executors.newCachedThreadPool(
                      new ThreadFactory() {
                        private static final AtomicInteger threadNumber = new AtomicInteger(
                                1
                        );

                        @Override
                        public Thread newThread(@NotNull Runnable r) {
                          Thread t = new Thread(
                                  r,
                                  "Infinicraft SD-API Thread Pool #" +
                                          threadNumber.getAndIncrement()
                          );
                          t.setDaemon(false);
                          t.setPriority(Thread.MIN_PRIORITY);
                          return t;
                        }
                      }
              );

      sdTalkerExecutor.execute(() -> {
        LOGGER.info("Starting infinicraft backend server daemon");

        @SuppressWarnings("resource")
        var httpClient = HttpClient
          .newBuilder()
          .executor(sdTalkerExecutor)
          .build();

        try {
          while (!Thread.currentThread().isInterrupted()) {
            var sdRequest = requests.take();
            var httpRequest = HttpRequest
              .newBuilder()
              .GET()
              .uri(
                new URIBuilder(new URI(CONFIG.SD_DAEMON_BASE() + "/generate"))
                  .setParameter("itemColor", sdRequest.itemColor)
                  .setParameter("itemDescription", sdRequest.itemDescription)
                  .build()
              )
              .build();

            httpClient
              .sendAsync(
                httpRequest,
                GsonBodyHandler.ofJson(
                  JsonHandler.GSON,
                  StableDiffusionResponse.class
                )
              )
              .thenAccept(response -> {
                if (
                  response.statusCode() >= 400 || response.statusCode() < 200
                ) {
                  var body = response.body().get();

                  LOGGER.error(
                    "SD failure: Status code {}; body: {}",
                    response.statusCode(),
                    body
                  );
                  sdRequest.callback.accept(body, null);
                  return;
                }

                var body = response.body().get();
                sdRequest.callback.accept(body, null);
              })
              .exceptionally(ex -> {
                sdRequest.callback.accept(null, ex);
                return null;
              });
          }
        } catch (InterruptedException e) {
          // empty
        } catch (URISyntaxException e) {
          throw new RuntimeException(e);
        }

        LOGGER.info("Ending infinicraft backend server daemon");
      });
    });

    ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
      LOGGER.info("Shutting down infinicraft backend server daemon");
      if (sdTalkerExecutor == null) {
        LOGGER.warn("Infinicraft backend server is nonexistant. This shouldn't happen!");
        return;
      }

      try {
        //noinspection ResultOfMethodCallIgnored
        sdTalkerExecutor.awaitTermination(2, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {}

      sdTalkerExecutor.shutdownNow();
    });

    ItemGroupEvents
      .modifyEntriesEvent(ItemGroups.FUNCTIONAL)
      .register(content -> {
        content.add(infinicrafter_blockitem);
      });
    ItemGroupEvents
      .modifyEntriesEvent(ItemGroups.INGREDIENTS)
      .register(content -> {
        content.add(INFINITUM);
      });
    ItemGroupEvents
      .modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
      .register(content -> {
        content.add(infinitum_blockitem);
        content.add(smooth_infinitum_blockitem);
        content.add(infinitum_column_blockitem);
      });
  }
}
