package net.jenkimods.bioforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class BioForgeTags {

    public static final TagKey<EntityType<?>> NO_INFECTION_ATTACK_SPREAD =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.tryBuild("bioforge", "no_infection_attack_spread"));

    public static final TagKey<EntityType<?>> NO_DIAGNOSTICS =
            TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.tryBuild("bioforge", "no_diagnostics"));

    public static final TagKey<Block> INFECTABLE_CROPS =
            BlockTags.create(
                    ResourceLocation.tryBuild("bioforge", "infectable_crops"));
    public static final TagKey<Block> DECONTAMINATION_TARGETS =
            BlockTags.create(ResourceLocation.tryBuild("bioforge", "decontamination_targets"));

    public static final TagKey<Item> BLOCKS_OUTGOING_AIRBORNE = item("blocks_outgoing_airborne");
    public static final TagKey<Item> REDUCES_INCOMING_AIRBORNE = item("reduces_incoming_airborne");
    public static final TagKey<Item> BLOCKS_INCOMING_AIRBORNE = item("blocks_incoming_airborne");
    public static final TagKey<Item> BLOCKS_OUTGOING_CONTACT = item("blocks_outgoing_contact");
    public static final TagKey<Item> BLOCKS_INCOMING_CONTACT = item("blocks_incoming_contact");
    public static final TagKey<Item> BLOCKS_SYRINGES = item("blocks_syringes");
    public static final TagKey<Item> BLOCKS_HEAT_SYMPTOMS = item("blocks_heat_symptoms");
    public static final TagKey<Item> BLOCKS_CHILL_SYMPTOMS = item("blocks_chill_symptoms");
    public static final TagKey<Item> HAZCURE_PIECES = item("hazcure_pieces");

    private static TagKey<Item> item(String path) {
        return ItemTags.create(ResourceLocation.tryBuild("bioforge", path));
    }
}
