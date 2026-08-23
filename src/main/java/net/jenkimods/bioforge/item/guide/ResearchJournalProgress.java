package net.jenkimods.bioforge.item.guide;

import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.guide.ResearchJournalPageDefinition;
import net.jenkimods.bioforge.api.guide.ResearchJournalRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;

@EventBusSubscriber(modid = BioForge.MODID)
public final class ResearchJournalProgress {
    private static final String DATA_KEY = "BioForgeResearchJournal";
    private static final String PERSISTED_NBT_TAG = "PlayerPersisted";
    private static final String ACTIVE_KEY = "Active";
    private static final String UNLOCKED_KEY = "UnlockedPages";
    private static final String LOCKED_KEY = "LockedPages";
    private static final String DATA_VERSION_KEY = "DataVersion";
    private static final int CURRENT_DATA_VERSION = 2;

    private ResearchJournalProgress() {}

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getCrafting().is(BioForge.RESEARCH_JOURNAL.get())) {
            activate(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        boolean active = isActive(player);
        if (!active && hasJournal(player.getInventory())) {
            activate(player, false);
            return;
        }
        if (active) discoverAvailablePages(player, true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag original = journalData(event.getOriginal(), false);
        if (original != null) saveJournalData(event.getEntity(), original.copy());
    }

    public static void activate(ServerPlayer player, boolean notifyUnlocks) {
        CompoundTag data = journalData(player, true);
        migrateData(data);
        boolean wasActive = data.getBoolean(ACTIVE_KEY);
        data.putBoolean(ACTIVE_KEY, true);
        saveJournalData(player, data);
        discoverAvailablePages(player, wasActive && notifyUnlocks);
        if (!wasActive) ResearchJournalNetwork.activated(player);
    }

    public static Set<ResourceLocation> unlockedPages(ServerPlayer player) {
        return readPages(player, UNLOCKED_KEY);
    }

    public static Set<ResourceLocation> lockedPages(ServerPlayer player) {
        return readPages(player, LOCKED_KEY);
    }

    private static Set<ResourceLocation> readPages(ServerPlayer player, String key) {
        CompoundTag data = journalData(player, false);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (data == null) return result;
        if (migrateData(data)) saveJournalData(player, data);
        ListTag list = data.getList(key, Tag.TAG_STRING);
        for (Tag entry : list) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) result.add(id);
        }
        return result;
    }

    public static int unlockPages(ServerPlayer player,
                                  Collection<ResourceLocation> pageIds) {
        Set<ResourceLocation> unlocked = unlockedPages(player);
        Set<ResourceLocation> locked = lockedPages(player);
        int changed = 0;
        for (ResourceLocation id : pageIds) {
            boolean updated = unlocked.add(id);
            updated |= locked.remove(id);
            if (updated) changed++;
        }
        writePages(player, UNLOCKED_KEY, unlocked);
        writePages(player, LOCKED_KEY, locked);
        return changed;
    }

    public static int lockPages(ServerPlayer player,
                                Collection<ResourceLocation> pageIds) {
        Set<ResourceLocation> unlocked = unlockedPages(player);
        Set<ResourceLocation> locked = lockedPages(player);
        int changed = 0;
        for (ResourceLocation id : pageIds) {
            boolean updated = unlocked.remove(id);
            updated |= locked.remove(id);
            if (updated) changed++;
        }
        writePages(player, UNLOCKED_KEY, unlocked);
        writePages(player, LOCKED_KEY, locked);
        return changed;
    }

    public static int unlockAll(ServerPlayer player) {
        return unlockPages(player, ResearchJournalRegistry.pages().stream()
                .map(ResearchJournalPageDefinition::id).toList());
    }

    public static int lockAll(ServerPlayer player) {
        return lockPages(player, ResearchJournalRegistry.pages().stream()
                .map(ResearchJournalPageDefinition::id).toList());
    }

    private static void discoverAvailablePages(ServerPlayer player, boolean notify) {
        Set<ResourceLocation> unlocked = unlockedPages(player);
        Set<ResourceLocation> locked = lockedPages(player);
        boolean changed = false;
        for (ResearchJournalPageDefinition page : ResearchJournalRegistry.pages()) {
            if (page.unlockRequirements().isEmpty() || unlocked.contains(page.id())
                    || locked.contains(page.id())) continue;
            boolean matches = page.requireAllUnlocks();
            if (page.requireAllUnlocks()) {
                for (ResearchJournalPageDefinition.UnlockRequirement requirement
                        : page.unlockRequirements()) {
                    if (!contains(player.getInventory(), requirement)) {
                        matches = false;
                        break;
                    }
                }
            } else {
                for (ResearchJournalPageDefinition.UnlockRequirement requirement
                        : page.unlockRequirements()) {
                    if (contains(player.getInventory(), requirement)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) continue;
            unlocked.add(page.id());
            changed = true;
            if (notify) ResearchJournalNetwork.unlocked(player, page.id(), page.title());
        }
        if (changed) writeUnlocked(player, unlocked);
    }

    private static boolean contains(Inventory inventory,
                                    ResearchJournalPageDefinition.UnlockRequirement requirement) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (requirement.test(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private static boolean hasJournal(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(BioForge.RESEARCH_JOURNAL.get())) return true;
        }
        return false;
    }

    private static boolean isActive(Player player) {
        CompoundTag data = journalData(player, false);
        if (data == null) return false;
        if (migrateData(data)) saveJournalData(player, data);
        return data.getBoolean(ACTIVE_KEY);
    }

    private static void writeUnlocked(Player player, Set<ResourceLocation> unlocked) {
        writePages(player, UNLOCKED_KEY, unlocked);
    }

    private static void writePages(Player player, String key,
                                   Set<ResourceLocation> pages) {
        CompoundTag data = journalData(player, true);
        ListTag list = new ListTag();
        pages.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id.toString())));
        data.put(key, list);
        saveJournalData(player, data);
    }

    private static CompoundTag journalData(Player player, boolean create) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            if (!create) return null;
            persisted.put(DATA_KEY, new CompoundTag());
            root.put(PERSISTED_NBT_TAG, persisted);
        }
        return persisted.getCompound(DATA_KEY);
    }

    private static void saveJournalData(Player player, CompoundTag data) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(PERSISTED_NBT_TAG);
        persisted.put(DATA_KEY, data);
        root.put(PERSISTED_NBT_TAG, persisted);
    }

    private static boolean migrateData(CompoundTag data) {
        if (data.getInt(DATA_VERSION_KEY) >= CURRENT_DATA_VERSION) return false;
        data.remove(LOCKED_KEY);
        data.putInt(DATA_VERSION_KEY, CURRENT_DATA_VERSION);
        return true;
    }
}
