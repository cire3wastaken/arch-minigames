package mc.arch.minigames.persistent.housing.game.menu.house.biome

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.persistent.housing.api.content.HousingBiome
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.menu.house.MainHouseMenu
import mc.arch.minigames.persistent.housing.game.world.HousingBiomeService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player
import kotlin.collections.set

class HouseBiomeSelectionMenu(val house: PlayerHouse) : Menu("Realm Biome")
{
    init
    {
        updateAfterClick = true
    }

    override fun size(buttons: Map<Int, Button>): Int = 36

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        val slots = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
        )

        HousingBiome.entries.forEachIndexed { index, biome ->
            val slot = slots.getOrNull(index) ?: return@forEachIndexed
            val isCurrent = house.housingBiome == biome

            buttons[slot] = ItemBuilder.of(iconFor(biome))
                .name("${if (isCurrent) CC.B_GREEN else CC.GREEN}${biome.displayName}")
                .addToLore(
                    "${CC.GRAY}Set your realm's biome to",
                    "${CC.GRAY}${biome.displayName}${CC.GRAY}.",
                    "",
                    if (isCurrent) "${CC.AQUA}This is your current biome!" else "${CC.YELLOW}Click to select!"
                )
                .toButton { _, _ ->
                    if (isCurrent)
                    {
                        player.sendMessage("${CC.YELLOW}That biome is already selected!")
                        Button.playFail(player)
                        return@toButton
                    }

                    val columns = HousingBiomeService.setBiome(house, biome, player.world)
                    if (columns == null)
                    {
                        player.sendMessage(
                            "${CC.RED}Your realm hasn't been fully bootstrapped yet - try again in a moment."
                        )
                        Button.playFail(player)
                        return@toButton
                    }

                    Button.playNeutral(player)
                    player.sendMessage(
                        "${CC.B_GREEN}SUCCESS! ${CC.GREEN}Your realm biome is now ${biome.displayName}${CC.GREEN}."
                    )
                }
        }

        buttons[31] = MainHouseMenu.mainMenuButton(house)
        return buttons
    }

    private fun iconFor(biome: HousingBiome): XMaterial = when (biome)
    {
        HousingBiome.PLAINS -> XMaterial.GRASS_BLOCK
        HousingBiome.FOREST -> XMaterial.OAK_SAPLING
        HousingBiome.BIRCH_FOREST -> XMaterial.BIRCH_SAPLING
        HousingBiome.JUNGLE -> XMaterial.JUNGLE_SAPLING
        HousingBiome.TAIGA -> XMaterial.SPRUCE_SAPLING
        HousingBiome.SAVANNA -> XMaterial.ACACIA_SAPLING
        HousingBiome.DESERT -> XMaterial.SAND
        HousingBiome.MESA -> XMaterial.RED_SAND
        HousingBiome.SWAMPLAND -> XMaterial.LILY_PAD
        HousingBiome.MUSHROOM_ISLAND -> XMaterial.RED_MUSHROOM
        HousingBiome.ICE_PLAINS -> XMaterial.SNOWBALL
        HousingBiome.EXTREME_HILLS -> XMaterial.STONE
        HousingBiome.OCEAN -> XMaterial.WATER_BUCKET
        HousingBiome.HELL -> XMaterial.NETHERRACK
        HousingBiome.SKY -> XMaterial.END_STONE
    }
}
