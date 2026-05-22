package mc.arch.minigames.persistent.housing.api.content

import net.evilblock.cubed.util.CC
import org.bukkit.block.Biome

enum class HousingBiome(val displayName: String, val bukkit: Biome)
{
    PLAINS("${CC.GREEN}Plains", Biome.PLAINS),
    FOREST("${CC.D_GREEN}Forest", Biome.FOREST),
    BIRCH_FOREST("${CC.GREEN}Birch Forest", Biome.BIRCH_FOREST),
    JUNGLE("${CC.D_GREEN}Jungle", Biome.JUNGLE),
    TAIGA("${CC.AQUA}Taiga", Biome.TAIGA),
    SAVANNA("${CC.GOLD}Savanna", Biome.SAVANNA),
    DESERT("${CC.YELLOW}Desert", Biome.DESERT),
    MESA("${CC.RED}Mesa", Biome.MESA),
    SWAMPLAND("${CC.D_GREEN}Swampland", Biome.SWAMPLAND),
    MUSHROOM_ISLAND("${CC.PINK}Mushroom Island", Biome.MUSHROOM_ISLAND),
    ICE_PLAINS("${CC.WHITE}Snowy Tundra", Biome.ICE_PLAINS),
    EXTREME_HILLS("${CC.GRAY}Extreme Hills", Biome.EXTREME_HILLS),
    OCEAN("${CC.D_BLUE}Ocean", Biome.OCEAN),
    HELL("${CC.D_RED}Nether", Biome.HELL),
    SKY("${CC.B_WHITE}The End", Biome.SKY)
}
