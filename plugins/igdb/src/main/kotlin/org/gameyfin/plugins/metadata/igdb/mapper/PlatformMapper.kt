package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.Platform

/**
 * Mapper for converting between IGDB platform slugs and Gameyfin Platform enums
 */
class PlatformMapper {
    companion object {
        /**
         * Map from IGDB platform slug to Gameyfin Platform enum
         */
        private val igdbToGameyfinMap: Map<String, Platform> = mapOf(
            "linux" to Platform.LINUX,
            "n64" to Platform.NINTENDO_64,
            "wii" to Platform.WII,
            "win" to Platform.PC_MICROSOFT_WINDOWS,
            "ps" to Platform.PLAYSTATION,
            "ps2" to Platform.PLAYSTATION_2,
            "ps3" to Platform.PLAYSTATION_3,
            "xbox" to Platform.XBOX,
            "xbox360" to Platform.XBOX_360,
            "dos" to Platform.DOS,
            "mac" to Platform.MAC,
            "c64" to Platform.COMMODORE_C64_128_MAX,
            "amiga" to Platform.AMIGA,
            "nes" to Platform.NINTENDO_ENTERTAINMENT_SYSTEM,
            "snes" to Platform.SUPER_NINTENDO_ENTERTAINMENT_SYSTEM,
            "nds" to Platform.NINTENDO_DS,
            "ngc" to Platform.NINTENDO_GAMECUBE,
            "gbc" to Platform.GAME_BOY_COLOR,
            "dc" to Platform.DREAMCAST,
            "gba" to Platform.GAME_BOY_ADVANCE,
            "acpc" to Platform.AMSTRAD_CPC,
            "zxs" to Platform.ZX_SPECTRUM,
            "msx" to Platform.MSX,
            "genesis-slash-megadrive" to Platform.SEGA_MEGA_DRIVE_GENESIS,
            "sega32" to Platform.SEGA_32X,
            "saturn" to Platform.SEGA_SATURN,
            "gb" to Platform.GAME_BOY,
            "android" to Platform.ANDROID,
            "gamegear" to Platform.SEGA_GAME_GEAR,
            "3ds" to Platform.NINTENDO_3DS,
            "psp" to Platform.PLAYSTATION_PORTABLE,
            "ios" to Platform.IOS,
            "wiiu" to Platform.WII_U,
            "ngage" to Platform.N_GAGE,
            "zod" to Platform.TAPWAVE_ZODIAC,
            "psvita" to Platform.PLAYSTATION_VITA,
            "vc" to Platform.VIRTUAL_CONSOLE,
            "ps4--1" to Platform.PLAYSTATION_4,
            "xboxone" to Platform.XBOX_ONE,
            "3do" to Platform._3DO_INTERACTIVE_MULTIPLAYER,
            "fds" to Platform.FAMILY_COMPUTER_DISK_SYSTEM,
            "arcade" to Platform.ARCADE,
            "msx2" to Platform.MSX2,
            "mobile" to Platform.LEGACY_MOBILE_DEVICE,
            "wonderswan" to Platform.WONDERSWAN,
            "sfam" to Platform.SUPER_FAMICOM,
            "atari2600" to Platform.ATARI_2600,
            "atari7800" to Platform.ATARI_7800,
            "lynx" to Platform.ATARI_LYNX,
            "jaguar" to Platform.ATARI_JAGUAR,
            "atari-st" to Platform.ATARI_ST_STE,
            "sms" to Platform.SEGA_MASTER_SYSTEM_MARK_III,
            "atari8bit" to Platform.ATARI_8_BIT,
            "atari5200" to Platform.ATARI_5200,
            "intellivision" to Platform.INTELLIVISION,
            "colecovision" to Platform.COLECOVISION,
            "bbcmicro" to Platform.BBC_MICROCOMPUTER_SYSTEM,
            "vectrex" to Platform.VECTREX,
            "vic-20" to Platform.COMMODORE_VIC_20,
            "ouya" to Platform.OUYA,
            "blackberry" to Platform.BLACKBERRY_OS,
            "winphone" to Platform.WINDOWS_PHONE,
            "appleii" to Platform.APPLE_II,
            "x1" to Platform.SHARP_X1,
            "sega-cd" to Platform.SEGA_CD,
            "neogeomvs" to Platform.NEO_GEO_MVS,
            "neogeoaes" to Platform.NEO_GEO_AES,
            "browser" to Platform.WEB_BROWSER,
            "sg1000" to Platform.SG_1000,
            "donner30" to Platform.DONNER_MODEL_30,
            "turbografx16--1" to Platform.TURBOGRAFX_16_PC_ENGINE,
            "virtualboy" to Platform.VIRTUAL_BOY,
            "odyssey--1" to Platform.ODYSSEY,
            "microvision--1" to Platform.MICROVISION,
            "cpet" to Platform.COMMODORE_PET,
            "astrocade" to Platform.BALLY_ASTROCADE,
            "c16" to Platform.COMMODORE_16,
            "c-plus-4" to Platform.COMMODORE_PLUS_4,
            "pdp1" to Platform.PDP_1,
            "pdp10" to Platform.PDP_10,
            "pdp-8--1" to Platform.PDP_8,
            "gt40" to Platform.DEC_GT40,
            "famicom" to Platform.FAMILY_COMPUTER,
            "analogueelectronics" to Platform.ANALOGUE_ELECTRONICS,
            "nimrod" to Platform.FERRANTI_NIMROD_COMPUTER,
            "edsac--1" to Platform.EDSAC,
            "pdp-7--1" to Platform.PDP_7,
            "hp2100" to Platform.HP_2100,
            "hp3000" to Platform.HP_3000,
            "sdssigma7" to Platform.SDS_SIGMA_7,
            "call-a-computer" to Platform.CALL_A_COMPUTER_TIME_SHARED_MAINFRAME_COMPUTER_SYSTEM,
            "pdp11" to Platform.PDP_11,
            "cdccyber70" to Platform.CDC_CYBER_70,
            "plato--1" to Platform.PLATO,
            "imlac-pds1" to Platform.IMLAC_PDS_1,
            "microcomputer--1" to Platform.MICROCOMPUTER,
            "onlive" to Platform.ONLIVE_GAME_SYSTEM,
            "amiga-cd32" to Platform.AMIGA_CD32,
            "apple-iigs" to Platform.APPLE_IIGS,
            "acorn-archimedes" to Platform.ACORN_ARCHIMEDES,
            "philips-cdi" to Platform.PHILIPS_CD_I,
            "fm-towns" to Platform.FM_TOWNS,
            "neo-geo-pocket" to Platform.NEO_GEO_POCKET,
            "neo-geo-pocket-color" to Platform.NEO_GEO_POCKET_COLOR,
            "sharp-x68000" to Platform.SHARP_X68000,
            "nuon" to Platform.NUON,
            "wonderswan-color" to Platform.WONDERSWAN_COLOR,
            "swancrystal" to Platform.SWANCRYSTAL,
            "pc-8800-series" to Platform.PC_8800_SERIES,
            "trs-80" to Platform.TRS_80,
            "fairchild-channel-f" to Platform.FAIRCHILD_CHANNEL_F,
            "supergrafx" to Platform.PC_ENGINE_SUPERGRAFX,
            "ti-99" to Platform.TEXAS_INSTRUMENTS_TI_99,
            "switch" to Platform.NINTENDO_SWITCH,
            "super-nes-cd-rom-system" to Platform.SUPER_NES_CD_ROM_SYSTEM,
            "firetv" to Platform.AMAZON_FIRE_TV,
            "odyssey-2-slash-videopac-g7000" to Platform.ODYSSEY_2_VIDEOPAC_G7000,
            "acorn-electron" to Platform.ACORN_ELECTRON,
            "hyper-neo-geo-64" to Platform.HYPER_NEO_GEO_64,
            "neo-geo-cd" to Platform.NEO_GEO_CD,
            "new-3ds" to Platform.NEW_NINTENDO_3DS,
            "vc-4000" to Platform.VC_4000,
            "1292-advanced-programmable-video-system" to Platform._1292_ADVANCED_PROGRAMMABLE_VIDEO_SYSTEM,
            "ay-3-8500" to Platform.AY_3_8500,
            "ay-3-8610" to Platform.AY_3_8610,
            "pc-50x-family" to Platform.PC_50X_FAMILY,
            "ay-3-8760" to Platform.AY_3_8760,
            "ay-3-8710" to Platform.AY_3_8710,
            "ay-3-8603" to Platform.AY_3_8603,
            "ay-3-8605" to Platform.AY_3_8605,
            "ay-3-8606" to Platform.AY_3_8606,
            "ay-3-8607" to Platform.AY_3_8607,
            "pc-9800-series" to Platform.PC_9800_SERIES,
            "turbografx-16-slash-pc-engine-cd" to Platform.TURBOGRAFX_16_PC_ENGINE_CD,
            "trs-80-color-computer" to Platform.TRS_80_COLOR_COMPUTER,
            "fm-7" to Platform.FM_7,
            "dragon-32-slash-64" to Platform.DRAGON_32_64,
            "apcw" to Platform.AMSTRAD_PCW,
            "tatung-einstein" to Platform.TATUNG_EINSTEIN,
            "thomson-mo5" to Platform.THOMSON_MO5,
            "nec-pc-6000-series" to Platform.NEC_PC_6000_SERIES,
            "commodore-cdtv" to Platform.COMMODORE_CDTV,
            "nintendo-dsi" to Platform.NINTENDO_DSI,
            "windows-mixed-reality" to Platform.WINDOWS_MIXED_REALITY,
            "oculus-vr" to Platform.OCULUS_VR,
            "steam-vr" to Platform.STEAMVR,
            "daydream" to Platform.DAYDREAM,
            "psvr" to Platform.PLAYSTATION_VR,
            "pokemon-mini" to Platform.POKEMON_MINI,
            "ps5" to Platform.PLAYSTATION_5,
            "series-x-s" to Platform.XBOX_SERIES_X_S,
            "stadia" to Platform.GOOGLE_STADIA,
            "duplicate-stadia" to Platform.DUPLICATE_STADIA,
            "exidy-sorcerer" to Platform.EXIDY_SORCERER,
            "sol-20" to Platform.SOL_20,
            "dvd-player" to Platform.DVD_PLAYER,
            "blu-ray-player" to Platform.BLU_RAY_PLAYER,
            "zeebo" to Platform.ZEEBO,
            "pc-fx" to Platform.PC_FX,
            "satellaview" to Platform.SATELLAVIEW,
            "g-and-w" to Platform.GAME_AND_WATCH,
            "playdia" to Platform.PLAYDIA,
            "evercade" to Platform.EVERCADE,
            "sega-pico" to Platform.SEGA_PICO,
            "ooparts" to Platform.OOPARTS,
            "sinclair-zx81" to Platform.SINCLAIR_ZX81,
            "sharp-mz-2200" to Platform.SHARP_MZ_2200,
            "epoch-cassette-vision" to Platform.EPOCH_CASSETTE_VISION,
            "epoch-super-cassette-vision" to Platform.EPOCH_SUPER_CASSETTE_VISION,
            "plug-and-play" to Platform.PLUG_AND_PLAY,
            "gamate" to Platform.GAMATE,
            "game-dot-com" to Platform.GAME_COM,
            "casio-loopy" to Platform.CASIO_LOOPY,
            "playdate" to Platform.PLAYDATE,
            "intellivision-amico" to Platform.INTELLIVISION_AMICO,
            "oculus-quest" to Platform.OCULUS_QUEST,
            "oculus-rift" to Platform.OCULUS_RIFT,
            "meta-quest-2" to Platform.META_QUEST_2,
            "oculus-go" to Platform.OCULUS_GO,
            "gear-vr" to Platform.GEAR_VR,
            "airconsole" to Platform.AIRCONSOLE,
            "psvr2" to Platform.PLAYSTATION_VR2,
            "windows-mobile" to Platform.WINDOWS_MOBILE,
            "sinclair-ql" to Platform.SINCLAIR_QL,
            "hyperscan" to Platform.HYPERSCAN,
            "mega-duck-slash-cougar-boy" to Platform.MEGA_DUCK_COUGAR_BOY,
            "legacy-computer" to Platform.LEGACY_COMPUTER,
            "atari-jaguar-cd" to Platform.ATARI_JAGUAR_CD,
            "handheld" to Platform.HANDHELD_ELECTRONIC_LCD,
            "leapster" to Platform.LEAPSTER,
            "leapster-explorer-slash-leadpad-explorer" to Platform.LEAPSTER_EXPLORER_LEADPAD_EXPLORER,
            "leaptv" to Platform.LEAPTV,
            "watara-slash-quickshot-supervision" to Platform.WATARA_QUICKSHOT_SUPERVISION,
            "64dd" to Platform._64DD,
            "palm-os" to Platform.PALM_OS,
            "arduboy" to Platform.ARDUBOY,
            "vsmile" to Platform.V_SMILE,
            "visual-memory-unit-slash-visual-memory-system" to Platform.VISUAL_MEMORY_UNIT_VISUAL_MEMORY_SYSTEM,
            "pocketstation" to Platform.POCKETSTATION,
            "meta-quest-3" to Platform.META_QUEST_3,
            "visionos" to Platform.VISIONOS,
            "arcadia-2001" to Platform.ARCADIA_2001,
            "gizmondo" to Platform.GIZMONDO,
            "r-zone" to Platform.R_ZONE,
            "apple-pippin" to Platform.APPLE_PIPPIN,
            "panasonic-jungle" to Platform.PANASONIC_JUNGLE,
            "panasonic-m2" to Platform.PANASONIC_M2,
            "terebikko-slash-see-n-say-video-phone" to Platform.TEREBIKKO_SEE_N_SAY_VIDEO_PHONE,
            "super-acan" to Platform.SUPER_ACAN,
            "tomy-tutor-slash-pyuta-slash-grandstand-tutor" to Platform.TOMY_TUTOR_PYUTA_GRANDSTAND_TUTOR,
            "sega-cd-32x" to Platform.SEGA_CD_32X,
            "digiblast" to Platform.DIGIBLAST,
            "laseractive" to Platform.LASERACTIVE,
            "uzebox" to Platform.UZEBOX,
            "elektor-tv-games-computer" to Platform.ELEKTOR_TV_GAMES_COMPUTER,
            "gx4000" to Platform.AMSTRAD_GX4000,
            "advanced-pico-beena" to Platform.ADVANCED_PICO_BEENA,
            "switch-2" to Platform.NINTENDO_SWITCH_2,
            "polymega" to Platform.POLYMEGA,
            "e-reader-slash-card-e-reader" to Platform.E_READER_CARD_E_READER
        )

        /**
         * Map from Gameyfin Platform enum to IGDB platform slug
         */
        private val gameyfinToIgdbMap: Map<Platform, String> =
            igdbToGameyfinMap.entries.associateBy({ it.value }, { it.key })

        /**
         * Convert an IGDB platform slug to a Gameyfin Platform enum
         * @param igdbPlatformSlug The IGDB platform slug
         * @return The corresponding Platform enum value, or null if not found
         */
        fun toGameyfin(igdbPlatformSlug: String): Platform {
            val platform = igdbToGameyfinMap[igdbPlatformSlug]
                ?: throw IllegalArgumentException("Could not map IGDB platform with slug '$igdbPlatformSlug' to Gameyfin Platform")
            return platform
        }

        /**
         * Convert multiple IGDB platform slugs to Gameyfin Platform enums
         * @param igdbPlatformSlugs Collection of IGDB platform slugs
         * @return Set of Platform enums (unmapped slugs are filtered out)
         */
        fun toGameyfin(igdbPlatformSlugs: Collection<String>): Set<Platform> {
            return igdbPlatformSlugs.map { toGameyfin(it) }.toSet()
        }

        /**
         * Convert a Gameyfin Platform enum to an IGDB platform slug
         * @param platform The Platform enum value
         * @return The corresponding IGDB platform slug, or null if not found
         */
        fun toIgdb(platform: Platform): String {
            val slug = gameyfinToIgdbMap[platform]
                ?: throw IllegalArgumentException("Could not map Gameyfin Platform '${platform.displayName}' to IGDB platform slug")
            return slug
        }

        /**
         * Convert multiple Gameyfin Platform enums to IGDB platform slugs
         * @param platforms Collection of Platform enums
         * @return Set of IGDB platform slugs (unmapped platforms are filtered out)
         */
        fun toIgdb(platforms: Collection<Platform>): Set<String> {
            return platforms.map { toIgdb(it) }.toSet()
        }

        /**
         * Get all IGDB platform slugs that have mappings
         * @return Set of all mapped IGDB platform slugs
         */
        fun getAllMappedIgdbSlugs(): Set<String> {
            return igdbToGameyfinMap.keys
        }
    }
}