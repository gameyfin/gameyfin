package org.gameyfin.pluginapi.gamemetadata

/**
 * Enum representing the platforms of a game.
 * Source: https://api.igdb.com/v4/platforms
 */
@Suppress("Unused", "EnumEntryName")
enum class Platform(
    val displayName: String
) {
    // 0-9 (prefixed with underscore because TypeScript enum entries cannot start with a number)
    _1292_ADVANCED_PROGRAMMABLE_VIDEO_SYSTEM("1292 Advanced Programmable Video System"),
    _3DO_INTERACTIVE_MULTIPLAYER("3DO Interactive Multiplayer"),
    _64DD("64DD"),

    // A
    ACORN_ARCHIMEDES("Acorn Archimedes"),
    ACORN_ELECTRON("Acorn Electron"),
    ADVANCED_PICO_BEENA("Advanced Pico Beena"),
    AIRCONSOLE("AirConsole"),

    AMAZON_FIRE_TV("Amazon Fire TV"),
    AMIGA("Amiga"),
    AMIGA_CD32("Amiga CD32"),
    AMSTRAD_CPC("Amstrad CPC"),
    AMSTRAD_GX4000("Amstrad GX4000"),
    AMSTRAD_PCW("Amstrad PCW"),
    ANDROID("Android"),
    ANALOGUE_ELECTRONICS("Analogue electronics"),
    ARCADE("Arcade"),
    ARCADIA_2001("Arcadia 2001"),
    ARDUBOY("Arduboy"),
    APPLE_II("Apple II"),
    APPLE_IIGS("Apple IIGS"),
    APPLE_PIPPIN("Apple Pippin"),
    ATARI_2600("Atari 2600"),
    ATARI_5200("Atari 5200"),
    ATARI_7800("Atari 7800"),
    ATARI_8_BIT("Atari 8-bit"),
    ATARI_JAGUAR("Atari Jaguar"),
    ATARI_JAGUAR_CD("Atari Jaguar CD"),
    ATARI_LYNX("Atari Lynx"),
    ATARI_ST_STE("Atari ST/STE"),
    AY_3_8500("AY-3-8500"),
    AY_3_8603("AY-3-8603"),
    AY_3_8605("AY-3-8605"),
    AY_3_8606("AY-3-8606"),
    AY_3_8607("AY-3-8607"),
    AY_3_8610("AY-3-8610"),
    AY_3_8710("AY-3-8710"),
    AY_3_8760("AY-3-8760"),

    // B
    BALLY_ASTROCADE("Bally Astrocade"),
    BBC_MICROCOMPUTER_SYSTEM("BBC Microcomputer System"),
    BLACKBERRY_OS("BlackBerry OS"),
    BLU_RAY_PLAYER("Blu-ray Player"),

    // C
    CALL_A_COMPUTER_TIME_SHARED_MAINFRAME_COMPUTER_SYSTEM("Call-A-Computer time-shared mainframe computer system"),
    CASIO_LOOPY("Casio Loopy"),
    CDC_CYBER_70("CDC Cyber 70"),
    COLECOVISION("ColecoVision"),
    COMMODORE_16("Commodore 16"),
    COMMODORE_C64_128_MAX("Commodore C64/128/MAX"),
    COMMODORE_CDTV("Commodore CDTV"),
    COMMODORE_PET("Commodore PET"),
    COMMODORE_PLUS_4("Commodore Plus/4"),
    COMMODORE_VIC_20("Commodore VIC-20"),

    // D
    DAYDREAM("Daydream"),
    DEC_GT40("DEC GT40"),
    DIGIBLAST("Digiblast"),
    DONNER_MODEL_30("Donner Model 30"),
    DOS("DOS"),
    DRAGON_32_64("Dragon 32/64"),
    DREAMCAST("Dreamcast"),
    DUPLICATE_STADIA("DUPLICATE Stadia"),
    DVD_PLAYER("DVD Player"),

    // E
    EDSAC("EDSAC"),
    ELEKTOR_TV_GAMES_COMPUTER("Elektor TV Games Computer"),
    EPOCH_CASSETTE_VISION("Epoch Cassette Vision"),
    EPOCH_SUPER_CASSETTE_VISION("Epoch Super Cassette Vision"),
    EVERCADE("Evercade"),
    EXIDY_SORCERER("Exidy Sorcerer"),
    E_READER_CARD_E_READER("e-Reader / Card-e Reader"),

    // F
    FAMILY_COMPUTER("Family Computer"),
    FAMILY_COMPUTER_DISK_SYSTEM("Family Computer Disk System"),
    FAIRCHILD_CHANNEL_F("Fairchild Channel F"),
    FERRANTI_NIMROD_COMPUTER("Ferranti Nimrod Computer"),
    FM_TOWNS("FM Towns"),
    FM_7("FM-7"),

    // G
    GAME_AND_WATCH("Game & Watch"),
    GAMATE("Gamate"),
    GAME_BOY("Game Boy"),
    GAME_BOY_ADVANCE("Game Boy Advance"),
    GAME_BOY_COLOR("Game Boy Color"),
    GAME_COM("Game.com"),
    GEAR_VR("Gear VR"),
    GIZMONDO("Gizmondo"),
    GOOGLE_STADIA("Google Stadia"),

    // H
    HANDHELD_ELECTRONIC_LCD("Handheld Electronic LCD"),
    HP_2100("HP 2100"),
    HP_3000("HP 3000"),
    HYPER_NEO_GEO_64("Hyper Neo Geo 64"),
    HYPERSCAN("HyperScan"),

    // I
    IMLAC_PDS_1("Imlac PDS-1"),
    INTELLIVISION("Intellivision"),
    INTELLIVISION_AMICO("Intellivision Amico"),
    IOS("iOS"),

    // L
    LASERACTIVE("LaserActive"),
    LEAPSTER("Leapster"),
    LEAPSTER_EXPLORER_LEADPAD_EXPLORER("Leapster Explorer/LeadPad Explorer"),
    LEAPTV("LeapTV"),
    LEGACY_COMPUTER("Legacy Computer"),
    LEGACY_MOBILE_DEVICE("Legacy Mobile Device"),
    LINUX("Linux"),

    // M
    MAC("Mac"),
    MEGA_DUCK_COUGAR_BOY("Mega Duck/Cougar Boy"),
    META_QUEST_2("Meta Quest 2"),
    META_QUEST_3("Meta Quest 3"),
    MICROCOMPUTER("Microcomputer"),
    MICROVISION("Microvision"),
    MSX("MSX"),
    MSX2("MSX2"),

    // N
    N_GAGE("N-Gage"),
    NEC_PC_6000_SERIES("NEC PC-6000 Series"),
    NEO_GEO_AES("Neo Geo AES"),
    NEO_GEO_CD("Neo Geo CD"),
    NEO_GEO_MVS("Neo Geo MVS"),
    NEO_GEO_POCKET("Neo Geo Pocket"),
    NEO_GEO_POCKET_COLOR("Neo Geo Pocket Color"),
    NEW_NINTENDO_3DS("New Nintendo 3DS"),
    NINTENDO_3DS("Nintendo 3DS"),
    NINTENDO_64("Nintendo 64"),
    NINTENDO_DS("Nintendo DS"),
    NINTENDO_DSI("Nintendo DSi"),
    NINTENDO_ENTERTAINMENT_SYSTEM("Nintendo Entertainment System"),
    NINTENDO_GAMECUBE("Nintendo GameCube"),
    NINTENDO_SWITCH("Nintendo Switch"),
    NINTENDO_SWITCH_2("Nintendo Switch 2"),
    NUON("Nuon"),

    // O
    OCULUS_GO("Oculus Go"),
    OCULUS_QUEST("Oculus Quest"),
    OCULUS_RIFT("Oculus Rift"),
    OCULUS_VR("Oculus VR"),
    ODYSSEY("Odyssey"),
    ODYSSEY_2_VIDEOPAC_G7000("Odyssey 2 / Videopac G7000"),
    ONLIVE_GAME_SYSTEM("OnLive Game System"),
    OOPARTS("OOParts"),
    OUYA("Ouya"),

    // P
    PALM_OS("Palm OS"),
    PANASONIC_JUNGLE("Panasonic Jungle"),
    PANASONIC_M2("Panasonic M2"),
    PC_MICROSOFT_WINDOWS("PC (Microsoft Windows)"),
    PC_50X_FAMILY("PC-50X Family"),
    PC_8800_SERIES("PC-8800 Series"),
    PC_9800_SERIES("PC-9800 Series"),
    PC_ENGINE_SUPERGRAFX("PC Engine SuperGrafx"),
    PC_FX("PC-FX"),
    PDP_1("PDP-1"),
    PDP_7("PDP-7"),
    PDP_8("PDP-8"),
    PDP_10("PDP-10"),
    PDP_11("PDP-11"),
    PHILIPS_CD_I("Philips CD-i"),
    PLATO("PLATO"),
    PLAYDATE("Playdate"),
    PLAYDIA("Playdia"),
    PLAYSTATION("PlayStation"),
    PLAYSTATION_2("PlayStation 2"),
    PLAYSTATION_3("PlayStation 3"),
    PLAYSTATION_4("PlayStation 4"),
    PLAYSTATION_5("PlayStation 5"),
    PLAYSTATION_PORTABLE("PlayStation Portable"),
    PLAYSTATION_VITA("PlayStation Vita"),
    PLAYSTATION_VR("PlayStation VR"),
    PLAYSTATION_VR2("PlayStation VR2"),
    PLUG_AND_PLAY("Plug & Play"),
    POCKETSTATION("PocketStation"),
    POKEMON_MINI("Pokémon mini"),
    POLYMEGA("Polymega"),

    // R
    R_ZONE("R-Zone"),

    // S
    SATELLAVIEW("Satellaview"),
    SDS_SIGMA_7("SDS Sigma 7"),
    SEGA_32X("Sega 32X"),
    SEGA_CD("Sega CD"),
    SEGA_CD_32X("Sega CD 32X"),
    SEGA_GAME_GEAR("Sega Game Gear"),
    SEGA_MASTER_SYSTEM_MARK_III("Sega Master System/Mark III"),
    SEGA_MEGA_DRIVE_GENESIS("Sega Mega Drive/Genesis"),
    SEGA_PICO("Sega Pico"),
    SEGA_SATURN("Sega Saturn"),
    SG_1000("SG-1000"),
    SHARP_MZ_2200("Sharp MZ-2200"),
    SHARP_X1("Sharp X1"),
    SHARP_X68000("Sharp X68000"),
    SINCLAIR_QL("Sinclair QL"),
    SINCLAIR_ZX81("Sinclair ZX81"),
    SOL_20("Sol-20"),
    STEAMVR("SteamVR"),
    SUPER_ACAN("Super A'Can"),
    SUPER_FAMICOM("Super Famicom"),
    SUPER_NES_CD_ROM_SYSTEM("Super NES CD-ROM System"),
    SUPER_NINTENDO_ENTERTAINMENT_SYSTEM("Super Nintendo Entertainment System"),
    SWANCRYSTAL("SwanCrystal"),

    // T
    TAPWAVE_ZODIAC("Tapwave Zodiac"),
    TATUNG_EINSTEIN("Tatung Einstein"),
    TEXAS_INSTRUMENTS_TI_99("Texas Instruments TI-99"),
    TEREBIKKO_SEE_N_SAY_VIDEO_PHONE("Terebikko / See 'n Say Video Phone"),
    THOMSON_MO5("Thomson MO5"),
    TOMY_TUTOR_PYUTA_GRANDSTAND_TUTOR("Tomy Tutor / Pyuta / Grandstand Tutor"),
    TRS_80("TRS-80"),
    TRS_80_COLOR_COMPUTER("TRS-80 Color Computer"),
    TURBOGRAFX_16_PC_ENGINE_CD("Turbografx-16/PC Engine CD"),
    TURBOGRAFX_16_PC_ENGINE("TurboGrafx-16/PC Engine"),

    // U
    UZEBOX("Uzebox"),

    // V
    V_SMILE("V.Smile"),
    VC_4000("VC 4000"),
    VECTREX("Vectrex"),
    VIRTUAL_BOY("Virtual Boy"),
    VIRTUAL_CONSOLE("Virtual Console"),
    VISIONOS("visionOS"),
    VISUAL_MEMORY_UNIT_VISUAL_MEMORY_SYSTEM("Visual Memory Unit / Visual Memory System"),

    // W
    WATARA_QUICKSHOT_SUPERVISION("Watara/QuickShot Supervision"),
    WEB_BROWSER("Web browser"),
    WII("Wii"),
    WII_U("Wii U"),
    WINDOWS_MOBILE("Windows Mobile"),
    WINDOWS_MIXED_REALITY("Windows Mixed Reality"),
    WINDOWS_PHONE("Windows Phone"),
    WONDERSWAN("WonderSwan"),
    WONDERSWAN_COLOR("WonderSwan Color"),

    // X
    XBOX("Xbox"),
    XBOX_360("Xbox 360"),
    XBOX_ONE("Xbox One"),
    XBOX_SERIES_X_S("Xbox Series X|S"),

    // Z
    ZEEBO("Zeebo"),
    ZX_SPECTRUM("ZX Spectrum");
}