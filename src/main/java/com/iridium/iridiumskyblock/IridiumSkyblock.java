package com.iridium.iridiumskyblock;

import com.iridium.iridiumskyblock.commands.CommandManager;
import com.iridium.iridiumskyblock.configs.*;
import com.iridium.iridiumskyblock.gui.ShopGUI;
import com.iridium.iridiumskyblock.gui.TopGUI;
import com.iridium.iridiumskyblock.gui.VisitGUI;
import com.iridium.iridiumskyblock.listeners.*;
import com.iridium.iridiumskyblock.placeholders.ClipPlaceholderAPIManager;
import com.iridium.iridiumskyblock.placeholders.MVDWPlaceholderAPIManager;
import com.iridium.iridiumskyblock.serializer.Persist;
import com.iridium.iridiumskyblock.support.Vault;
import com.iridium.iridiumskyblock.support.Wildstacker;
import com.iridium.iridiumskyblock.timings.StopWatch;
import com.iridium.iridiumskyblock.timings.TimingsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

public class IridiumSkyblock extends JavaPlugin {

    private static IridiumSkyblock instance;

    public HashMap<Schematics.FakeSchematic, Schematic> schems = new HashMap<>();
    public HashMap<Schematics.FakeSchematic, Schematic> netherschems = new HashMap<>();

    public static Config configuration;
    public static Messages messages;
    public static Missions missions;
    public static Upgrades upgrades;
    public static Boosters boosters;
    public static Inventories inventories;
    public static Schematics schematics;
    public static Commands commands;
    public static BlockValues blockValues;
    public static Shop shop;
    public static TimingsManager timingsManager;

    private static Persist persist;

    private static IslandManager islandManager;

    private static CommandManager commandManager;

    public static TopGUI topGUI;

    public static ShopGUI shopGUI;

    public static HashMap<Integer, VisitGUI> visitGUI;

    public boolean updatingBlocks = false;

    private String latest;

    public static HashMap<Integer, List<String>> oreUpgradeCache = new HashMap<>();
    public static HashMap<Integer, List<String>> netherOreUpgradeCache = new HashMap<>();

    public static SkyblockGenerator generator;

    @Override
    public void onEnable() {
        timingsManager = new TimingsManager();
        StopWatch startTimer = new StopWatch("Plugin Load timer");
        timingsManager.Watch(startTimer);
        try {
            startTimer.Start();
            instance = this;

            super.onEnable();
            Bukkit.getUpdateFolderFile().mkdir();
            getDataFolder().mkdir();

            persist = new Persist();

            configuration = persist.getFile(Config.class).exists() ? persist.load(Config.class) : new Config();
            startTimer.Checkpoint("Configuration file set", true);

            Bukkit.getScheduler().runTask(this, () -> { // Call this a tick later to ensure all worlds are loaded

                startTimer.Checkpoint("Scheduler started", true);
                loadConfigs();
                startTimer.Checkpoint("Config loaded", true);
                loadIslandManager();
                startTimer.Checkpoint("Island manager started", true);
                saveConfigs();
                startTimer.Checkpoint("Config's saved", true);

                commandManager = new CommandManager("island");
                commandManager.registerCommands();
                startTimer.Checkpoint("Commands registered", true);

                if (Bukkit.getPluginManager().getPlugin("Vault") != null) new Vault();
                if (Bukkit.getPluginManager().isPluginEnabled("WildStacker")) new Wildstacker();
                if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) registerMultiverse();
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
                    registerListeners(new onExpansionUnregister());

                // Call it as a delayed task to wait for the server to properly load first
                Bukkit.getScheduler().scheduleSyncDelayedTask(IridiumSkyblock.getInstance(), IridiumSkyblock.getInstance()::islandValueManager);

                topGUI = new TopGUI();
                shopGUI = new ShopGUI();
                visitGUI = new HashMap<>();

                registerListeners(new onBlockPiston(), new onEntityPickupItem(), new onPlayerTalk(), new onItemCraft(), new onPlayerTeleport(), new onPlayerPortal(), new onBlockBreak(), new onBlockPlace(), new onClick(), new onBlockFromTo(), new onSpawnerSpawn(), new onEntityDeath(), new onPlayerJoinLeave(), new onBlockGrow(), new onPlayerTalk(), new onPlayerMove(), new onEntityDamageByEntity(), new onPlayerExpChange(), new onPlayerFish(), new onEntityExplode());

                new Metrics(IridiumSkyblock.getInstance());

                Bukkit.getScheduler().scheduleAsyncRepeatingTask(IridiumSkyblock.getInstance(), this::saveIslandManager, 0, 20 * 60);

                if (configuration.doIslandBackup)
                    Bukkit.getScheduler().scheduleAsyncRepeatingTask(IridiumSkyblock.getInstance(), this::backupIslandManager, 0, 20 * 60 * getConfiguration().backupIntervalMinutes);

                Bukkit.getScheduler().scheduleAsyncRepeatingTask(IridiumSkyblock.getInstance(), this::addPages, 0, 20 * 60);

                setupPlaceholderAPI();
                startTimer.Checkpoint("Placeholder API setup", true);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    Island island = getIslandManager().getIslandViaLocation(p.getLocation());
                    if (island != null) {
                        island.sendBorder(p);
                    }
                }
                startTimer.Checkpoint("Everybody sent island border", true);

                startCounting();
                startTimer.Checkpoint("Counting started", true);
                startTimer.Stop();
                getLogger().info("-------------------------------");
                getLogger().info("");
                getLogger().info(getDescription().getName() + " Enabled!");
                getLogger().info("");
                getLogger().info("-------------------------------");

                Bukkit.getScheduler().scheduleAsyncDelayedTask(this, () -> {
                    try {
                        latest = new BufferedReader(new InputStreamReader(new URL("https://api.spigotmc.org/legacy/update.php?resource=62480").openConnection().getInputStream())).readLine();
                    } catch (IOException e) {
                        getLogger().warning("Failed to connect to api.spigotmc.org");
                    }
                    if (latest != null && !latest.equals(getDescription().getVersion())) {
                        getLogger().info("Newer version available: " + latest);
                        if (getConfiguration().automaticUpdate) {
                            getLogger().info("Attempting to download version: " + latest);
                            try {
                                getFile().renameTo(new File(getFile().getParentFile(), "/IridiumSkyblock-" + latest + ".jar"));
                                File file = new File(Bukkit.getUpdateFolderFile() + "/IridiumSkyblock-" + latest + ".jar");
                                file.createNewFile();
                                URL url = new URL("http://www.peachessupport.xyz/IridiumSkyblock-" + latest + ".jar");
                                OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
                                URLConnection conn = url.openConnection();
                                conn.setConnectTimeout(15000);
                                conn.setReadTimeout(15000);
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                                conn.setAllowUserInteraction(false);
                                conn.setDoOutput(true);
                                InputStream in = conn.getInputStream();
                                byte[] buffer = new byte[1024];

                                int numRead;
                                while ((numRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, numRead);
                                }
                                in.close();
                                out.close();
                            } catch (Exception e) {
                                sendErrorMessage(e);
                            }
                        }
                    }
                });
            });
        } catch (Exception e) {
            sendErrorMessage(e);
        }
    }

    private void registerMultiverse() {
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mv import " + getIslandManager().getWorld().getName() + " normal -g " + getName());
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mv modify set generator " + getName() + " " + getIslandManager().getWorld().getName());

        if (IridiumSkyblock.getConfiguration().netherIslands) {
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mv import " + getIslandManager().getNetherWorld().getName() + " nether -g " + getName());
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "mv modify set generator " + getName() + " " + getIslandManager().getNetherWorld().getName());
        }
    }

    @Override
    public void onDisable() {
        try {
            super.onDisable();

            saveData();

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.closeInventory();
            }

            getLogger().info("-------------------------------");
            getLogger().info("");
            getLogger().info(getDescription().getName() + " Disabled!");
            getLogger().info("");
            getLogger().info("-------------------------------");
        } catch (Exception e) {
            sendErrorMessage(e);
        }
    }

    public void saveIslandManager() {
        if (getIslandManager() != null) {
            getDataFolder().mkdir();
            if (islandManager != null) {
                getPersist().save(islandManager, getPersist().getFile("IslandManager_temp"));
                try {
                    if (persist.load(IslandManager.class, getPersist().getFile("IslandManager_temp")) == null) {
                        getPersist().getFile("IslandManager_temp").delete();
                        return;
                    }
                } catch (Exception e) {
                    getPersist().getFile("IslandManager_temp").delete();
                    return;
                }
                getPersist().getFile(islandManager).delete();
                getPersist().getFile("IslandManager_temp").renameTo(getPersist().getFile(islandManager));
            }
        }
    }

    public void backupIslandManager() {
        if (getIslandManager() != null) {
            File backupsFolder = new File(getDataFolder(), "backups");
            if (!backupsFolder.exists()) backupsFolder.mkdir();
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -getConfiguration().deleteBackupsAfterDays);
            for (File file : backupsFolder.listFiles()) {
                Date date = getLocalDateTime(file.getName().replace(".json", "").replace("IslandManager_", ""));
                if (date == null) {
                    file.delete();
                } else {
                    if (date.before(cal.getTime())) {
                        file.delete();
                    }
                }
            }
            getPersist().save(islandManager, new File(backupsFolder, "IslandManager_" + getCurrentTimeStamp() + ".json"));
        }
    }

    public String getCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");//dd/MM/yyyy
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    public Date getLocalDateTime(String time) {
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");//dd/MM/yyyy
        try {
            return sdfDate.parse(time);
        } catch (ParseException e) {
            return null;
        }
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (worldName.equals(getConfiguration().worldName) || worldName.equals(getConfiguration().worldName + "_nether"))
            return generator;
        return super.getDefaultWorldGenerator(worldName, id);
    }

    private void addPages() {
        int size = (int) (Math.floor(Utils.getIslands().size() / 45.00) + 1);
        for (int i = 1; i <= size; i++) {
            if (!visitGUI.containsKey(i)) {
                visitGUI.put(i, new VisitGUI(i));
            }
        }
    }

    public void startCounting() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        new Timer().schedule(new TimerTask() {
            public void run() {
                LocalDateTime ldt = LocalDateTime.now();
                if (ldt.getDayOfWeek().equals(DayOfWeek.MONDAY) && getConfiguration().missionRestart.equals(MissionRestart.Weekly) || getConfiguration().missionRestart.equals(MissionRestart.Daily)) {
                    for (Island island : getIslandManager().islands.values()) {
                        island.resetMissions();
                    }
                }
                for (Island island : getIslandManager().islands.values()) {
                    int cm = island.money;
                    int cc = island.getCrystals();
                    int ce = island.exp;
                    island.money = (int) Math.floor(island.money * (1 + (getConfiguration().dailyMoneyInterest / 100.00)));
                    island.setCrystals((int) Math.floor(island.getCrystals() * (1 + (getConfiguration().dailyCrystalsInterest / 100.00))));
                    island.exp = (int) Math.floor(island.exp * (1 + (getConfiguration().dailyExpInterest / 100.00)));
                    for (String member : island.getMembers()) {
                        Player p = Bukkit.getPlayer(User.getUser(member).name);
                        p.sendMessage(Utils.color(IridiumSkyblock.getMessages().islandInterest.replace("%exp%", island.exp - ce + "").replace("%crystals%", island.getCrystals() - cc + "").replace("%money%", island.money - cm + "").replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
                    }
                }
                startCounting();
            }

        }, c.getTime());
    }

    public void islandValueManager() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            ListIterator<Integer> islands = new ArrayList<>(islandManager.islands.keySet()).listIterator();

            @Override
            public void run() {
                if (!updatingBlocks) {
                    if (!islands.hasNext()) {
                        islands = new ArrayList<>(islandManager.islands.keySet()).listIterator();
                    }
                    if (islands.hasNext()) {
                        int id = islands.next();
                        Island island = IridiumSkyblock.getIslandManager().getIslandViaId(id);
                        if (island != null) {
                            updatingBlocks = true;
                            island.initBlocks();
                        }
                    }
                }
            }
        }, 0, 0);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            ListIterator<Integer> islands = new ArrayList<>(islandManager.islands.keySet()).listIterator();

            @Override
            public void run() {
                if (!islands.hasNext()) {
                    islands = new ArrayList<>(islandManager.islands.keySet()).listIterator();
                }
                if (islands.hasNext()) {
                    int id = islands.next();
                    Island island = IridiumSkyblock.getIslandManager().getIslandViaId(id);
                    if (island != null) {
                        island.calculateIslandValue();
                    }
                }
            }
        }, 0, IridiumSkyblock.getConfiguration().islandsUpdateInterval);
    }

    public void sendErrorMessage(Exception e) {
        e.printStackTrace();
    }

    public void registerListeners(Listener... listener) {
        for (Listener l : listener) {
            Bukkit.getPluginManager().registerEvents(l, this);
        }
    }

    private void setupPlaceholderAPI() {
        Plugin mvdw = getServer().getPluginManager().getPlugin("MVdWPlaceholderAPI");
        if (mvdw != null && mvdw.isEnabled()) {
            new MVDWPlaceholderAPIManager().register();
            getLogger().info("Successfully registered placeholders with MVDWPlaceholderAPI.");
        }
        setupClipsPlaceholderAPI();
    }

    public void setupClipsPlaceholderAPI() {
        Plugin clip = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (clip != null && clip.isEnabled()) {
            if (new ClipPlaceholderAPIManager().register()) {
                getLogger().info("Successfully registered placeholders with PlaceholderAPI.");
            }
        }
    }

    public void loadSchematics() throws IOException {
        File schematicFolder = new File(getDataFolder(), "schematics");
        if (!schematicFolder.exists()) {
            schematicFolder.mkdir();
            if (!new File(schematicFolder, "island.schematic").exists()) {
                if (getResource("schematics/island.schematic") != null) {
                    saveResource("schematics/island.schematic", false);
                }
            }
            if (!new File(schematicFolder, "nether.schematic").exists()) {
                if (getResource("schematics/nether.schematic") != null) {
                    saveResource("schematics/nether.schematic", false);
                }
            }
        }

        schems.clear();

        for (Schematics.FakeSchematic fakeSchematic : schematics.schematics) {
            schems.put(fakeSchematic, Schematic.loadSchematic(new File(schematicFolder, fakeSchematic.name)));
            if (fakeSchematic.netherisland == null) {
                fakeSchematic.netherisland = fakeSchematic.name;
            }
            netherschems.put(fakeSchematic, Schematic.loadSchematic(new File(schematicFolder, fakeSchematic.netherisland)));
        }
    }

    public void loadIslandManager() {
        islandManager = persist.getFile(IslandManager.class).exists() ? persist.load(IslandManager.class) : new IslandManager();

        for (Island island : islandManager.islands.values()) {
            island.init();
        }
        getIslandManager().getWorld().getWorldBorder().setSize(Double.MAX_VALUE);
        if (getConfiguration().netherIslands)
            getIslandManager().getNetherWorld().getWorldBorder().setSize(Double.MAX_VALUE);
    }

    public void loadConfigs() {
        configuration = persist.getFile(Config.class).exists() ? persist.load(Config.class) : new Config();
        missions = persist.getFile(Missions.class).exists() ? persist.load(Missions.class) : new Missions();
        messages = persist.getFile(Messages.class).exists() ? persist.load(Messages.class) : new Messages();
        upgrades = persist.getFile(Upgrades.class).exists() ? persist.load(Upgrades.class) : new Upgrades();
        boosters = persist.getFile(Boosters.class).exists() ? persist.load(Boosters.class) : new Boosters();
        inventories = persist.getFile(Inventories.class).exists() ? persist.load(Inventories.class) : new Inventories();
        schematics = persist.getFile(Schematics.class).exists() ? persist.load(Schematics.class) : new Schematics();
        commands = persist.getFile(Commands.class).exists() ? persist.load(Commands.class) : new Commands();
        blockValues = persist.getFile(BlockValues.class).exists() ? persist.load(BlockValues.class) : new BlockValues();
        shop = persist.getFile(Shop.class).exists() ? persist.load(Shop.class) : new Shop();

        if (shop.shop == null) shop = new Shop();

        if (getCommandManager() != null) {
            if (getCommandManager().commands.contains(IridiumSkyblock.getCommands().shopCommand)) {
                if (!configuration.islandShop)
                    getCommandManager().unRegisterCommand(IridiumSkyblock.getCommands().shopCommand);
            } else {
                if (configuration.islandShop)
                    getCommandManager().registerCommand(IridiumSkyblock.getCommands().shopCommand);
            }
        }

        getBlockValues().blockvalue.remove(MultiversionMaterials.AIR);

        oreUpgradeCache.clear();
        for (int i : getUpgrades().oresUpgrade.upgrades.keySet()) {
            ArrayList<String> items = new ArrayList<>();
            for (String item : getUpgrades().oresUpgrade.upgrades.get(i).ores) {
                if (item != null) {
                    int i1 = Integer.parseInt(item.split(":")[1]);
                    for (int a = 0; a <= i1; a++) {
                        items.add(item.split(":")[0]);
                    }
                } else {
                    getUpgrades().oresUpgrade.upgrades.get(i).ores.remove(null);
                }
            }
            oreUpgradeCache.put(i, items);
        }

        netherOreUpgradeCache.clear();
        for (int i : getUpgrades().oresUpgrade.upgrades.keySet()) {
            ArrayList<String> items = new ArrayList<>();
            for (String item : getUpgrades().oresUpgrade.upgrades.get(i).netherores) {
                if (item != null) {
                    int i1 = Integer.parseInt(item.split(":")[1]);
                    for (int a = 0; a <= i1; a++) {
                        items.add(item.split(":")[0]);
                    }
                } else {
                    getUpgrades().oresUpgrade.upgrades.get(i).netherores.remove(null);
                }
            }
            netherOreUpgradeCache.put(i, items);
        }

        if (getBoosters().flightBooster.time == 0) getBoosters().flightBooster.time = 3600;
        if (getBoosters().experianceBooster.time == 0) getBoosters().experianceBooster.time = 3600;
        if (getBoosters().farmingBooster.time == 0) getBoosters().farmingBooster.time = 3600;
        if (getBoosters().spawnerBooster.time == 0) getBoosters().spawnerBooster.time = 3600;

        if (getBoosters().spawnerBooster.crystalsCost == 0 && getBoosters().spawnerBooster.vaultCost == 0)
            getBoosters().spawnerBooster.crystalsCost = 15;
        if (getBoosters().farmingBooster.crystalsCost == 0 && getBoosters().farmingBooster.vaultCost == 0)
            getBoosters().farmingBooster.crystalsCost = 15;
        if (getBoosters().experianceBooster.crystalsCost == 0 && getBoosters().experianceBooster.vaultCost == 0)
            getBoosters().experianceBooster.crystalsCost = 15;
        if (getBoosters().flightBooster.crystalsCost == 0 && getBoosters().flightBooster.vaultCost == 0)
            getBoosters().flightBooster.crystalsCost = 15;

        if (getConfiguration().blockvalue != null) {
            getBlockValues().blockvalue = (HashMap<MultiversionMaterials, Integer>) getConfiguration().blockvalue.clone();
            getConfiguration().blockvalue = null;
        }
        if (getConfiguration().spawnervalue != null) {
            getBlockValues().spawnervalue = (HashMap<String, Integer>) getConfiguration().spawnervalue.clone();
            getConfiguration().spawnervalue = null;
        }
        int max = 0;
        for (Upgrades.IslandUpgrade size : getUpgrades().sizeUpgrade.upgrades.values()) {
            if (max < size.size) {
                max = size.size;
            }
        }
        if (getConfiguration().distance <= max) {
            getConfiguration().distance = max + 1;
        }
        try {
            loadSchematics();
        } catch (Exception e) {

        }
    }

    public void saveData() {
        if (islandManager != null) persist.save(islandManager);
    }

    public void saveConfigs() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (configuration != null) persist.save(configuration);
            if (missions != null) persist.save(missions);
            if (messages != null) persist.save(messages);
            if (upgrades != null) persist.save(upgrades);
            if (boosters != null) persist.save(boosters);
            if (inventories != null) persist.save(inventories);
            if (schematics != null) persist.save(schematics);
            if (commands != null) persist.save(commands);
            if (blockValues != null) persist.save(blockValues);
            if (shop != null) persist.save(shop);
        });
    }

    public String getLatest() {
        return latest;
    }

    public static IridiumSkyblock getInstance() {
        return instance;
    }

    public static IslandManager getIslandManager() {
        return islandManager;
    }

    public static Config getConfiguration() {
        return configuration;
    }

    public static Missions getMissions() {
        return missions;
    }

    public static CommandManager getCommandManager() {
        return commandManager;
    }

    public static Messages getMessages() {
        return messages;
    }

    public static BlockValues getBlockValues() {
        return blockValues;
    }

    public static Upgrades getUpgrades() {
        if (upgrades == null) {
            upgrades = new Upgrades();
            IridiumSkyblock.getPersist().getFile(upgrades).delete();
            IridiumSkyblock.getInstance().saveConfigs();
        }
        return upgrades;
    }

    public static Commands getCommands() {
        return commands;
    }

    public static Boosters getBoosters() {
        return boosters;
    }

    public static Schematics getSchematics() {
        return schematics;
    }

    public static Inventories getInventories() {
        return inventories;
    }

    public static ShopGUI getShopGUI() {
        return shopGUI;
    }

    public static Shop getShop() {
        return shop;
    }

    public static Persist getPersist() {
        return persist;
    }
}