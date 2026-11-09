package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.domain.entities.QuestTargetRarity

/**
 * REQ-176: which discovered blocks and mobs may become weekly quest targets.
 * Allowlists by name, so a new Minecraft version never adds an odd target
 * (a new pet, a technical entity, a decorative block variant) by itself.
 */
internal object QuestTargetPool {
    private val COMMON_MOBS = setOf(
        // Overworld hostiles
        "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED", "SPIDER", "CAVE_SPIDER",
        "CREEPER", "ENDERMAN", "SLIME", "WITCH", "PILLAGER", "VINDICATOR", "SILVERFISH", "GUARDIAN",
        // Nether and End
        "BLAZE", "GHAST", "MAGMA_CUBE", "ZOMBIFIED_PIGLIN", "PIGLIN", "HOGLIN", "WITHER_SKELETON", "SHULKER",
        // Farm animals
        "COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "SQUID",
    )
    private val RARE_MOBS = setOf(
        "PHANTOM", "BREEZE", "CREAKING", "EVOKER", "PIGLIN_BRUTE", "WARDEN", "ELDER_GUARDIAN", "RAVAGER",
    )
    private val PRECIOUS_MOBS = setOf("ENDER_DRAGON", "WITHER")

    /** Rarity for a kill-quest mob, or null when the mob must never be a target. */
    fun mobRarity(entity: String): QuestTargetRarity? = when (entity) {
        in PRECIOUS_MOBS -> QuestTargetRarity.PRECIOUS
        in RARE_MOBS -> QuestTargetRarity.RARE
        in COMMON_MOBS -> QuestTargetRarity.COMMON
        else -> null
    }

    /** Age-based crops count only when fully grown; the rest are growth-timer plants (see GrownPlantHarvest). */
    private val HARVEST_TARGETS = setOf(
        "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH",
        "TORCHFLOWER_CROP", "PITCHER_CROP", "SUGAR_CANE", "CACTUS", "KELP", "BAMBOO",
    )

    fun isHarvestTarget(block: String): Boolean = block in HARVEST_TARGETS

    private val PLACE_EXACT = setOf(
        "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "SMOOTH_STONE", "BRICKS", "GLASS", "TERRACOTTA",
        "DIRT", "SAND", "RED_SAND", "GRAVEL", "SANDSTONE", "SMOOTH_SANDSTONE", "RED_SANDSTONE",
        "SMOOTH_RED_SANDSTONE", "ANDESITE", "DIORITE", "GRANITE", "POLISHED_ANDESITE", "POLISHED_DIORITE",
        "POLISHED_GRANITE", "DEEPSLATE", "COBBLED_DEEPSLATE", "POLISHED_DEEPSLATE", "TUFF", "POLISHED_TUFF",
        "CALCITE", "BLACKSTONE", "POLISHED_BLACKSTONE", "QUARTZ_BLOCK", "SMOOTH_QUARTZ", "PRISMARINE",
        "NETHERRACK", "END_STONE", "MUD", "PACKED_MUD", "CLAY", "SNOW_BLOCK", "HAY_BLOCK", "BASALT",
        "SMOOTH_BASALT", "POLISHED_BASALT", "BOOKSHELF",
    )
    private val PLACE_SUFFIXES = listOf("_PLANKS", "_LOG", "_WOOD", "_STEM", "_HYPHAE", "_BRICKS", "_CONCRETE",
        "_WOOL", "_TERRACOTTA", "_STAINED_GLASS")
    private val PLACE_EXCLUDED = listOf("_STAIRS", "_SLAB", "_WALL", "_FENCE", "_PANE", "_DOOR", "_BUTTON",
        "_PRESSURE_PLATE", "_SIGN", "_CARPET", "WAXED", "EXPOSED", "WEATHERED", "OXIDIZED", "INFESTED",
        "CRACKED", "CHISELED", "POWDER", "MELON_STEM", "PUMPKIN_STEM")

    /** Full building blocks players place in bulk; never stairs, slabs, panes or other decorative variants. */
    fun isPlaceTarget(block: String): Boolean =
        PLACE_EXCLUDED.none { it in block } &&
            (block in PLACE_EXACT || PLACE_SUFFIXES.any { block.endsWith(it) })
}
