package mc.arch.minigame.pof

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import net.evilblock.cubed.util.bukkit.ItemBuilder

object PofGameType : MiniGameTypeMetadata(
    internalId = "pof",
    displayName = "Pillar of Fortune",
    item = XMaterial.GOLD_BLOCK,
    lobbyGroup = "poflobby",
    autoJoinSkinValue = "ewogICJ0aW1lc3RhbXAiIDogMTYyMDcxOTk4NTc3MCwKICAicHJvZmlsZUlkIiA6ICJhMjk1ODZmYmU1ZDk0Nzk2OWZjOGQ4ZGE0NzlhNDNlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJWaWVydGVsdG9hc3RpaWUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTZmN2JlNzE1MWFmMWVjZmNjYjlmZTRiM2JiMjc0MjIzYWQyOGM0NTdlMzQ5ZjZhYmRmYTgwOTE0MDA2MDZmZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
    autoJoinSkinSignature = "n2n8hBYp5wdcXySt9Hs6Cweibt7s+NG+Xa76G+GS7jj2ztWciIUPjFFGLeNQw47kBj5AJNdBr3g0Z66HRa7rDa12UeGbgnI6xaqL4DvwAnXSQVBsCwjS8Ef5fmwyNI7nhNG5buagUg8nDHx5ZCmqf/9DNYlPEizHC14z1veJ8fBpIfP0eeI21BmFv4rnsF0dn4jTC+0FeK3GwqPEevcF/sfiHfkyLpmR/KO5D2fNtSq7z3Gw4q24XBU1M4fBtIsZGyCWtlOPgfGGy8d/VlRx5zlLERcvkfNPRrhS8yxvTAnFbq1zWCehzo1l3dmkI1Zi/RcqOi2+K64R2D3uKX/+96UEI42Y4pFMXI5f3MyFISACWsu5Qelv8S5YHtSOxKmk8PETXf4HMLtqh2KxWfMUt1zRkgyCOOLaTTlqC/jrmyJMJMNXqKjjAVnNSOL9BTv/K8zUcin3AbWlQfEkK0fNL6oxkODTeN8FdnlwCtGMjpR9bLPw2pnPSEvx/o/KOSq3eli6qavA1cAqtRI/lsavv6v01tkvkUgc+G2dK8xhWbO5WKUL8P1liuMcpPltzCCvOKgQYboGynW7Q4mRfImgd4kktNxclJEzSp/eJyCwSSvoZ2Axn7sKoW/YWO4rTHijZU69JVW3Er6SI5tjGcQjIQVSRF1ZUtaR1iqVgv6IenE=",
    gameModes = mapOf(
        "solo" to MiniGameModeMetadata(
            id = "solo",
            description = "Random loot rains down — outlast everyone on your pillar!",
            queueId = "pof_main:Casual:1v1",
            displayName = "Solo",
            displayItem = ItemBuilder
                .of(XMaterial.GOLD_BLOCK)
                .build(),
            mapGroup = "pof_main",
            kitID = "pof_main",
            mode = PofMode.SOLO,
            npcSkinValue = "ewogICJ0aW1lc3RhbXAiIDogMTYyMzQyMzEzNDMxMSwKICAicHJvZmlsZUlkIiA6ICJjNjc3MGJjZWMzZjE0ODA3ODc4MTU0NWRhMGFmMDI1NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDE2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Q0Zjg5Yjg0YzNhZGU4ZTJjYzRlZjc1MTgwZDI2OGEwMzM5YzdjZTA5ZjJkYmU3ZjBiMWU5YTViNzdlY2I4NjUiCiAgICB9CiAgfQp9",
            npcSkinSignature = "kFQ++Gfc7RTFx8eZ85Mrho/7ZlW1zltqJrltGi3Jb6rL9iEkppoGlfop8t7UU4JeSUEx/P9sjs58QQ+jWaeIb6lv7YZvJ0fmd3hzSvhcPFGBgwdBsu0GSp3e1RHV8HNwaIi/qg7LeQvQ6IaZ189eBKJxWULU4NxNw7YTtZyUz6ezaPfQETk7Ctb0FT52IUuyPYNVUnlI/Tu1BkmD5VWxzjJpJ1L7DrydBK+SULzRDNea1x3VSEKdNXT5M3ierMt8KqPy8B4ZT6Z3AS5WyE0hsP9dmoJAxbIlswFgjsmJWswYMI+RpNKORGEnciVu9Yqbbv/M05+6y/6PJju6vHotl40mzU8rqLkxuc/LCuWuRpRMRTChNKzgGaDfcDBQP/3T1URmKoyBj4DqLm99U1sl4Kum9aUTbzZF868aNBzRkX9oltL6zaBDuVbuJArIE1d+I35gcSMEWc1RsS/x4/N0kV7OSGLLAj1HnDghT/az3YEK+5IPLcY/GojHDIIsoDopaypj49p1eoiQUml7vFVtCml8gGRERFGOk92/gq7Q5tV63ysf1SN7CHUSfX+EowvNN0KAJAOjSeYTkzhLVakh3MCMt4S62/NqvXWtQfX97tAE/hEd6OXGymP7lLysMTZYT8Zb2I2umMkXn8ay/xxSzKO1qg+4J+cR0r/fAbsQLpM="
        ),
        "duos" to MiniGameModeMetadata(
            id = "duos",
            description = "Random loot rains down — team up and be the last duo standing!",
            queueId = "pof_main:Casual:2v2",
            displayName = "Duos",
            displayItem = ItemBuilder
                .of(XMaterial.GOLD_INGOT)
                .build(),
            mapGroup = "pof_main",
            kitID = "pof_main",
            mode = PofMode.DUOS,
            npcSkinValue = "ewogICJ0aW1lc3RhbXAiIDogMTYyMzQyMzEzNDMxMSwKICAicHJvZmlsZUlkIiA6ICJjNjc3MGJjZWMzZjE0ODA3ODc4MTU0NWRhMGFmMDI1NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDE2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Q0Zjg5Yjg0YzNhZGU4ZTJjYzRlZjc1MTgwZDI2OGEwMzM5YzdjZTA5ZjJkYmU3ZjBiMWU5YTViNzdlY2I4NjUiCiAgICB9CiAgfQp9",
            npcSkinSignature = "kFQ++Gfc7RTFx8eZ85Mrho/7ZlW1zltqJrltGi3Jb6rL9iEkppoGlfop8t7UU4JeSUEx/P9sjs58QQ+jWaeIb6lv7YZvJ0fmd3hzSvhcPFGBgwdBsu0GSp3e1RHV8HNwaIi/qg7LeQvQ6IaZ189eBKJxWULU4NxNw7YTtZyUz6ezaPfQETk7Ctb0FT52IUuyPYNVUnlI/Tu1BkmD5VWxzjJpJ1L7DrydBK+SULzRDNea1x3VSEKdNXT5M3ierMt8KqPy8B4ZT6Z3AS5WyE0hsP9dmoJAxbIlswFgjsmJWswYMI+RpNKORGEnciVu9Yqbbv/M05+6y/6PJju6vHotl40mzU8rqLkxuc/LCuWuRpRMRTChNKzgGaDfcDBQP/3T1URmKoyBj4DqLm99U1sl4Kum9aUTbzZF868aNBzRkX9oltL6zaBDuVbuJArIE1d+I35gcSMEWc1RsS/x4/N0kV7OSGLLAj1HnDghT/az3YEK+5IPLcY/GojHDIIsoDopaypj49p1eoiQUml7vFVtCml8gGRERFGOk92/gq7Q5tV63ysf1SN7CHUSfX+EowvNN0KAJAOjSeYTkzhLVakh3MCMt4S62/NqvXWtQfX97tAE/hEd6OXGymP7lLysMTZYT8Zb2I2umMkXn8ay/xxSzKO1qg+4J+cR0r/fAbsQLpM="
        )
    )
)
