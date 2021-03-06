package me.tomski.prophunt;

import me.tomski.arenas.Arena;
import me.tomski.arenas.ArenaManager;
import me.tomski.blocks.SolidBlock;
import me.tomski.bungee.Pinger;
import me.tomski.classes.HiderClass;
import me.tomski.classes.SeekerClass;
import me.tomski.language.LanguageManager;
import me.tomski.language.MessageBank;
import me.tomski.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.logging.Level;

public class GameManager {

    public static boolean gameStatus = false;
    public static boolean isHosting = false;
    public static boolean canHost = true;

    public static int playersToStartGame = 0;

    public static String firstSeeker = null;

    public static List<String> playersWaiting = new ArrayList<String>();
    public static List<String> hiders = new ArrayList<String>();
    public static List<String> seekers = new ArrayList<String>();
    public static List<String> spectators = new ArrayList<String>();
    public static List<String> playerstoundisguise = new ArrayList<String>();
    public static List<String> playersQuit = new ArrayList<String>();

    public static Map<Player, Integer> seekerLives = new HashMap<Player, Integer>();
    public static int seekerLivesAmount;

    public static HiderClass hiderCLASS;
    public static SeekerClass seekerCLASS;

    private static int SCOREBOARDTASKID = 0;

    public static int interval;
    public static int starting_time;
    public static double seeker_damage;
    public static int timeleft;
    public static int time_reward;
    public static int TIMERID;
    public static boolean chooseNewSeeker;

    private int TRACKERID;

    public static GameTimer GT;
    public static boolean automatic = false;
    public static boolean dedicated = false;

    private PropHunt plugin;
    private LobbyThread LT;
    private int DETRACKERID;
    public static SeekerDelay sd;


    public static PHScoreboard SB;

    public static Arena currentGameArena = null;
    public static boolean blowDisguises;
    public static boolean crouchBlockLock = false;

    public static boolean usingSolidBlock;
    public static int solidBlockTime;

    public static int seekerDelayTime;
    public static boolean usingHitmarkers;
    public static boolean usingHitsounds;
    public static boolean blindSeeker;
    public static boolean autoRespawn;
    public static boolean useSideStats;
    public static int lobbyTime;
    public static int currentLobbyTime = 0;

    public GameManager(PropHunt plugin) {
        this.plugin = plugin;
        this.plugin.setupClasses();
    }

    public void hostGame(Player host, Arena arena) {

        if (automatic) {
            if (!checkReady(arena)) {
                plugin.getLogger().log(Level.WARNING, "Cant Host Arena not setup");
                return;
            }
            if (host != null) {
                PropHuntMessaging.sendMessage(host, MessageBank.HOSTING_AUTO_CANT_HOST.getMsg());
                return;
            }
            isHosting = true;
            currentGameArena = arena;
            if (dedicated) {
                String msg = MessageBank.HOST_AUTO_BROADCAST_DEDI.getMsg();
                msg = LanguageManager.regex(msg, "\\{arena\\}", arena.getArenaName());
                PropHuntMessaging.broadcastMessage(msg);

            } else {
                String msg = MessageBank.HOST_AUTO_BROADCAST.getMsg();
                msg = LanguageManager.regex(msg, "\\{arena\\}", arena.getArenaName());
                PropHuntMessaging.broadcastMessage(msg);
            }
            return;
        }
        if (gameStatus) {
            PropHuntMessaging.sendMessage(host, MessageBank.GAME_ALREADY_HOSTED.getMsg());
            return;
        }
        if (checkReady(arena)) {
            if (isHosting) {
                PropHuntMessaging.sendMessage(host, MessageBank.GAME_ALREADY_HOSTED.getMsg());
                return;
            }
            if (!canHost) {
                PropHuntMessaging.sendMessage(host, MessageBank.GAME_CANT_HOST.getMsg());
                return;
            }
            isHosting = true;
            PropHuntMessaging.sendMessage(host, MessageBank.GAME_HOST.getMsg());
            String msg = MessageBank.BROADCAST_HOST.getMsg();
            msg = LanguageManager.regex(msg, "\\{arena\\}", arena.getArenaName());
            msg = LanguageManager.regex(msg, "\\{host\\}", host.getName());

            PropHuntMessaging.broadcastMessage(msg);
            currentGameArena = arena;
        } else {
            PropHuntMessaging.sendMessage(host, MessageBank.ARENA_NOT_READY.getMsg());
            return;
        }
    }

    public boolean checkReady(Arena a) {
        if (a == null) {
            return false;
        }
        if (a.getExitSpawn() != null && a.getHiderSpawn() != null && a.getLobbySpawn() != null && a.getSeekerSpawn() != null && a.getSpectatorSpawn() != null) {
            return true;
        }
        return false;
    }

    public void startGame(Player p) {
        if (!(playersWaiting.size() >= playersToStartGame)) {
            if (p != null) {
                String msg = MessageBank.NOT_ENOUGH_PLAYERS.getMsg();
                msg = LanguageManager.regex(msg, "\\{playeramount\\}", String.valueOf(playersToStartGame));
                PropHuntMessaging.sendMessage(p, msg);
            } else {
                if (automatic) {
                    hostGame(null, ArenaManager.getNextInRotation());
                    if (dedicated) {
                        for (Player pe : plugin.getServer().getOnlinePlayers()) {
                            addPlayerToGame(pe.getName());
                        }
                    }
                }
            }
            return;
        }

        GT = new GameTimer(this, plugin, seeker_damage, interval, starting_time, plugin.SBS);
        TIMERID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, GT, 20, 20);
        GT.ID = TIMERID;
        timeleft = starting_time;

        if (usingSolidBlock) {
            SolidBlockTracker SBT = new SolidBlockTracker(plugin);
            TRACKERID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, SBT, 0L, 20L);

            DeSolidifyThread DST = new DeSolidifyThread(plugin);
            DETRACKERID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, DST, 0L, 2L);

        }

        freshPlayers();
        chooseSeekerAndSortPlayers();
        teleportPlayersStart();
        teleportSeekerStart(plugin.getServer().getPlayer(firstSeeker));
        seekerLives.put(plugin.getServer().getPlayer(firstSeeker), seekerLivesAmount);
        if (seekerDelayTime != 0) {
            sd = new SeekerDelay(plugin.getServer().getPlayer(firstSeeker), seekerDelayTime, plugin);
            int delayID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, sd, 0L, 20L);
            sd.setID(delayID);

        } else {
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

                @Override
                public void run() {
                    plugin.SBS.addPlayerToGame(plugin, plugin.getServer().getPlayer(firstSeeker));
                }
            }, 20L);
        }

        givePlayersLoadOuts(currentGameArena);
        disguisePlayers(currentGameArena);

        gameStatus = true;
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

            @Override
            public void run() {
                for (String hider : hiders) {
                    plugin.SBS.addPlayerToGame(plugin, plugin.getServer().getPlayer(hider));
                }
            }
        }, 20L);

        if (PropHunt.usingTABAPI) {
            setupScoreBoard();
        }
    }

    private void setupScoreBoard() {
        System.out.print("Setting UP Scoreboard");
        SB = new PHScoreboard(plugin);
        for (String name : seekers) {
            if (plugin.getServer().getPlayer(name) != null) {
                SB.updateTab(plugin.getServer().getPlayer(name));
            }
        }
        for (String name : spectators) {
            if (plugin.getServer().getPlayer(name) != null) {
                SB.updateTab(plugin.getServer().getPlayer(name));
            }
        }
        for (String name : hiders) {
            if (plugin.getServer().getPlayer(name) != null) {
                SB.updateTab(plugin.getServer().getPlayer(name));
            }
        }

        SCOREBOARDTASKID = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {

            @Override
            public void run() {
                for (String name : seekers) {
                    if (plugin.getServer().getPlayer(name) != null) {
                        SB.updateTab(plugin.getServer().getPlayer(name));
                    }
                }
                for (String name : spectators) {
                    if (plugin.getServer().getPlayer(name) != null) {
                        SB.updateTab(plugin.getServer().getPlayer(name));
                    }
                }
                for (String name : hiders) {
                    if (plugin.getServer().getPlayer(name) != null) {
                        SB.updateTab(plugin.getServer().getPlayer(name));
                    }
                }
            }
        }, 20 * 5L, 20 * 5L);
    }

    private void givePlayersLoadOuts(Arena a) {
        for (String seek : seekers) {
            if (plugin.getServer().getPlayer(seek) != null) {
                Player p = plugin.getServer().getPlayer(seek);
                ArenaManager.arenaConfigs.get(a).getArenaSeekerClass().givePlayer(p);
                if (DisguiseManager.loadouts.containsKey(p)) {
                    DisguiseManager.loadouts.get(p).giveLoadout();
                    DisguiseManager.loadouts.remove(p);
                }
            }
        }
        for (String hider : hiders) {
            if (plugin.getServer().getPlayer(hider) != null) {
                Player p = plugin.getServer().getPlayer(hider);
                ArenaManager.arenaConfigs.get(a).getArenaHiderClass().givePlayer(p);
                if (DisguiseManager.loadouts.containsKey(p)) {
                    DisguiseManager.loadouts.get(p).giveLoadout();
                    DisguiseManager.loadouts.remove(p);
                }
            }
        }
    }

    private void freshPlayers() {
        for (String s : playersWaiting) {
            if (plugin.getServer().getPlayer(s) != null) {
                if (plugin.getServer().getPlayer(s).getGameMode().equals(GameMode.CREATIVE)) {
                    plugin.getServer().getPlayer(s).setGameMode(GameMode.SURVIVAL);
                }
                PlayerManagement.gameStartPlayer(plugin.getServer().getPlayer(s));
            }
        }
    }

    private void disguisePlayers(Arena a) {
        for (String s : hiders) {
            if (seekers.contains(s) || firstSeeker.equals(s)) {
                continue;
            }
            if (plugin.getServer().getPlayer(s) != null) {
                plugin.dm.randomDisguise(plugin.getServer().getPlayer(s), ArenaManager.arenaConfigs.get(a));
            }
        }

    }

    private void chooseSeekerAndSortPlayers() {
        int playersize = playersWaiting.size();
        hiders.clear();
        seekers.clear();
        Random rnd = new Random();
        int randomnum = rnd.nextInt(playersize);
        String seeker = playersWaiting.get(randomnum);
        firstSeeker = seeker;
        seekers.add(seeker);
        playersWaiting.remove(seeker);
        for (String hider : playersWaiting) {
            if (hider.equals(firstSeeker)) {
                continue;
            }
            hiders.add(hider);
        }
        playersWaiting.clear();
        String msg = MessageBank.BROADCAST_FIRST_SEEKER.getMsg();
        msg = LanguageManager.regex(msg, "\\{seeker\\}", seeker);
        PropHuntMessaging.broadcastMessageToPlayers(hiders, seekers, msg);
    }

    public void endGame(Reason reason, boolean shutdown) throws IOException {
        plugin.getServer().getScheduler().cancelTask(TIMERID);
        String bcreason = broadcastEndReason(reason);
        PropHuntMessaging.broadcastMessage(bcreason);
        for (final String hider : hiders) {
            if (plugin.getServer().getPlayer(hider) != null) {
                plugin.showPlayer(plugin.getServer().getPlayer(hider));
                teleportToExit(plugin.getServer().getPlayer(hider), false);
                PlayerManagement.gameRestorePlayer(plugin.getServer().getPlayer(hider));
                if (PropHunt.usingTABAPI) {
                    SB.removeTab(plugin.getServer().getPlayer(hider));
                }
                if (useSideStats) {
                    plugin.SBS.removeScoreboard(plugin, plugin.getServer().getPlayer(hider));
                }
                if (shutdown) {
                    if (plugin.getServer().getPlayer(hider) != null) {
                        if (plugin.dm.isDisguised(plugin.getServer().getPlayer(hider))) {
                            plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(hider));
                        }
                    }
                } else {
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (plugin.getServer().getPlayer(hider) != null) {
                                if (plugin.dm.isDisguised(plugin.getServer().getPlayer(hider))) {
                                    plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(hider));
                                }
                            }
                        }
                    }, 20L);
                }
            }
            playerstoundisguise.add(hider);
        }

        for (final String seeker : seekers) {
            if (plugin.getServer().getPlayer(seeker) != null) {
                plugin.showPlayer(plugin.getServer().getPlayer(seeker));
                teleportToExit(plugin.getServer().getPlayer(seeker), false);
                PlayerManagement.gameRestorePlayer(plugin.getServer().getPlayer(seeker));
                if (PropHunt.usingTABAPI) {
                    SB.removeTab(plugin.getServer().getPlayer(seeker));
                }
                if (useSideStats) {
                    plugin.SBS.removeScoreboard(plugin, plugin.getServer().getPlayer(seeker));
                }
                if (shutdown) {
                    if (plugin.getServer().getPlayer(seeker) != null) {
                        if (plugin.dm.isDisguised(plugin.getServer().getPlayer(seeker))) {
                            plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(seeker));
                        }
                    }
                } else {
                    plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            if (plugin.getServer().getPlayer(seeker) != null) {
                                if (plugin.dm.isDisguised(plugin.getServer().getPlayer(seeker))) {
                                    plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(seeker));
                                }
                            }
                        }
                    }, 20L);
                }

            }
            playerstoundisguise.add(seeker);

        }

        for (String spectator : spectators) {
            if (plugin.getServer().getPlayer(spectator) != null) {
                teleportToExit(plugin.getServer().getPlayer(spectator), false);
                PlayerManagement.gameRestorePlayer(plugin.getServer().getPlayer(spectator));
                if (PropHunt.usingTABAPI) {
                    SB.removeTab(plugin.getServer().getPlayer(spectator));
                }
                if (useSideStats) {
                    plugin.SBS.removeScoreboard(plugin, plugin.getServer().getPlayer(spectator));
                }
                if (plugin.dm.isDisguised(plugin.getServer().getPlayer(spectator))) {
                    plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(spectator));
                }

            }
            playerstoundisguise.add(spectator);

        }


        for (String player : playerstoundisguise) {
            if (plugin.getServer().getPlayer(player) != null) {
                if (plugin.dm.isDisguised(plugin.getServer().getPlayer(player))) {
                    plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(player));
                }
            }
        }


        for (String player : playersWaiting) {
            if (plugin.getServer().getPlayer(player) != null) {
                teleportToExit((plugin.getServer().getPlayer(player)), false);
            }
            if (useSideStats) {
                plugin.SBS.removeScoreboard(plugin, plugin.getServer().getPlayer(player));
            }
        }


        if (SCOREBOARDTASKID != 0) {
            plugin.getServer().getScheduler().cancelTask(SCOREBOARDTASKID);
        }

        if (usingSolidBlock) {
            SolidBlockTracker.solidBlocks.clear();
            SolidBlockTracker.currentLocation.clear();
            SolidBlockTracker.movementTracker.clear();
            plugin.getServer().getScheduler().cancelTask(TRACKERID);
            plugin.getServer().getScheduler().cancelTask(DETRACKERID);
        }


        playerstoundisguise.clear();
        playersWaiting.clear();
        hiders.clear();
        seekers.clear();
        spectators.clear();
        firstSeeker = null;
        gameStatus = false;
        isHosting = false;
        timeleft = 0;
        PHScoreboard.disguisesBlown = false;
        SideBarStats.playerBoards.clear();
        for (SolidBlock sb : SolidBlockTracker.solidBlocks.values()) {
            try {
                sb.unSetBlock(plugin);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        if (BungeeSettings.usingPropHuntSigns && BungeeSettings.kickToHub) {
            Pinger ping = new Pinger(plugin);
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                ping.connectToServer(p, BungeeSettings.hubname);
            }
        }
        PropHuntMessaging.broadcastMessage(ChatColor.GREEN + "------------------------");
        if (automatic) {
            if (AutomationSettings.runChecks(plugin)) {
                return;
            }
            hostGame(null, ArenaManager.getNextInRotation());
            if (dedicated) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    addPlayerToGameDedi(p.getName());
                }
            }
        }
    }

    private String broadcastEndReason(Reason reason) {
        String reasonmsg = "";
        switch (reason) {
            case TIME:
                reasonmsg = MessageBank.HIDERS_WON_TIME.getMsg();
                break;
            case HOSTENDED:
                reasonmsg = MessageBank.HOST_ENDED.getMsg();
                break;
            case HIDERSQUIT:
                reasonmsg = MessageBank.SEEKERS_WON_HIDERS_QUIT.getMsg();
                break;
            case SEEKERDIED:
                reasonmsg = MessageBank.HIDERS_WON_KILLS.getMsg();
                break;
            case SEEKERQUIT:
                reasonmsg = MessageBank.HIDERS_WON_SEEKERS_QUIT.getMsg();
                break;
            case SEEKERWON:
                reasonmsg = MessageBank.SEEKERS_WON.getMsg();
                break;
            case HIDERSWON:
                reasonmsg = MessageBank.HIDERS_WON.getMsg();
                break;
            default:
                break;
        }
        return reasonmsg;

    }

    public void kickPlayer(final String name, boolean logOff) throws IOException {
        if (plugin.getServer().getPlayer(name) != null) {
            teleportToExit(plugin.getServer().getPlayer(name), true);
            if (PropHunt.usingTABAPI) {
                if (SB != null) {
                    SB.removeTab(plugin.getServer().getPlayer(name));
                }
            }
            if (useSideStats) {
                plugin.SBS.removeScoreboard(plugin, plugin.getServer().getPlayer(name));
            }
            if (logOff) {
                if (plugin.dm.isDisguised(plugin.getServer().getPlayer(name))) {
                    plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(name));
                }
                PlayerManagement.gameRestorePlayer(plugin.getServer().getPlayer(name));
            } else {
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {

                    @Override
                    public void run() {
                        if (plugin.getServer().getPlayer(name) != null) {
                            if (plugin.dm.isDisguised(plugin.getServer().getPlayer(name))) {
                                plugin.dm.undisguisePlayer(plugin.getServer().getPlayer(name));
                            }
                            PlayerManagement.gameRestorePlayer(plugin.getServer().getPlayer(name));
                        }
                    }
                }, 20L);
            }
        }

        if (spectators.contains(name)) {
            spectators.remove(name);
        }
        if (playersWaiting.contains(name)) {
            playersWaiting.remove(name);
        }
        if (hiders.contains(name)) {
            hiders.remove(name);
        }
        if (seekers.contains(name)) {
            seekers.remove(name);
        }
        if (GameManager.gameStatus) {
            if (seekers.size() == 0) {
                if (GameManager.firstSeeker.equalsIgnoreCase(name)) {
                    if (plugin.GM.chooseNewSeekerMeth()) {
                        return;
                    } else {
                        endGame(Reason.SEEKERQUIT, false);
                    }
                } else {
                    endGame(Reason.SEEKERQUIT, false);
                }
                return;
            }
            if (hiders.size() == 0) {
                endGame(Reason.HIDERSQUIT, false);
                return;
            }

            checkEnd();
        }
    }

    public boolean chooseNewSeekerMeth() {
        if (GameManager.hiders.size() <= 0) {
            return false;
        }
        Random rand = new Random();
        String newSeeker = hiders.get(rand.nextInt(GameManager.hiders.size()));
        if (Bukkit.getPlayer(newSeeker) != null) {
            Player newSeekerPlayer = Bukkit.getPlayer(newSeeker);
            newSeekerPlayer.setHealth(0);
            PropHuntMessaging.broadcastMessageToPlayers(GameManager.hiders, GameManager.seekers, MessageBank.NEW_SEEKER_CHOSEN.getMsg() + newSeeker);
            return true;
        } else {
            return true;
        }
    }

    public void addPlayerToGameDedi(String name) {
        if (!safeToJoin(name)) {
            PropHuntMessaging.sendMessage(plugin.getServer().getPlayer(name), "You are not safe to teleport");
            return;
        }
        if (gameStatus) {
            plugin.SBS.addPlayerToLobby(plugin, plugin.getServer().getPlayer(name));
        } else {
            plugin.SBS.addPlayerToLobby(plugin, plugin.getServer().getPlayer(name));
        }
        if (playersWaiting.contains(name)) {
            return;
        }
        playersWaiting.add(name);
        if (plugin.getServer().getPlayer(name) != null) {
            teleportToLobby(plugin.getServer().getPlayer(name), false);
        }
        if (automatic && !gameStatus) {
            if (playersWaiting.size() >= playersToStartGame) {
                if (LT == null) {
                    LT = new LobbyThread(plugin, lobbyTime);
                }
                if (!LT.isRunning) {
                    if (dedicated) {
                        String msg = MessageBank.STARTING_IN_60_DEDI.getMsg();
                        msg = LanguageManager.regex(msg, "\\{time\\}", String.valueOf(lobbyTime));
                        PropHuntMessaging.broadcastMessage(msg);
                    } else {
                        String msg = MessageBank.STARTING_IN_60.getMsg();
                        msg = LanguageManager.regex(msg, "\\{time\\}", String.valueOf(lobbyTime));
                        PropHuntMessaging.broadcastMessage(msg);
                    }
                    LT = new LobbyThread(plugin, lobbyTime);
                    LT.isRunning = true;
                    int id = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, LT, 0L, 20L);
                    LT.setId(id);
                }
            }
        }
    }

    private boolean safeToJoin(String name) {
        Player p = plugin.getServer().getPlayer(name);
        return !p.isInsideVehicle();
    }

    public void addPlayerToGame(String name) {
        if (GameManager.useSideStats) {
            plugin.SBS.addPlayerToLobby(plugin, plugin.getServer().getPlayer(name));
        }
        if (playersWaiting.contains(name)) {
            return;
        }
        playersWaiting.add(name);
        if (plugin.getServer().getPlayer(name) != null) {
            teleportToLobby(plugin.getServer().getPlayer(name), true);
            PropHuntMessaging.broadcastMessageToPlayers(playersWaiting, seekers, name + MessageBank.PLAYER_JOIN_LOBBY.getMsg());
        }
        if (automatic && !gameStatus) {
            if (playersWaiting.size() >= playersToStartGame) {
                if (LT == null) {
                    LT = new LobbyThread(plugin, lobbyTime);
                }
                if (!LT.isRunning) {
                    if (dedicated) {
                        String msg = MessageBank.STARTING_IN_60_DEDI.getMsg();
                        msg = LanguageManager.regex(msg, "\\{time\\}", String.valueOf(lobbyTime));
                        PropHuntMessaging.broadcastMessage(msg);
                    } else {
                        String msg = MessageBank.STARTING_IN_60.getMsg();
                        msg = LanguageManager.regex(msg, "\\{time\\}", String.valueOf(lobbyTime));
                        PropHuntMessaging.broadcastMessage(msg);
                    }
                    LT = new LobbyThread(plugin, lobbyTime);
                    LT.isRunning = true;
                    int id = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, LT, 0L, 20L);
                    LT.setId(id);
                }
            }
        }
    }

    public void teleportPlayersStart() {
        currentGameArena.getHiderSpawn().getChunk().load();
        for (String s : hiders) {
            if (plugin.getServer().getPlayer(s) != null) {
                Player p = plugin.getServer().getPlayer(s);
                p.teleport(currentGameArena.getHiderSpawn());
                PropHuntMessaging.sendMessage(p, MessageBank.GAME_START_MESSAGE_HIDERS.getMsg());

            }
        }
    }

    public void teleportSeekerStart(Player p) {
        currentGameArena.getSeekerSpawn().getChunk().load();
        p.teleport(currentGameArena.getSeekerSpawn());
        PropHuntMessaging.sendMessage(p, MessageBank.GAME_START_MESSAGE_SEEKERS.getMsg());
    }

    public void teleportToSpectator(Player p) {
        p.teleport(currentGameArena.getSpectatorSpawn());
        PropHuntMessaging.sendMessage(p, MessageBank.SPECTATING.getMsg());

    }

    public void teleportToLobby(Player p, boolean message) {
        p.teleport(currentGameArena.getLobbySpawn());
        if (message) {
            PropHuntMessaging.sendMessage(p, MessageBank.JOIN_LOBBY_MESSAGE.getMsg());
        }
    }

    public void teleportToExit(Player p, boolean message) {
        p.teleport(currentGameArena.getExitSpawn());
        if (message) {
            PropHuntMessaging.sendMessage(p, MessageBank.QUIT_GAME_MESSAGE.getMsg());
        }
    }

    public void checkEnd() throws IOException {
        if (seekers.isEmpty()) {
            endGame(Reason.HIDERSWON, false);
            return;
        }
        if (hiders.isEmpty()) {
            endGame(Reason.SEEKERWON, false);
            return;
        }
    }

    public void spectateGame(Player p) {
        if (gameStatus) {
            teleportToSpectator(p);
            spectators.add(p.getName());
        }
    }

}
