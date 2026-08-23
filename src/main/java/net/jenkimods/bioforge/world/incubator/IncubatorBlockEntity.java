package net.jenkimods.bioforge.world.incubator;

import net.jenkimods.bioforge.config.BioForgeServerConfig;
import net.jenkimods.bioforge.BioForge;
import net.jenkimods.bioforge.api.definition.BioForgeIds;
import net.jenkimods.bioforge.api.definition.PathogenDefinition;
import net.jenkimods.bioforge.api.definition.SymptomDefinition;
import net.jenkimods.bioforge.definition.BioForgeDefinitionManager;
import net.jenkimods.bioforge.infection.*;
import net.jenkimods.bioforge.infection.symptoms.BioForgeSymptoms;
import net.jenkimods.bioforge.infection.symptoms.SymptomKey;
import net.jenkimods.bioforge.item.*;
import net.jenkimods.bioforge.item.reagents.CatalystVialItem;
import net.jenkimods.bioforge.util.NbtObfuscator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class IncubatorBlockEntity extends BlockEntity implements MenuProvider {

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0) {
                return hasPrimaryRecipe(stack);
            }
            return hasSecondaryRecipe(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return (slot >= 1 && slot <= 3) ? 1 : super.getSlotLimit(slot);
        }
    };

    private int progress = 0;
    private int maxProgress = 200;
    @Nullable
    private ResourceLocation activeRecipeId;
    private String activePrimarySignature = "";

    protected final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return index == 0 ? progress : maxProgress; }
        @Override public void set(int index, int value) { if (index == 0) progress = value; else maxProgress = value; }
        @Override public int getCount() { return 2; }
    };

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(BioForge.INCUBATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (level.isClientSide()) return;

        Optional<RecipeHolder<IncubatorRecipe>> recipeOptional = be.findRecipe();
        if (recipeOptional.isEmpty()) {
            be.resetProgress();
            return;
        }

        RecipeHolder<IncubatorRecipe> recipeHolder = recipeOptional.get();
        IncubatorRecipe recipe = recipeHolder.value();
        String primarySignature = createPrimarySignature(be.items.getStackInSlot(0));
        if (!recipeHolder.id().equals(be.activeRecipeId)
                || !primarySignature.equals(be.activePrimarySignature)) {
            be.progress = 0;
            be.activeRecipeId = recipeHolder.id();
            be.activePrimarySignature = primarySignature;
        }
        be.maxProgress = recipe.processingTime();
        be.progress++;
        if (be.progress >= be.maxProgress) {
            be.progress = 0;
            be.process(recipe, level.random);
        }
        be.setChanged();
    }

    private Optional<RecipeHolder<IncubatorRecipe>> findRecipe() {
        if (level == null) {
            return Optional.empty();
        }
        ItemStack primary = items.getStackInSlot(0);
        return getRecipes().stream()
                .filter(holder -> holder.value().matchesPrimary(primary))
                .filter(holder -> {
                    for (int slot = 1; slot <= 3; slot++) {
                        if (holder.value().matchesSecondary(items.getStackInSlot(slot))) {
                            return true;
                        }
                    }
                    return false;
                })
                .sorted(Comparator
                        .comparingInt((RecipeHolder<IncubatorRecipe> holder) ->
                                holder.value().primaryInput().specificity())
                        .thenComparingInt(holder ->
                                holder.value().secondaryInput().specificity())
                        .reversed()
                        .thenComparing(holder -> holder.id().toString()))
                .findFirst();
    }

    private List<RecipeHolder<IncubatorRecipe>> getRecipes() {
        if (level == null) {
            return List.of();
        }
        return level.getRecipeManager().getAllRecipesFor(IncubatorRecipeRegistration.TYPE);
    }

    private boolean hasPrimaryRecipe(ItemStack stack) {
        return getRecipes().stream()
                .anyMatch(holder -> holder.value().matchesPrimary(stack));
    }

    private boolean hasSecondaryRecipe(ItemStack stack) {
        return getRecipes().stream()
                .anyMatch(holder -> holder.value().matchesSecondary(stack));
    }

    private void resetProgress() {
        if (progress != 0 || activeRecipeId != null) {
            progress = 0;
            activeRecipeId = null;
            activePrimarySignature = "";
            setChanged();
        }
    }

    private static String createPrimarySignature(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                + ":" + stack.getCount() + ":" + ItemStack.hashItemAndComponents(stack);
    }

    private void process(IncubatorRecipe recipe, RandomSource random) {
        ItemStack primary = items.getStackInSlot(0);
        String sourceStrain = recipe.getSourceStrain(primary);
        ResourceLocation generatedPathogen = null;
        if (recipe.operation() == IncubatorOperation.GENERATE_STRAIN) {
            generatedPathogen = CatalystVialItem.getPathogenIdOrRandom(primary);
            if (generatedPathogen == null
                    || BioForgeDefinitionManager.pathogen(generatedPathogen).isEmpty()) {
                return;
            }
        }

        boolean produced = false;
        int producedSlots = 0;
        int affordableSlots = recipe.primaryCostPerOutput() && recipe.primaryItemCost() > 0
                ? primary.getCount() / recipe.primaryItemCost()
                : 3;
        for (int slot = 1; slot <= 3; slot++) {
            if (producedSlots >= affordableSlots) {
                break;
            }
            ItemStack secondary = items.getStackInSlot(slot);
            if (!recipe.matchesSecondary(secondary)) {
                continue;
            }

            Item outputItem = recipe.output().resolveItem(random);
            if (outputItem == null) {
                continue;
            }
            int outputCount = Math.min(
                    recipe.outputCount(), outputItem.getDefaultMaxStackSize());
            ItemStack output = new ItemStack(outputItem, outputCount);
            if (recipe.operation() == IncubatorOperation.GENERATE_STRAIN) {
                StrainData strain = generateRandomStrain(generatedPathogen, random);
                NbtObfuscator.writeString(output, strain.toPayload());
            } else if (sourceStrain != null) {
                NbtObfuscator.writeString(output, sourceStrain);
            } else if (recipe.operation() != IncubatorOperation.CRAFT) {
                continue;
            }

            items.setStackInSlot(slot, output);
            produced = true;
            producedSlots++;
        }

        if (!produced) {
            return;
        }

        if (recipe.catalystChargeCost() > 0) {
            for (int charge = 0; charge < recipe.catalystChargeCost() && !primary.isEmpty(); charge++) {
                CatalystVialItem.consumeCharge(primary);
            }
        }
        if (recipe.primaryItemCost() > 0) {
            int primaryCost = recipe.primaryCostPerOutput()
                    ? recipe.primaryItemCost() * producedSlots
                    : recipe.primaryItemCost();
            primary.shrink(primaryCost);
        }
    }

    private static StrainData generateRandomStrain(ResourceLocation pathogenId, RandomSource random) {
        StrainData strain = StrainData.createEmpty();
        strain.setPathogenId(pathogenId);
        strain.setColonyId(UUID.randomUUID());
        PathogenType pathogen = BioForgeIds.legacyPathogen(pathogenId);
        PathogenDefinition pathogenDefinition =
                BioForgeDefinitionManager.pathogen(pathogenId).orElse(null);
        List<ResourceLocation> allowed = pathogenDefinition == null
                ? pathogen == null ? new ArrayList<>() : pathogen.getAllowedTransmissions().stream()
                .filter(BioForgeServerConfig::isTransmissionEnabled)
                .map(BioForgeIds::transmission)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new))
                : pathogenDefinition.allowedTransmissions().stream()
                .filter(id -> {
                    InfectionType legacy = BioForgeIds.legacyTransmission(id);
                    return legacy == null || BioForgeServerConfig.isTransmissionEnabled(legacy);
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!allowed.isEmpty()) {
            for (int index = allowed.size() - 1; index > 0; index--) {
                Collections.swap(allowed, index, random.nextInt(index + 1));
            }
            int count = 1 + random.nextInt(allowed.size());
            for (int i = 0; i < count; i++) strain.getTransmissionIds().add(allowed.get(i));
        }
        if (pathogen == null && pathogenDefinition != null) {
            pathogenDefinition.defaultSymptoms().forEach((symptomId, configured) -> {
                String storageId = BioForgeDefinitionManager.storageId(symptomId);
                if (!BioForgeServerConfig.isSymptomEnabled(storageId)) return;
                SymptomDefinition symptom = BioForgeDefinitionManager.symptom(symptomId).orElse(null);
                if (symptom == null) return;
                try {
                    String value = switch (symptom.valueType()) {
                        case FLOAT -> String.valueOf(configured.minimum().getAsFloat()
                                + random.nextFloat() * (configured.maximum().getAsFloat()
                                - configured.minimum().getAsFloat()));
                        case INTEGER -> {
                            int min = configured.minimum().getAsInt();
                            int max = configured.maximum().getAsInt();
                            yield String.valueOf(min + (max <= min ? 0 : random.nextInt(max - min + 1)));
                        }
                        case BOOLEAN -> String.valueOf(configured.minimum().getAsBoolean());
                        case STRING, ENUM -> configured.minimum().getAsString();
                    };
                    strain.getSymptoms().put(storageId, value);
                } catch (RuntimeException ignored) {
                }
            });
            return strain;
        }
        Map<SymptomKey<?>, float[]> ranges = BioForgeSymptoms.getDefaultRanges(pathogenId);
        for (Map.Entry<String, SymptomKey<?>> entry : BioForgeSymptoms.getEnabledSymptomKeys().entrySet()) {
            SymptomKey<?> key = entry.getValue();
            String keyId = entry.getKey();
            if (key.getType() == Float.class) {
                float[] minMax = ranges.get(key);
                if (minMax != null) {
                    float value = minMax[0] + random.nextFloat() * (minMax[1] - minMax[0]);
                    strain.getSymptoms().put(keyId, String.valueOf(value));
                }
            } else if (key.getType() == Boolean.class) {
                strain.getSymptoms().put(keyId, String.valueOf(random.nextBoolean()));
            } else if (key.getType().isEnum()) {
                Object[] constants = key.getType().getEnumConstants();
                if (constants != null && constants.length > 0) {
                    int idx = random.nextInt(constants.length);
                    strain.getSymptoms().put(keyId, ((Enum<?>) constants[idx]).name());
                }
            }
        }
        return strain;
    }

    @Override public Component getDisplayName() { return Component.translatable("block.bioforge.incubator"); }

    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new IncubatorMenu(id, inv, this, data);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inv", items.serializeNBT(registries));
        tag.putInt("progress", progress);
        if (activeRecipeId != null) {
            tag.putString("active_recipe", activeRecipeId.toString());
        }
        tag.putString("active_primary", activePrimarySignature);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("inv"));
        progress = tag.getInt("progress");
        activeRecipeId = ResourceLocation.tryParse(tag.getString("active_recipe"));
        activePrimarySignature = tag.getString("active_primary");
    }
    public ItemStackHandler getItemHandler() { return items; }

    public void drops() {
        if (level == null) return;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX()+0.5, worldPosition.getY()+0.5, worldPosition.getZ()+0.5, stack);
        }
    }
}
