package org.popcraft.chunky.platform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.popcraft.chunky.Chunky;
import org.popcraft.chunky.GenerationTask;
import org.popcraft.chunky.Selection;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FabricConfig implements Config {
    private final Chunky chunky;
    private final Gson gson;
    private final Path configPath;
    private ConfigModel configModel;

    public FabricConfig(Chunky chunky) {
        this.chunky = chunky;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chunky.json");
        this.configPath = configFile.toPath();
        if (!configFile.exists()) {
            try {
                if (configFile.createNewFile()) {
                    this.configModel = new ConfigModel();
                    configModel.version = 1;
                    configModel.language = "en";
                    configModel.continueOnRestart = false;
                    configModel.watchdogs = new HashMap<>();

                    WatchdogModel playerModel = new WatchdogModel();
                    playerModel.enabled = false;
                    playerModel.startOn = 0;
                    configModel.watchdogs.put("players", playerModel);

                    WatchdogModel tpsModel = new WatchdogModel();
                    tpsModel.enabled = false;
                    tpsModel.startOn = 17;
                    configModel.watchdogs.put("tps", tpsModel);

                    saveConfig();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            reload();
        }
    }

    @Override
    public Optional<GenerationTask> loadTask(World world) {
        if (this.configModel == null) {
            return Optional.empty();
        }
        Map<String, TaskModel> tasks = this.configModel.tasks;
        if (tasks == null) {
            return Optional.empty();
        }
        TaskModel taskModel = tasks.get(world.getName());
        if (taskModel == null || taskModel.cancelled) {
            return Optional.empty();
        }
        Selection selection = new Selection(chunky);
        selection.world = world;
        selection.radiusX = taskModel.radius;
        selection.radiusZ = taskModel.radiusZ == null ? taskModel.radius : taskModel.radiusZ;
        selection.centerX = taskModel.centerX;
        selection.centerZ = taskModel.centerZ;
        selection.pattern = taskModel.iterator;
        selection.shape = taskModel.shape;
        long count = taskModel.count;
        long time = taskModel.time;
        return Optional.of(new GenerationTask(chunky, selection, count, time));
    }

    @Override
    public List<GenerationTask> loadTasks() {
        List<GenerationTask> generationTasks = new ArrayList<>();
        chunky.getPlatform().getServer().getWorlds().forEach(world -> loadTask(world).ifPresent(generationTasks::add));
        return generationTasks;
    }

    @Override
    public void saveTask(GenerationTask generationTask) {
        if (this.configModel == null) {
            this.configModel = new ConfigModel();
        }
        if (this.configModel.tasks == null) {
            this.configModel.tasks = new HashMap<>();
        }
        Map<String, TaskModel> tasks = this.configModel.tasks;
        TaskModel taskModel = tasks.getOrDefault(generationTask.getWorld().getName(), new TaskModel());
        String shape = generationTask.getShape().name();
        taskModel.cancelled = generationTask.isCancelled();
        taskModel.radius = generationTask.getRadiusX();
        if ("rectangle".equals(shape) || "oval".equals(shape)) {
            taskModel.radiusZ = generationTask.getRadiusZ();
        }
        taskModel.centerX = generationTask.getCenterX();
        taskModel.centerZ = generationTask.getCenterZ();
        taskModel.iterator = generationTask.getChunkIterator().name();
        taskModel.shape = shape;
        taskModel.count = generationTask.getCount();
        taskModel.time = generationTask.getTotalTime();
        tasks.put(generationTask.getWorld().getName(), taskModel);
        saveConfig();
    }

    @Override
    public void saveTasks() {
        chunky.getGenerationTasks().values().forEach(this::saveTask);
    }

    @Override
    public void cancelTask(World world) {
        loadTask(world).ifPresent(generationTask -> {
            generationTask.stop(true);
            saveTask(generationTask);
        });
    }

    @Override
    public void cancelTasks() {
        loadTasks().forEach(generationTask -> {
            generationTask.stop(true);
            saveTask(generationTask);
        });
    }

    @Override
    public void reload() {
        StringBuilder configBuilder = new StringBuilder();
        try {
            Files.lines(configPath).forEach(configBuilder::append);
        } catch (IOException e) {
            e.printStackTrace();
        }
        configModel = gson.fromJson(configBuilder.toString(), new TypeToken<ConfigModel>() {
        }.getType());
    }

    @Override
    public boolean getWatchdogEnabled(String key) {
        if (this.configModel == null) {
            this.configModel = new ConfigModel();
        }
        if (this.configModel.watchdogs == null) {
            this.configModel.watchdogs = new HashMap<>();
        }
        WatchdogModel model = this.configModel.watchdogs.get(key);
        if (model != null) {
            return model.enabled;
        }
        return false;
    }

    @Override
    public int getWatchdogStartOn(String key) {
        if (this.configModel == null) {
            this.configModel = new ConfigModel();
        }
        if (this.configModel.watchdogs == null) {
            this.configModel.watchdogs = new HashMap<>();
        }
        WatchdogModel model = this.configModel.watchdogs.get(key);
        if (model != null) {
            return model.startOn;
        }
        return 0;
    }

    public void saveConfig() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            gson.toJson(configModel, new TypeToken<ConfigModel>() {
            }.getType(), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Optional<ConfigModel> getConfigModel() {
        return Optional.ofNullable(configModel);
    }

    public static class ConfigModel {
        public int version;
        public String language;
        public boolean continueOnRestart;
        public Map<String, TaskModel> tasks;
        public Map<String, WatchdogModel> watchdogs;
    }

    public static class TaskModel {
        public boolean cancelled;
        public int radius;
        public Integer radiusZ;
        public int centerX;
        public int centerZ;
        public String iterator;
        public String shape;
        public long count;
        public long time;
    }

    public static class WatchdogModel {
        public boolean enabled;
        public int startOn;
    }
}
