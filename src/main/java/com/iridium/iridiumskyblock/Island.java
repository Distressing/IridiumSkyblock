package com.iridium.iridiumskyblock;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.spawn.EssentialsSpawn;
import com.iridium.iridiumskyblock.api.IslandCreateEvent;
import com.iridium.iridiumskyblock.api.IslandDeleteEvent;
import com.iridium.iridiumskyblock.configs.Missions;
import com.iridium.iridiumskyblock.configs.Schematics;
import com.iridium.iridiumskyblock.gui.*;
import com.iridium.iridiumskyblock.support.Wildstacker;
import com.iridium.iridiumskyblock.timings.StopWatch;
import net.md_5.bungee.api.chat.*;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockData;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class Island {

    public static class Warp {
        Location location;
        String name;
        String password;

        public Warp(Location location, String name, String password) {
            this.location = location;
            this.name = name;
            this.password = password;
        }

        public Location getLocation() {
            return location;
        }

        public String getName() {
            return name;
        }

        public String getPassword() {
            return password;
        }
    }

    private transient List<Chunk> chunks;

    private String owner;
    private HashSet<String> members;
    private Location pos1;
    private Location pos2;
    private Location center;
    private Location home;
    private Location netherhome;

    private transient UpgradeGUI upgradeGUI;
    private transient BoosterGUI boosterGUI;
    private transient MissionsGUI missionsGUI;
    private transient MembersGUI membersGUI;
    private transient WarpGUI warpGUI;
    private transient BorderColorGUI borderColorGUI;
    private transient SchematicSelectGUI schematicSelectGUI;
    private transient PermissionsGUI permissionsGUI;
    private transient IslandMenuGUI islandMenuGUI;
    private transient CoopGUI coopGUI;
    private transient BankGUI bankGUI;
    private transient BiomeGUI biomeGUI;

    private int id;

    private int spawnerBooster;
    private int farmingBooster;
    private int expBooster;
    private int flightBooster;

    private int boosterid;

    private int crystals;

    private int sizeLevel;
    private int memberLevel;
    private int warpLevel;
    private int oreLevel;

    private int a;

    private int chunkID;

    private int value;

    //Changed to Set to remove need for inefficient contains checks.
    public CopyOnWriteArraySet<Location> blocks;
    public transient int lastblocks = 0;

    private List<Warp> warps;

    private int startvalue;

    private HashMap<String, Integer> missions = new HashMap<>();

    private boolean visit;

    private NMSUtils.Color borderColor;

    private HashMap<Role, Permissions> permissions;

    private String schematic;

    private HashSet<String> bans;

    private HashSet<String> votes;

    private HashSet<Integer> coop;

    public transient HashSet<Integer> coopInvites;

    private String name;

    public int money;
    public int exp;

    public Biome biome;

    private StopWatch stopWatch;

    public transient HashSet<Location> failedGenerators;

    public Island(Player owner, Location pos1, Location pos2, Location center, Location home, Location netherhome, int id) {
        User user = User.getUser(owner);
        user.role = Role.Owner;
        this.biome = IridiumSkyblock.getConfiguration().defaultBiome;
        blocks = new CopyOnWriteArraySet<>();
        this.owner = user.player;
        this.name = user.name;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.center = center;
        this.home = home;
        this.netherhome = netherhome;
        this.members = new HashSet<>(Collections.singletonList(user.player));
        this.id = id;
        spawnerBooster = 0;
        farmingBooster = 0;
        expBooster = 0;
        flightBooster = 0;
        crystals = 0;
        sizeLevel = 1;
        memberLevel = 1;
        warpLevel = 1;
        oreLevel = 1;
        value = 0;
        warps = new ArrayList<>();
        startvalue = -1;
        borderColor = NMSUtils.Color.Blue;
        visit = IridiumSkyblock.getConfiguration().defaultIslandPublic;
        permissions = (HashMap<Role, Permissions>) IridiumSkyblock.getConfiguration().defaultPermissions.clone();
        this.coop = new HashSet<>();
        this.bans = new HashSet<>();
        this.votes = new HashSet<>();
        init();
        Bukkit.getPluginManager().callEvent(new IslandCreateEvent(owner, this));
    }

    public void resetMissions() {
        if (missions == null) missions = new HashMap<>();
        missions.clear();
    }

    public int getMission(String mission) {
        if (missions == null) missions = new HashMap<>();
        if (!missions.containsKey(mission)) missions.put(mission, 0);
        return missions.get(mission);
    }

    public void addMission(String mission, int amount) {
        if (missions == null) missions = new HashMap<>();
        if (!missions.containsKey(mission)) missions.put(mission, 0);
        if (missions.get(mission) == Integer.MIN_VALUE) return;
        missions.put(mission, missions.get(mission) + amount);
        for (Missions.Mission m : IridiumSkyblock.getMissions().missions) {
            if (m.name.equals(mission)) {
                if (m.amount <= missions.get(mission)) {
                    completeMission(m);
                }
                break;
            }
        }
    }

    public void setMission(String mission, int amount) {
        if (missions == null) missions = new HashMap<>();
        if (!missions.containsKey(mission)) missions.put(mission, 0);
        if (missions.get(mission) == Integer.MIN_VALUE) return;
        missions.put(mission, amount);
        for (Missions.Mission m : IridiumSkyblock.getMissions().missions) {
            if (m.name.equals(mission)) {
                if (m.amount <= missions.get(mission)) {
                    completeMission(m);
                }
                break;
            }
        }
    }

    public Permissions getPermissions(Role role) {
        if (permissions == null)
            permissions = (HashMap<Role, Permissions>) IridiumSkyblock.getConfiguration().defaultPermissions.clone();
        if (!permissions.containsKey(role)) {
            permissions.put(role, new Permissions());
        }
        return permissions.get(role);
    }

    public void sendBorder() {
        for (Chunk c : chunks) {
            for (Entity e : c.getEntities()) {
                if (e instanceof Player) {
                    if (isInIsland(e.getLocation())) {
                        Player p = (Player) e;
                        sendBorder(p);
                    }
                }
            }
        }
    }

    public void hideBorder() {
        for (Chunk c : chunks) {
            for (Entity e : c.getEntities()) {
                if (e instanceof Player) {
                    if (isInIsland(e.getLocation())) {
                        Player p = (Player) e;
                        hideBorder(p);
                    }
                }
            }
        }
    }

    public void sendBorder(Player p) {
        if (p.getLocation().getWorld().equals(IridiumSkyblock.getIslandManager().getWorld())) {
            NMSUtils.sendWorldBorder(p, borderColor, IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size + 1, getCenter());
        } else if (IridiumSkyblock.getConfiguration().netherIslands) {
            Location loc = getCenter().clone();
            loc.setWorld(IridiumSkyblock.getIslandManager().getNetherWorld());
            NMSUtils.sendWorldBorder(p, borderColor, IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size + 1, loc);
        }
    }

    public void hideBorder(Player p) {
        NMSUtils.sendWorldBorder(p, borderColor, Integer.MAX_VALUE, getCenter().clone());
    }

    public void completeMission(Missions.Mission mission) {
        missions.put(mission.name, (IridiumSkyblock.getConfiguration().missionRestart == MissionRestart.Instantly ? 0 : Integer.MIN_VALUE));
        this.crystals += mission.crystalReward;
        money += mission.vaultReward;
        for (String member : members) {
            Player p = Bukkit.getPlayer(User.getUser(member).name);
            if (p != null) {
                NMSUtils.sendTitle(p, IridiumSkyblock.getMessages().missionComplete.replace("%mission%", mission.name), 20, 40, 20);
                NMSUtils.sendSubTitle(p, IridiumSkyblock.getMessages().rewards.replace("%crystalsReward%", mission.crystalReward + "").replace("%vaultReward%", mission.vaultReward + ""), 20, 40, 20);
            }
        }
    }

    public void calculateIslandValue() {
        if (blocks == null) blocks = new CopyOnWriteArraySet<>();
        if (blocks.hashCode() == lastblocks) return;
        int value = 0;
        Iterator<Location> locations = blocks.iterator();
        while (locations.hasNext()) {
            Location loc = locations.next();
            Block block = loc.getBlock();
            if (IridiumSkyblock.getBlockValues().blockvalue.containsKey(MultiversionMaterials.fromMaterial(block.getType()))) {
                value += IridiumSkyblock.getBlockValues().blockvalue.get(MultiversionMaterials.fromMaterial(block.getType()));
            } else if (loc.getBlock().getState() instanceof CreatureSpawner) {
                CreatureSpawner spawner = (CreatureSpawner) block.getState();
                if (IridiumSkyblock.getBlockValues().spawnervalue.containsKey(spawner.getSpawnedType().name())) {
                    int temp = IridiumSkyblock.getBlockValues().spawnervalue.get(spawner.getSpawnedType().name());
                    if (Wildstacker.enabled) {
                        temp *= Wildstacker.getSpawnerAmount((CreatureSpawner) loc.getBlock().getState());
                    }
                    value += temp;
                } else {
                    locations.remove();
                }
            } else {
                locations.remove();
            }
        }
        this.value = value;
        if (startvalue == -1) startvalue = value;
        for (Missions.Mission mission : IridiumSkyblock.getMissions().missions) {
            if (mission.type.equals(MissionType.VALUE_INCREASE)) {
                setMission(mission.name, value - startvalue);
            }
        }
        lastblocks = blocks.hashCode();
    }

    public void initBlocks() {
        this.a = Bukkit.getScheduler().scheduleSyncRepeatingTask(IridiumSkyblock.getInstance(), new Runnable() {
            int world = 0;
            double X = pos1.getX();
            double Y = 0;
            double Z = pos1.getZ();

            @Override
            public void run() {
                try {
                    for (int i = 0; i < IridiumSkyblock.getConfiguration().blocksPerTick; i++) {
                        if (X < pos2.getX()) {
                            X++;
                        } else if (Z < pos2.getZ()) {
                            X = pos1.getX();
                            Z++;
                        } else if (Y <= IridiumSkyblock.getIslandManager().getWorld().getMaxHeight()) {
                            X = pos1.getX();
                            Z = pos1.getZ();
                            Y++;
                        } else if (world <= 1 && IridiumSkyblock.getConfiguration().netherIslands) {
                            world++;
                            X = pos1.getX();
                            Y = 0;
                            Z = pos1.getZ();
                        } else {
                            Bukkit.getScheduler().cancelTask(a);
                            a = -1;
                            IridiumSkyblock.getInstance().updatingBlocks = false;
                        }
                        if (IridiumSkyblock.getInstance().updatingBlocks) {
                            if (world == 0) {
                                Location loc = new Location(IridiumSkyblock.getIslandManager().getWorld(), X, Y, Z);
                                if (Utils.isBlockValuable(loc.getBlock())) {
                                    blocks.add(loc);
                                }
                            } else if (IridiumSkyblock.getConfiguration().netherIslands) {
                                Location loc = new Location(IridiumSkyblock.getIslandManager().getNetherWorld(), X, Y, Z);
                                if (Utils.isBlockValuable(loc.getBlock())) {
                                    blocks.add(loc);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    IridiumSkyblock.getInstance().sendErrorMessage(e);
                }
            }
        }, 0, 1);
    }

    public void addWarp(Player player, Location location, String name, String password) {
        if (warps.size() < IridiumSkyblock.getUpgrades().warpUpgrade.upgrades.get(warpLevel).size) {
            warps.add(new Warp(location, name, password));
            player.sendMessage(Utils.color(IridiumSkyblock.getMessages().warpAdded.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
        } else {
            player.sendMessage(Utils.color(IridiumSkyblock.getMessages().maxWarpsReached.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
        }
    }

    public void addUser(User user) {
        if (members.size() < IridiumSkyblock.getUpgrades().memberUpgrade.upgrades.get(memberLevel).size) {

            for (String player : members) {
                User u = User.getUser(player);
                Player p = Bukkit.getPlayer(u.name);
                if (p != null) {
                    p.sendMessage(Utils.color(IridiumSkyblock.getMessages().playerJoinedYourIsland.replace("%player%", user.name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
                }
            }
            bans.remove(user.player);
            user.islandID = id;
            user.role = Role.Member;
            user.invites.clear();
            members.add(user.player);
            teleportHome(Bukkit.getPlayer(user.name));
            user.invites.clear();
        } else {
            if (Bukkit.getPlayer(user.name) != null) {
                Bukkit.getPlayer(user.name).sendMessage(Utils.color(IridiumSkyblock.getMessages().maxMemberCount.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        getMembersGUI().getInventory().clear();
        getMembersGUI().addContent();
    }

    public void removeUser(User user) {
        user.islandID = 0;
        Player player = Bukkit.getPlayer(user.name);
        spawnPlayer(player);
        player.setFlying(false);
        player.setAllowFlight(false);
        members.remove(user.player);
        user.role = Role.Visitor;
        for (String member : members) {
            User u = User.getUser(member);
            Player p = Bukkit.getPlayer(u.name);
            if (p != null) {
                p.sendMessage(Utils.color(IridiumSkyblock.getMessages().kickedMember.replace("%member%", user.name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        getMembersGUI().getInventory().clear();
        getMembersGUI().addContent();
    }

    public boolean isInIsland(Location location) {
        return (location.getX() > getPos1().getX() - 1 && location.getX() < getPos2().getX() + 1) && (location.getZ() > getPos1().getZ() - 1 && location.getZ() < getPos2().getZ() + 1);
    }

    public void init() {
        stopWatch = new StopWatch("Island "+id);
        IridiumSkyblock.timingsManager.Watch(stopWatch);
        stopWatch.Start();
        if (biome == null) biome = IridiumSkyblock.getConfiguration().defaultBiome;
        if (blocks == null) blocks = new CopyOnWriteArraySet<>();

        blocks = new CopyOnWriteArraySet<>(new HashSet<>(blocks));

        upgradeGUI = new UpgradeGUI(this);
        boosterGUI = new BoosterGUI(this);
        missionsGUI = new MissionsGUI(this);
        membersGUI = new MembersGUI(this);
        warpGUI = new WarpGUI(this);
        borderColorGUI = new BorderColorGUI(this);
        schematicSelectGUI = new SchematicSelectGUI(this);
        permissionsGUI = new PermissionsGUI(this);
        islandMenuGUI = new IslandMenuGUI(this);
        coopGUI = new CoopGUI(this);
        bankGUI = new BankGUI(this);
        biomeGUI = new BiomeGUI(this);
        failedGenerators = new HashSet<>();
        coopInvites = new HashSet<>();

        initChunks();
        boosterid = Bukkit.getScheduler().scheduleAsyncRepeatingTask(IridiumSkyblock.getInstance(), () -> {
            if (spawnerBooster > 0) spawnerBooster--;
            if (farmingBooster > 0) farmingBooster--;
            if (expBooster > 0) expBooster--;
            if (flightBooster == 1) {
                for (String player : members) {
                    Player p = Bukkit.getPlayer(player);
                    if (p != null) {
                        if (!p.hasPermission("IridiumSkyblock.Fly") && p.getGameMode().equals(GameMode.SURVIVAL)) {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                            User.getUser(p).flying = false;
                        }
                    }
                }
            }
            if (flightBooster > 0) flightBooster--;
        }, 0, 20);
        if (permissions == null) {
            permissions = new HashMap<Role, Permissions>() {{
                for (Role role : Role.values()) {
                    put(role, new Permissions());
                }
            }};
        }
        stopWatch.Checkpoint("Island initialised", false);
        stopWatch.Stop();
        sendBorder();
    }

    public void initChunks() {
        stopWatch.Checkpoint("Init chunks started", true);
        StopWatch initChunksSW = new StopWatch("Island %id init chunks task".replace("%id", id+""));
        IridiumSkyblock.timingsManager.Watch(initChunksSW);
        chunks = new ArrayList<>();
        chunkID = Bukkit.getScheduler().scheduleSyncRepeatingTask(IridiumSkyblock.getInstance(), new Runnable() {
            int X = getPos1().getChunk().getX();
            int Z = getPos1().getChunk().getZ();

            @Override
            public void run() {
                initChunksSW.Reset();
                initChunksSW.Start();
                chunks.add(IridiumSkyblock.getIslandManager().getWorld().getChunkAt(X, Z));
                if (IridiumSkyblock.getConfiguration().netherIslands)
                    chunks.add(IridiumSkyblock.getIslandManager().getNetherWorld().getChunkAt(X, Z));
                X++;
                if (X > getPos2().getChunk().getX()) {
                    X = getPos1().getChunk().getX();
                    Z++;
                    if (Z > getPos2().getChunk().getZ()) {
                        Bukkit.getScheduler().cancelTask(chunkID);
                        chunkID = -1;
                    }
                }
                initChunksSW.Stop();
            }
        }, 0, 5);
    }

    public void generateIsland() {
        StopWatch genSW = new StopWatch("Island %id gen".replace("%id", id+""));
        IridiumSkyblock.timingsManager.Watch(genSW);
        genSW.Start();
        for (String player : members) {
            User user = User.getUser(player);
            Player p = Bukkit.getPlayer(user.name);
            if (p != null) {
                p.sendMessage(Utils.color(IridiumSkyblock.getMessages().regenIsland.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        genSW.Checkpoint("Start intensive", true);
        deleteBlocks();
        genSW.Checkpoint("Remove existing blocks", true);
        killEntities();
        genSW.Checkpoint("Kill all entitys", true);
        pasteSchematic();
        genSW.Checkpoint("Paste island", true);
        clearInventories();
        genSW.Checkpoint("Clear inventories", true);
        genSW.Stop();
    }

    public void pasteSchematic() {
        for (Schematics.FakeSchematic fakeSchematic : IridiumSkyblock.getInstance().schems.keySet()) {
            if (fakeSchematic.name.equals(schematic)) {
                blocks.addAll(IridiumSkyblock.getInstance().schems.get(fakeSchematic).pasteSchematic(getCenter().clone(), this));
                Island island = this;
                if (IridiumSkyblock.getConfiguration().debugSchematics) {
                    File schematicFolder = new File(IridiumSkyblock.getInstance().getDataFolder(), "schematics");
                    try {
                        Schematic.debugSchematic(new File(schematicFolder, fakeSchematic.name));
                        if (IridiumSkyblock.getConfiguration().netherIslands)
                            Schematic.debugSchematic(new File(schematicFolder, fakeSchematic.netherisland));
                    } catch (IOException e) {
                    }
                }
                Location center = getCenter().clone();
                if (IridiumSkyblock.getConfiguration().netherIslands) {
                    center.setWorld(IridiumSkyblock.getIslandManager().getNetherWorld());
                    blocks.addAll(IridiumSkyblock.getInstance().netherschems.get(fakeSchematic).pasteSchematic(center, island));
                }
            }
        }
    }

    public void clearInventories() {
        if (IridiumSkyblock.getConfiguration().clearInventories) {
            for (String player : members) {
                User user = User.getUser(player);
                if (Bukkit.getPlayer(user.name) != null) Bukkit.getPlayer(user.name).getInventory().clear();
            }
        }
    }

    public void teleportHome(Player p) {
        if (isBanned(User.getUser(p)) && !members.contains(p.getUniqueId().toString())) {
            p.sendMessage(Utils.color(IridiumSkyblock.getMessages().bannedFromIsland.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            return;
        }
        if (getSchematic() == null) {
            User u = User.getUser(p);
            if (u.getIsland().equals(this)) {
                if (IridiumSkyblock.getInstance().schems.size() == 1) {
                    for (Schematics.FakeSchematic schematic : IridiumSkyblock.getInstance().schems.keySet()) {
                        setSchematic(schematic.name);
                    }
                } else {
                    p.openInventory(getSchematicSelectGUI().getInventory());
                }
            }
            return;
        }
        p.setFallDistance(0);
        if (members.contains(p.getUniqueId().toString())) {
            p.sendMessage(Utils.color(IridiumSkyblock.getMessages().teleportingHome.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
        } else {
            p.sendMessage(Utils.color(IridiumSkyblock.getMessages().visitingIsland.replace("%player%", User.getUser(owner).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            for (String pl : members) {
                Player player = Bukkit.getPlayer(User.getUser(pl).name);
                if (player != null) {
                    p.sendMessage(Utils.color(IridiumSkyblock.getMessages().visitedYourIsland.replace("%player%", p.getName()).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
                }
            }
        }
        if (Utils.isSafe(getHome(), this)) {
            p.teleport(getHome());
            sendBorder(p);
        } else {

            Location loc = Utils.getNewHome(this, this.home);
            if (loc != null) {
                this.home = loc;
                p.teleport(this.home);
                sendBorder(p);
            } else {
                generateIsland();
                teleportHome(p);
                sendBorder(p);
            }
        }
    }

    public void teleportNetherHome(Player p) {
        if (isBanned(User.getUser(p)) && !members.contains(p.getUniqueId().toString())) {
            p.sendMessage(Utils.color(IridiumSkyblock.getMessages().bannedFromIsland.replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            return;
        }
        if (getSchematic() == null) {
            User u = User.getUser(p);
            if (u.getIsland().equals(this)) {
                if (IridiumSkyblock.getInstance().schems.size() == 1) {
                    for (Schematics.FakeSchematic schematic : IridiumSkyblock.getInstance().schems.keySet()) {
                        setSchematic(schematic.name);
                    }
                } else {
                    p.openInventory(getSchematicSelectGUI().getInventory());
                }
            }
            return;
        }
        p.setFallDistance(0);
        if (Utils.isSafe(getNetherhome(), this)) {
            p.teleport(getNetherhome());
            sendBorder(p);
        } else {

            Location loc = Utils.getNewHome(this, getNetherhome());
            if (loc != null) {
                this.netherhome = loc;
                p.teleport(this.netherhome);
                sendBorder(p);
            } else {
                generateIsland();
                teleportNetherHome(p);
                sendBorder(p);
            }
        }
    }

    public void delete() {
        Bukkit.getPluginManager().callEvent(new IslandDeleteEvent(this));

        Bukkit.getScheduler().cancelTask(getMembersGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getBoosterGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getMissionsGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getUpgradeGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getWarpGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getPermissionsGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getIslandMenuGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getCoopGUI().scheduler);
        Bukkit.getScheduler().cancelTask(getBankGUI().scheduler);
        if (chunkID != -1) Bukkit.getScheduler().cancelTask(chunkID);
        permissions.clear();
        if (a != -1) Bukkit.getScheduler().cancelTask(a);
        deleteBlocks();
        clearInventories();
        spawnPlayers();
        killEntities();
        for (String player : members) {
            User.getUser(player).islandID = 0;
            if (Bukkit.getPlayer(player) != null) Bukkit.getPlayer(player).closeInventory();
        }
        hideBorder();
        this.owner = null;
        this.pos1 = null;
        this.pos2 = null;
        this.members = null;
        this.chunks = null;
        this.center = null;
        this.home = null;
        IridiumSkyblock.getIslandManager().islands.remove(this.id);
        this.id = 0;
        IridiumSkyblock.getInstance().saveConfigs();
        Bukkit.getScheduler().cancelTask(boosterid);
        boosterid = -1;
    }

    public void removeBan(User user) {
        if (bans == null) bans = new HashSet<>();
        bans.remove(user.player);
    }

    public void addBan(User user) {
        if (bans == null) bans = new HashSet<>();
        bans.add(user.player);
    }

    public void removeVote(User user) {
        if (votes == null) votes = new HashSet<>();
        votes.remove(user.player);
    }

    public void addVote(User user) {
        if (votes == null) votes = new HashSet<>();
        votes.add(user.player);
    }

    public boolean hasVoted(User user) {
        if (votes == null) votes = new HashSet<>();
        return votes.contains(user.player);
    }

    public int getVotes() {
        if (votes == null) votes = new HashSet<>();
        return votes.size();
    }

    public boolean isBanned(User user) {
        if (bans == null) bans = new HashSet<>();
        return bans.contains(user.player);
    }

    public void addCoop(Island island) {
        if (coop == null) coop = new HashSet<>();
        for (String member : island.getMembers()) {
            Player pl = Bukkit.getPlayer(User.getUser(member).name);
            if (pl != null) {
                pl.sendMessage(Utils.color(IridiumSkyblock.getMessages().coopGiven.replace("%player%", User.getUser(owner).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        for (String member : getMembers()) {
            Player pl = Bukkit.getPlayer(User.getUser(member).name);
            if (pl != null) {
                pl.sendMessage(Utils.color(IridiumSkyblock.getMessages().coopAdded.replace("%player%", User.getUser(island.getOwner()).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        coop.add(island.id);
        if (island.coop == null) island.coop = new HashSet<>();
        island.coop.add(id);
    }

    public void inviteCoop(Island island) {
        if (coopInvites == null) coopInvites = new HashSet<>();
        coopInvites.add(island.getId());
        for (String member : getMembers()) {
            Player pl = Bukkit.getPlayer(User.getUser(member).name);
            if (pl != null) {
                BaseComponent[] components = TextComponent.fromLegacyText(Utils.color(IridiumSkyblock.getMessages().coopInvite.replace("%player%", User.getUser(island.getOwner()).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));

                ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/is coop " + User.getUser(island.getOwner()).name);
                HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to coop players island!").create());
                for (BaseComponent component : components) {
                    component.setClickEvent(clickEvent);
                    component.setHoverEvent(hoverEvent);
                }
                pl.getPlayer().spigot().sendMessage(components);
            }
        }
    }

    public void removeCoop(Island island) {
        if (coop == null) coop = new HashSet<>();
        coop.remove(island.id);
        if (island.coop == null) island.coop = new HashSet<>();
        island.coop.remove(id);
        for (String member : island.getMembers()) {
            Player pl = Bukkit.getPlayer(User.getUser(member).name);
            if (pl != null) {
                pl.sendMessage(Utils.color(IridiumSkyblock.getMessages().coopTaken.replace("%player%", User.getUser(owner).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        for (String member : getMembers()) {
            Player pl = Bukkit.getPlayer(User.getUser(member).name);
            if (pl != null) {
                pl.sendMessage(Utils.color(IridiumSkyblock.getMessages().coopTaken.replace("%player%", User.getUser(island.getOwner()).name).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));
            }
        }
        getCoopGUI().getInventory().clear();
        getCoopGUI().addContent();
        island.getCoopGUI().getInventory().clear();
        island.getCoopGUI().addContent();
    }

    public void removeCoop(int id) {
        if (coop == null) coop = new HashSet<>();
        coop.remove(id);
    }

    public boolean isCoop(Island island) {
        if (coop == null) coop = new HashSet<>();
        if (island == null) return false;
        return coop.contains(island.id);
    }

    public HashSet<Integer> getCoop() {
        if (coop == null) coop = new HashSet<>();
        return coop;
    }

    public void spawnPlayers() {
        for (Chunk c : chunks) {
            for (Entity e : c.getEntities()) {
                if (e instanceof Player) {
                    spawnPlayer((Player) e);
                }
            }
        }
    }

    public void spawnPlayer(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("EssentialsSpawn")) {
            EssentialsSpawn essentialsSpawn = (EssentialsSpawn) Bukkit.getPluginManager().getPlugin("EssentialsSpawn");
            Essentials essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
            player.teleport(essentialsSpawn.getSpawn(essentials.getUser(player).getGroup()));
        } else {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    public void setBiome(Biome biome) {
        this.biome = biome;
        for (double X = getPos1().getX(); X <= getPos2().getX(); X++) {
            for (double Z = getPos1().getZ(); Z <= getPos2().getZ(); Z++) {
                IridiumSkyblock.getIslandManager().getWorld().setBiome((int) X, (int) Z, biome);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Chunk c : chunks) {
                if (c.getWorld().equals(IridiumSkyblock.getIslandManager().getWorld())) {
                    NMSUtils.sendChunk(p, c);
                }
            }
        }
    }

    public void deleteBlocks() {
        blocks.clear();
        for (int X = (int)getPos1().getX(); X <= getPos2().getX(); X++) {
            for (int Y = 0; Y <= IridiumSkyblock.getIslandManager().getWorld().getMaxHeight(); Y++) {
                for (int Z = (int)getPos1().getZ(); Z <= getPos2().getZ(); Z++) {
                    //Block b = new Location(IridiumSkyblock.getIslandManager().getWorld(), X, Y, Z).getBlock();
                    //if (b.getState() instanceof Chest) {
                    //    ((Chest) b.getState()).getBlockInventory().clear();
                    //}
                    SetBlockFast(IridiumSkyblock.getIslandManager().getWorld(), X, Y, Z, 0, (byte)0);
                }
            }
        }
        if (IridiumSkyblock.getConfiguration().netherIslands) {
            for (int X = (int)getPos1().getX(); X <= getPos2().getX(); X++) {
                for (int Y = 0; Y <= IridiumSkyblock.getIslandManager().getNetherWorld().getMaxHeight(); Y++) {
                    for (int Z = (int)getPos1().getZ(); Z <= getPos2().getZ(); Z++) {
                        //Block b = new Location(IridiumSkyblock.getIslandManager().getNetherWorld(), X, Y, Z).getBlock();
                        //if (b.getState() instanceof Chest) {
                        //    ((Chest) b.getState()).getBlockInventory().clear();
                        //}
                        SetBlockFast(IridiumSkyblock.getIslandManager().getNetherWorld(), X, Y, Z, 0, (byte)0);
                    }
                }
            }
        }
    }

    public static void SetBlockFast(World world, int x, int y, int z, int blockId, byte data){
        net.minecraft.server.v1_8_R3.World w = ((CraftWorld) world).getHandle();
        net.minecraft.server.v1_8_R3.Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
        BlockPosition bp = new BlockPosition(x, y, z);
        int combined = blockId + (data << 12);
        IBlockData ibd = net.minecraft.server.v1_8_R3.Block.getByCombinedId(combined);
        w.setTypeAndData(bp, ibd, 2);
        chunk.a(bp, ibd);
    }

    public void killEntities() {
        for (Chunk c : chunks) {
            for (Entity e : c.getEntities()) {
                if (isInIsland(e.getLocation())) {
                    if (e.getType() != EntityType.PLAYER) {
                        e.remove();
                    }
                }
            }
        }
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public Location getCenter() {
        return center;
    }

    public Location getHome() {
        return home;
    }

    public Location getNetherhome() {
        if (netherhome == null) {
            netherhome = getHome().clone();
            netherhome.setWorld(IridiumSkyblock.getIslandManager().getNetherWorld());
        }
        return netherhome;
    }

    public void setHome(Location home) {
        this.home = home;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(OfflinePlayer owner) {

        for (String player : members) {
            User user = User.getUser(player);
            Player p = Bukkit.getPlayer(user.name);
            if (p != null) {
                p.sendMessage(Utils.color(IridiumSkyblock.getMessages().transferdOwnership.replace("%player%", owner.getName()).replace("%prefix%", IridiumSkyblock.getConfiguration().prefix)));

            }
        }
        User.getUser(getOwner()).role = Role.CoOwner;
        this.owner = owner.getUniqueId().toString();
        User.getUser(getOwner()).role = Role.Owner;
    }

    public int getId() {
        return id;
    }

    public BiomeGUI getBiomeGUI() {
        return biomeGUI;
    }

    public BankGUI getBankGUI() {
        return bankGUI;
    }

    public CoopGUI getCoopGUI() {
        return coopGUI;
    }

    public UpgradeGUI getUpgradeGUI() {
        return upgradeGUI;
    }

    public BoosterGUI getBoosterGUI() {
        return boosterGUI;
    }

    public SchematicSelectGUI getSchematicSelectGUI() {
        return schematicSelectGUI;
    }

    public MissionsGUI getMissionsGUI() {
        return missionsGUI;
    }

    public MembersGUI getMembersGUI() {
        return membersGUI;
    }

    public WarpGUI getWarpGUI() {
        return warpGUI;
    }

    public PermissionsGUI getPermissionsGUI() {
        return permissionsGUI;
    }

    public IslandMenuGUI getIslandMenuGUI() {
        return islandMenuGUI;
    }

    public BorderColorGUI getBorderColorGUI() {
        return borderColorGUI;
    }

    public int getSpawnerBooster() {
        return spawnerBooster;
    }

    public void setSpawnerBooster(int spawnerBooster) {
        this.spawnerBooster = spawnerBooster;
    }

    public int getFarmingBooster() {
        return farmingBooster;
    }

    public void setFarmingBooster(int farmingBooster) {
        this.farmingBooster = farmingBooster;
    }

    public int getExpBooster() {
        return expBooster;
    }

    public void setExpBooster(int expBooster) {
        this.expBooster = expBooster;
    }

    public int getFlightBooster() {
        return flightBooster;
    }

    public void setFlightBooster(int flightBooster) {
        this.flightBooster = flightBooster;
    }

    public int getCrystals() {
        return crystals;
    }

    public void setCrystals(int crystals) {
        this.crystals = crystals;
    }

    public HashSet<String> getMembers() {
        return members;
    }

    public int getSizeLevel() {
        return sizeLevel;
    }

    public void setSizeLevel(int sizeLevel) {
        this.sizeLevel = sizeLevel;

        pos1 = getCenter().clone().subtract(IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size / 2.00, 0, IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size / 2.00);
        pos2 = getCenter().clone().add(IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size / 2.00, 0, IridiumSkyblock.getUpgrades().sizeUpgrade.upgrades.get(sizeLevel).size / 2.00);
        sendBorder();
    }

    public int getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(int memberLevel) {
        this.memberLevel = memberLevel;
    }

    public int getWarpLevel() {
        return warpLevel;
    }

    public void setWarpLevel(int warpLevel) {
        this.warpLevel = warpLevel;
    }

    public int getOreLevel() {
        return oreLevel;
    }

    public void setOreLevel(int oreLevel) {
        this.oreLevel = oreLevel;
    }

    public void removeWarp(Warp warp) {
        warps.remove(warp);
    }

    public List<Warp> getWarps() {
        return warps;
    }

    public int getValue() {
        return value;
    }

    public NMSUtils.Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(NMSUtils.Color borderColor) {
        this.borderColor = borderColor;
    }

    public boolean isVisit() {
        return visit;
    }

    public void setVisit(boolean visit) {
        this.visit = visit;
    }

    public String getSchematic() {
        return schematic;
    }

    public void setSchematic(String schematic) {
        this.schematic = schematic;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        if (name == null) name = User.getUser(getOwner()).name;
        return name;
    }

    public Biome getBiome() {
        return biome;
    }
}
