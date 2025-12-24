
package pl.kukold.ranks;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main extends JavaPlugin implements Listener {

    private File playersFile, ranksFile;
    private YamlConfiguration players, ranks;

    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, String> editing = new HashMap<>();

    @Override
    public void onEnable() {
        loadFiles();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadFiles() {
        playersFile = new File(getDataFolder(), "players.yml");
        ranksFile = new File(getDataFolder(), "ranks.yml");

        try {
            if (!playersFile.exists()) {
                playersFile.getParentFile().mkdirs();
                playersFile.createNewFile();
            }
            if (!ranksFile.exists()) {
                ranksFile.getParentFile().mkdirs();
                ranksFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        players = YamlConfiguration.loadConfiguration(playersFile);
        ranks = YamlConfiguration.loadConfiguration(ranksFile);

        ensureRank("gracz", "gray");
        ensureRank("vip", "gold");
        ensureRank("admin", "red");
        ensureRank("wlasciciel", "dark_red");

        saveRanks();
    }

    private void ensureRank(String rank, String color) {
        if (!ranks.isConfigurationSection(rank)) {
            ranks.set(rank + ".color", color);
        }
    }

    private void savePlayers() {
        try { players.save(playersFile); } catch (Exception ignored) {}
    }

    private void saveRanks() {
        try { ranks.save(ranksFile); } catch (Exception ignored) {}
    }

    private String getRank(Player p) {
        return players.getString(p.getUniqueId().toString(), "gracz");
    }

    private NamedTextColor colorOf(String rank) {
        String c = ranks.getString(rank + ".color", "gray");
        return switch (c.toLowerCase()) {
            case "red" -> NamedTextColor.RED;
            case "gold" -> NamedTextColor.GOLD;
            case "yellow" -> NamedTextColor.YELLOW;
            case "green" -> NamedTextColor.GREEN;
            case "blue" -> NamedTextColor.BLUE;
            case "aqua" -> NamedTextColor.AQUA;
            case "dark_red" -> NamedTextColor.DARK_RED;
            case "dark_blue" -> NamedTextColor.DARK_BLUE;
            case "black" -> NamedTextColor.BLACK;
            case "white" -> NamedTextColor.WHITE;
            default -> NamedTextColor.GRAY;
        };
    }

    private void updateTab(Player p) {
        if (vanished.contains(p.getUniqueId())) {
            p.playerListName(Component.empty());
            return;
        }

        String rank = getRank(p);
        p.playerListName(
                Component.text("[" + rank.toUpperCase() + "] " + p.getName())
                        .color(colorOf(rank))
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (!players.contains(p.getUniqueId().toString())) {
            players.set(p.getUniqueId().toString(), "gracz");
            savePlayers();
        }

        updateTab(p);

        for (UUID v : vanished) {
            Player vp = Bukkit.getPlayer(v);
            if (vp != null) {
                p.hidePlayer(this, vp);
            }
        }
    }

    /* ================= GUI ================= */

    private void openMainGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Rangi");

        inv.setItem(2, guiItem(Material.EMERALD, "§aDodaj rangę"));
        inv.setItem(4, guiItem(Material.BOOK, "§eZarządzaj rangami"));
        inv.setItem(6, guiItem(Material.BARRIER, "§cUsuń rangę"));

        p.openInventory(inv);
    }

    private void openColorGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Kolor rangi");

        Material[] dyes = {
                Material.RED_DYE, Material.ORANGE_DYE, Material.YELLOW_DYE,
                Material.LIME_DYE, Material.GREEN_DYE, Material.CYAN_DYE,
                Material.LIGHT_BLUE_DYE, Material.BLUE_DYE, Material.PURPLE_DYE,
                Material.BLACK_DYE, Material.WHITE_DYE
        };

        for (int i = 0; i < dyes.length; i++) {
            inv.setItem(i, guiItem(dyes[i], dyes[i].name().replace("_DYE", "")));
        }

        p.openInventory(inv);
    }

    private void openEditGUI(Player p, String rank) {
        Inventory inv = Bukkit.createInventory(null, 9, "§8Edytuj " + rank);

        inv.setItem(3, guiItem(Material.PAINTING, "§eZmień kolor"));
        inv.setItem(4, guiItem(Material.NAME_TAG, "§eZmień nazwę"));
        inv.setItem(5, guiItem(Material.BARRIER, "§cUsuń rangę"));

        editing.put(p.getUniqueId(), rank);
        p.openInventory(inv);
    }

    private ItemStack guiItem(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name));
        it.setItemMeta(meta);
        return it;
    }

  /* ================ EVENTS ================ */

@EventHandler
public void onInventoryClick(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) return;
    if (e.getCurrentItem() == null) return;

    String title = e.getView().getTitle();
    e.setCancelled(true);

    if (title.equals("§8Rangi")) {

        switch (e.getCurrentItem().getType()) {

            case EMERALD -> {
                p.closeInventory();

                ConversationFactory factory = new ConversationFactory(this)
                        .withFirstPrompt(new StringPrompt() {
                            @Override
                            public String getPromptText(ConversationContext context) {
                                return "§aWpisz nazwę nowej rangi:";
                            }

                            @Override
                            public Prompt acceptInput(ConversationContext context, String input) {
                                String rank = input.toLowerCase();

                                if (ranks.isConfigurationSection(rank)) {
                                    p.sendMessage("§cTaka ranga już istnieje!");
                                    return END_OF_CONVERSATION;
                                }

                                ensureRank(rank, "gray");
                                saveRanks();

                                editing.put(p.getUniqueId(), rank);
                                openColorGUI(p);
                                return END_OF_CONVERSATION;
                            }
                        })
                        .withLocalEcho(false);

                factory.buildConversation(p).begin();
            }

            case BOOK -> {
                p.closeInventory();
                p.sendMessage("§eKliknij rangę:");
                for (String r : ranks.getKeys(false)) {

                    String display = ranks.getString(r + ".display", r);

                    p.sendMessage(
                            Component.text("[" + display.toUpperCase() + "]")
                                    .color(colorOf(r))
                                    .clickEvent(ClickEvent.runCommand("/ranga edit " + r))
                    );
                }
            }

            case BARRIER -> {
                p.closeInventory();
                p.sendMessage("§cKliknij rangę do usunięcia:");
                for (String r : ranks.getKeys(false)) {

                    String display = ranks.getString(r + ".display", r);

                    p.sendMessage(
                            Component.text("[" + display.toUpperCase() + "]")
                                    .color(colorOf(r))
                                    .clickEvent(ClickEvent.runCommand("/ranga delete " + r))
                    );
                }
            }
        }

    } else if (title.equals("§8Kolor rangi")) {

        String rank = editing.get(p.getUniqueId());
        if (rank == null) return;

        String color = e.getCurrentItem().getType().name()
                .replace("_DYE", "")
                .toLowerCase();

        ranks.set(rank + ".color", color);
        saveRanks();

        p.sendMessage("§aUstawiono kolor rangi.");
        p.closeInventory();

    } else if (title.startsWith("§8Edytuj ")) {

        String rank = editing.get(p.getUniqueId());
        if (rank == null) return;

        switch (e.getCurrentItem().getType()) {

            case PAINTING -> openColorGUI(p);

            case NAME_TAG -> {
                p.closeInventory();

                ConversationFactory factory = new ConversationFactory(this)
                        .withFirstPrompt(new StringPrompt() {
                            @Override
                            public String getPromptText(ConversationContext context) {
                                return "§aWpisz nową nazwę rangi:";
                            }

                            @Override
                            public Prompt acceptInput(ConversationContext context, String input) {
                                String newRank = input.toLowerCase();

                                if (ranks.isConfigurationSection(newRank)) {
                                    p.sendMessage("§cTaka ranga już istnieje!");
                                    return END_OF_CONVERSATION;
                                }

                                String col = ranks.getString(rank + ".color", "gray");

                                ranks.set(rank, null);
                                ranks.set(newRank + ".color", col);

                                for (String k : players.getKeys(false)) {
                                    if (players.getString(k).equals(rank)) {
                                        players.set(k, newRank);
                                    }
                                }

                                saveRanks();
                                savePlayers();

                                p.sendMessage("§aZmieniono nazwę rangi.");
                                return END_OF_CONVERSATION;
                            }
                        })
                        .withLocalEcho(false);

                factory.buildConversation(p).begin();
            }

            case BARRIER -> {
                p.closeInventory();
                p.sendMessage(
                        Component.text("[TAK]").color(NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/ranga confirmdelete " + rank))
                );
                p.sendMessage(
                        Component.text("[NIE]").color(NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/ranga canceldelete"))
                );
            }
        }
    }
}

/* ================ CHAT ================= */

@EventHandler
public void onChat(AsyncChatEvent e) {
    Player p = e.getPlayer();

    String rank = getRank(p);

    Component prefix = Component.text("[" + rank.toUpperCase() + "] ")
            .color(colorOf(rank));

    Component name = Component.text(p.getName(), NamedTextColor.WHITE);
    Component msg = e.message();

    e.renderer((source, sourceDisplayName, message, viewer) ->
            prefix
                    .append(name)
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(msg)
    );
}


    /* ================ COMMANDS ================ */

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {

        if (c.getName().equalsIgnoreCase("ranga") && s instanceof Player p) {

            if (a.length == 0) {
                openMainGUI(p);
                return true;
            }

            if (a.length == 2 && a[0].equalsIgnoreCase("edit")) {
                openEditGUI(p, a[1]);
                return true;
            }

            if (a.length == 2 && a[0].equalsIgnoreCase("delete")) {
                ranks.set(a[1], null);
                saveRanks();
                p.sendMessage("§cUsunięto rangę.");
                return true;
            }

            if (a.length == 2 && a[0].equalsIgnoreCase("confirmdelete")) {
                ranks.set(a[1], null);
                saveRanks();
                p.sendMessage("§cUsunięto rangę.");
                return true;
            }

            if (a.length == 1 && a[0].equalsIgnoreCase("canceldelete")) {
                p.sendMessage("§aAnulowano.");
                return true;
            }
        }

        if (c.getName().equalsIgnoreCase("setrank") && s.isOp()) {

            if (a.length != 2) {
                s.sendMessage("§c/setrank <nick> <ranga>");
                return true;
            }

            Player t = Bukkit.getPlayer(a[0]);
            if (t == null) {
                s.sendMessage("§cGracz offline");
                return true;
            }

            String rank = a[1].toLowerCase();

            if (!ranks.isConfigurationSection(rank)) {
                s.sendMessage("§cTaka ranga nie istnieje!");
                return true;
            }

            players.set(t.getUniqueId().toString(), rank);
            savePlayers();

            updateTab(t);
            for (Player o : Bukkit.getOnlinePlayers()) {
                updateTab(o);
            }

            s.sendMessage("§aUstawiono rangę §e" + rank);
            return true;
        }

        if (c.getName().equalsIgnoreCase("fly") && s instanceof Player p) {
            p.setAllowFlight(!p.getAllowFlight());
            p.sendMessage("§aFly: " + (p.getAllowFlight() ? "ON" : "OFF"));
            return true;
        }

        if (c.getName().equalsIgnoreCase("vanish") && s instanceof Player p) {

            if (vanished.contains(p.getUniqueId())) {
                vanished.remove(p.getUniqueId());

                for (Player o : Bukkit.getOnlinePlayers()) {
                    o.showPlayer(this, p);
                }

                p.setInvisible(false);
                updateTab(p);

                p.sendMessage("§aVanish OFF");
            } else {
                vanished.add(p.getUniqueId());

                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(p)) {
                        o.hidePlayer(this, p);
                    }
                }

                p.setInvisible(true);
                p.playerListName(Component.empty());

                p.sendMessage("§aVanish ON");
            }
            return true;
        }

        return true;
    }
}
