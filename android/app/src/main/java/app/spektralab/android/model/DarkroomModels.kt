package app.spektralab.android.model

import org.json.JSONObject

enum class FilmCategory {
    COLOR_NEGATIVE,
    CINE_FILM,
    SLIDE_POSITIVE,
    BLACK_AND_WHITE
}

data class FilmStockSpec(
    val id: String,
    val nameEn: String,
    val nameZh: String,
    val subtitleEn: String,
    val subtitleZh: String,
    val iso: Int,
    val category: FilmCategory,
    val defaultPaperId: String,
    val edgeCode: String,
    val accentColorHex: Long
) {
    fun displayName(isZh: Boolean): String = if (isZh) nameZh else nameEn
    fun displaySubtitle(isZh: Boolean): String = if (isZh) subtitleZh else subtitleEn
}

data class PaperStockSpec(
    val id: String,
    val nameEn: String,
    val nameZh: String,
    val subtitleEn: String,
    val subtitleZh: String,
    val isBlackAndWhite: Boolean = false
) {
    fun displayName(isZh: Boolean): String = if (isZh) nameZh else nameEn
    fun displaySubtitle(isZh: Boolean): String = if (isZh) subtitleZh else subtitleEn
}

data class FilmFormatSpec(
    val id: String,
    val labelEn: String,
    val labelZh: String,
    val widthMm: Double,
    val isMediumFormat: Boolean = false
) {
    fun displayLabel(isZh: Boolean): String = if (isZh) labelZh else labelEn
}

data class CameraFilterSpec(
    val id: String,
    val labelEn: String,
    val labelZh: String,
    val swatchHex: Long
) {
    fun displayLabel(isZh: Boolean): String = if (isZh) labelZh else labelEn
}

object SpektraCatalog {
    val filmStocks: List<FilmStockSpec> = listOf(
        // Color Negative
        FilmStockSpec(
            id = "kodak_portra_400",
            nameEn = "Kodak Portra 400",
            nameZh = "柯达 Portra 400",
            subtitleEn = "Natural skin tones, wide exposure latitude",
            subtitleZh = "自然肤色还原，极宽曝光宽容度",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK PORTRA 400",
            accentColorHex = 0xFFE6A145
        ),
        FilmStockSpec(
            id = "kodak_portra_160",
            nameEn = "Kodak Portra 160",
            nameZh = "柯达 Portra 160",
            subtitleEn = "Ultra-fine grain, smooth portrait gradation",
            subtitleZh = "超细颗粒，柔和细腻人像阶调",
            iso = 160,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK PORTRA 160",
            accentColorHex = 0xFFD99B4E
        ),
        FilmStockSpec(
            id = "kodak_portra_800",
            nameEn = "Kodak Portra 800",
            nameZh = "柯达 Portra 800",
            subtitleEn = "High-speed tungsten/daylight versatility, rich saturation",
            subtitleZh = "高感光度，弱光层次饱满且色彩浓郁",
            iso = 800,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK PORTRA 800",
            accentColorHex = 0xFFE88D38
        ),
        FilmStockSpec(
            id = "kodak_portra_800_push1",
            nameEn = "Kodak Portra 800 (+1 Push)",
            nameZh = "柯达 Portra 800 (迫冲 +1档)",
            subtitleEn = "EI 1600 pushed development, punchier contrast",
            subtitleZh = "EI 1600 迫冲显影，更高反差与暖调颗粒",
            iso = 1600,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK PORTRA 800 +1",
            accentColorHex = 0xFFE07A2E
        ),
        FilmStockSpec(
            id = "kodak_portra_800_push2",
            nameEn = "Kodak Portra 800 (+2 Push)",
            nameZh = "柯达 Portra 800 (迫冲 +2档)",
            subtitleEn = "EI 3200 nightdark aesthetic, pronounced shadow warmth",
            subtitleZh = "EI 3200 夜景迫冲，浓郁暗部与颗粒质感",
            iso = 3200,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK PORTRA 800 +2",
            accentColorHex = 0xFFD46624
        ),
        FilmStockSpec(
            id = "kodak_gold_200",
            nameEn = "Kodak Gold 200",
            nameZh = "柯达 Gold 200 金卷",
            subtitleEn = "Classic golden warmth & nostalgic consumer color",
            subtitleZh = "经典金色暖调，浓郁怀旧胶片氛围",
            iso = 200,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK GOLD 200-8",
            accentColorHex = 0xFFF5B82E
        ),
        FilmStockSpec(
            id = "kodak_ultramax_400",
            nameEn = "Kodak UltraMax 400",
            nameZh = "柯达 UltraMax 400",
            subtitleEn = "Vibrant primaries, deep blues & energetic contrast",
            subtitleZh = "明快三原色，深邃蓝调与高反差街拍感",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK GC 400-8",
            accentColorHex = 0xFF3E8EDE
        ),
        FilmStockSpec(
            id = "kodak_ektar_100",
            nameEn = "Kodak Ektar 100",
            nameZh = "柯达 Ektar 100",
            subtitleEn = "Micro-fine grain, slide-like vivid landscape saturation",
            subtitleZh = "微米级超细颗粒，反转片般高饱和风光表现",
            iso = 100,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK EKTAR 100",
            accentColorHex = 0xFFE54B4B
        ),
        FilmStockSpec(
            id = "kodak_verita_200d",
            nameEn = "Kodak Aerocolor IV (Verita 200D)",
            nameZh = "柯达 Aerocolor IV (Verita 200D)",
            subtitleEn = "Unmasked clear base aerial negative, glowing reds",
            subtitleZh = "无色罩透明片基航空负片，独特通透红调与光晕",
            iso = 200,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK 2460 AEROCOLOR",
            accentColorHex = 0xFFEC5E5E
        ),
        FilmStockSpec(
            id = "fujifilm_pro_400h",
            nameEn = "Fujifilm Pro 400H",
            nameZh = "富士 Pro 400H",
            subtitleEn = "4th cyan-sensitive emulsion layer, airy pastel greens & cyan",
            subtitleZh = "第四感青乳剂层，标志性空气感青绿与柔美高光",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "fujifilm_crystal_archive_typeii",
            edgeCode = "FUJIFILM PRO 400H",
            accentColorHex = 0xFF34B38A
        ),
        FilmStockSpec(
            id = "fujifilm_xtra_400",
            nameEn = "Fujifilm Superia X-TRA 400",
            nameZh = "富士 Superia X-TRA 400",
            subtitleEn = "Cool emerald shadows, crisp magenta-green separation",
            subtitleZh = "冷调翠绿暗部，经典日系街拍色彩分离",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "fujifilm_crystal_archive_typeii",
            edgeCode = "FUJI S-400",
            accentColorHex = 0xFF2FA86E
        ),
        FilmStockSpec(
            id = "fujifilm_c200",
            nameEn = "Fujifilm Fujicolor C200",
            nameZh = "富士 Fujicolor C200",
            subtitleEn = "Balanced daylight negative with clean highlights",
            subtitleZh = "均衡日光彩色负片，清新高光与自然绿调",
            iso = 200,
            category = FilmCategory.COLOR_NEGATIVE,
            defaultPaperId = "fujifilm_crystal_archive_typeii",
            edgeCode = "FUJICOLOR 200",
            accentColorHex = 0xFF42B883
        ),

        // Cine / Motion Picture
        FilmStockSpec(
            id = "cinestill_800t",
            nameEn = "CineStill 800T",
            nameZh = "CineStill 800T 电影夜片",
            subtitleEn = "RemJet-free tungsten cine stock with signature red halation",
            subtitleZh = "去除防光晕层钨丝灯电影片，标志性霓虹红色光晕",
            iso = 800,
            category = FilmCategory.CINE_FILM,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "CINESTILL 800T",
            accentColorHex = 0xFFFF5252
        ),
        FilmStockSpec(
            id = "kodak_vision3_500t",
            nameEn = "Kodak Vision3 500T (5219)",
            nameZh = "柯达 Vision3 500T (5219)",
            subtitleEn = "Hollywood tungsten motion picture negative, ECN-2 latitude",
            subtitleZh = "好莱坞主力钨丝灯电影负片，极宽高光宽容度",
            iso = 500,
            category = FilmCategory.CINE_FILM,
            defaultPaperId = "kodak_2383",
            edgeCode = "EASTMAN 5219",
            accentColorHex = 0xFF4E9AF1
        ),
        FilmStockSpec(
            id = "kodak_vision3_250d",
            nameEn = "Kodak Vision3 250D (5207)",
            nameZh = "柯达 Vision3 250D (5207)",
            subtitleEn = "Daylight cinema negative, creamy highlight roll-off",
            subtitleZh = "日光型电影负片，丝滑高光滚降与电影感肤色",
            iso = 250,
            category = FilmCategory.CINE_FILM,
            defaultPaperId = "kodak_2383",
            edgeCode = "EASTMAN 5207",
            accentColorHex = 0xFFE5A93B
        ),
        FilmStockSpec(
            id = "kodak_vision3_200t",
            nameEn = "Kodak Vision3 200T (5213)",
            nameZh = "柯达 Vision3 200T (5213)",
            subtitleEn = "Fine-grain tungsten cinema negative",
            subtitleZh = "细颗粒钨丝灯电影负片，纯净暗部表现",
            iso = 200,
            category = FilmCategory.CINE_FILM,
            defaultPaperId = "kodak_2383",
            edgeCode = "EASTMAN 5213",
            accentColorHex = 0xFF5FA8D3
        ),
        FilmStockSpec(
            id = "kodak_vision3_50d",
            nameEn = "Kodak Vision3 50D (5203)",
            nameZh = "柯达 Vision3 50D (5203)",
            subtitleEn = "Ultra-fine daylight cinema stock, maximum resolving power",
            subtitleZh = "超细颗粒低感日光电影卷，极致解析力与色阶",
            iso = 50,
            category = FilmCategory.CINE_FILM,
            defaultPaperId = "kodak_2383",
            edgeCode = "EASTMAN 5203",
            accentColorHex = 0xFFF2C14E
        ),

        // Slide / Positive Reversal
        FilmStockSpec(
            id = "kodak_kodachrome_64",
            nameEn = "Kodak Kodachrome 64 (KR64)",
            nameZh = "柯达 Kodachrome 64 传奇反转片",
            subtitleEn = "K-14 archival reversal, museum-grade documentary color",
            subtitleZh = "K-14 显影传奇反转片，厚重纪实色彩与高锐度",
            iso = 64,
            category = FilmCategory.SLIDE_POSITIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODACHROME 64",
            accentColorHex = 0xFFD94E34
        ),
        FilmStockSpec(
            id = "kodak_ektachrome_100",
            nameEn = "Kodak Ektachrome E100",
            nameZh = "柯达 Ektachrome E100",
            subtitleEn = "Clean neutral whites, true-to-life E-6 slide transparency",
            subtitleZh = "纯净中性白场，真实通透 E-6 彩色反转片",
            iso = 100,
            category = FilmCategory.SLIDE_POSITIVE,
            defaultPaperId = "kodak_endura_premier",
            edgeCode = "KODAK E100",
            accentColorHex = 0xFF4A90E2
        ),
        FilmStockSpec(
            id = "fujifilm_velvia_100",
            nameEn = "Fujifilm Velvia 100 (RVP100)",
            nameZh = "富士 Velvia 100 (RVP100)",
            subtitleEn = "Ultra-high saturation & dramatic contrast for landscapes",
            subtitleZh = "超高色彩饱和度与深邃反差，风光摄影反转片标杆",
            iso = 100,
            category = FilmCategory.SLIDE_POSITIVE,
            defaultPaperId = "fujifilm_crystal_archive_typeii",
            edgeCode = "FUJI RVP 100",
            accentColorHex = 0xFF9B51E0
        ),
        FilmStockSpec(
            id = "fujifilm_provia_100f",
            nameEn = "Fujifilm Provia 100F (RDPIII)",
            nameZh = "富士 Provia 100F (RDPIII)",
            subtitleEn = "Reference-grade natural reversal slide with fine gradation",
            subtitleZh = "标准自然色彩反转片，极其顺滑的明暗过渡",
            iso = 100,
            category = FilmCategory.SLIDE_POSITIVE,
            defaultPaperId = "fujifilm_crystal_archive_typeii",
            edgeCode = "FUJI RDP III",
            accentColorHex = 0xFF2D9CDB
        ),

        // Black & White Silver Halide
        FilmStockSpec(
            id = "kodak_tri_x_400",
            nameEn = "Kodak Tri-X 400 (400TX)",
            nameZh = "柯达 Tri-X 400 经典黑白",
            subtitleEn = "Iconic cubic-grain photojournalism B&W with deep blacks",
            subtitleZh = "经典立方体银盐颗粒，浑厚黑场与人文纪实反差",
            iso = 400,
            category = FilmCategory.BLACK_AND_WHITE,
            defaultPaperId = "ilford_multigrade_iv_rc",
            edgeCode = "KODAK 400TX",
            accentColorHex = 0xFFBDBDBD
        ),
        FilmStockSpec(
            id = "kodak_tmax_100",
            nameEn = "Kodak T-Max 100 (100TMX)",
            nameZh = "柯达 T-Max 100 扁平颗粒黑白",
            subtitleEn = "Tabular T-Grain architecture, extreme sharpness & tonal range",
            subtitleZh = "T-Grain 扁平结晶颗粒，极高锐度与连续灰阶",
            iso = 100,
            category = FilmCategory.BLACK_AND_WHITE,
            defaultPaperId = "ilford_multigrade_iv_rc",
            edgeCode = "KODAK 100TMX",
            accentColorHex = 0xFF9E9E9E
        ),
        FilmStockSpec(
            id = "ilford_hp5_plus_400",
            nameEn = "Ilford HP5 Plus 400",
            nameZh = "伊尔福 HP5 Plus 400",
            subtitleEn = "Classic British panchromatic B&W with rich midtone separation",
            subtitleZh = "英伦经典全色黑白胶片，极其丰富的中间调层次",
            iso = 400,
            category = FilmCategory.BLACK_AND_WHITE,
            defaultPaperId = "ilford_multigrade_iv_rc",
            edgeCode = "ILFORD HP5 PLUS",
            accentColorHex = 0xFFD6D6D6
        ),
        FilmStockSpec(
            id = "fujifilm_neopan_acros_100_ii",
            nameEn = "Fujifilm Neopan Acros 100 II",
            nameZh = "富士 Neopan Acros 100 II",
            subtitleEn = "Super Fine-Σ grain technology, dimensional highlight micro-contrast",
            subtitleZh = "Super Fine-Σ 超细微粒技术，立体感极强的高光微反差",
            iso = 100,
            category = FilmCategory.BLACK_AND_WHITE,
            defaultPaperId = "ilford_multigrade_iv_rc",
            edgeCode = "ACROS 100 II",
            accentColorHex = 0xFFAFAFAF
        )
    )

    val paperStocks: List<PaperStockSpec> = listOf(
        PaperStockSpec(
            id = "kodak_endura_premier",
            nameEn = "Kodak Endura Premier",
            nameZh = "柯达 Endura Premier 专业彩扩相纸",
            subtitleEn = "Standard darkroom RA-4 paper with rich D-max & clean whites",
            subtitleZh = "标准暗房 RA-4 专业相纸，浓郁最大黑度与纯净纸白"
        ),
        PaperStockSpec(
            id = "fujifilm_crystal_archive_typeii",
            nameEn = "Fujifilm Crystal Archive Type II",
            nameZh = "富士晶彩二代 (Crystal Archive II)",
            subtitleEn = "Crisp brightness, delicate cyan/green reproduction",
            subtitleZh = "明快通透，对青绿与肤色有极佳还原力"
        ),
        PaperStockSpec(
            id = "kodak_portra_endura",
            nameEn = "Kodak Portra Endura",
            nameZh = "柯达 Portra Endura 人像相纸",
            subtitleEn = "Lower contrast RA-4 paper tailored for portrait gradation",
            subtitleZh = "低反差柔和阶调相纸，专为人像肤色过渡设计"
        ),
        PaperStockSpec(
            id = "kodak_supra_endura",
            nameEn = "Kodak Supra Endura",
            nameZh = "柯达 Supra Endura 商业相纸",
            subtitleEn = "Balanced commercial color paper with neutral gray scale",
            subtitleZh = "均衡商业冲印相纸，中性灰阶准确"
        ),
        PaperStockSpec(
            id = "kodak_ultra_endura",
            nameEn = "Kodak Ultra Endura",
            nameZh = "柯达 Ultra Endura 高反差相纸",
            subtitleEn = "High-contrast, punchy saturation RA-4 exhibition paper",
            subtitleZh = "高反差、高饱和度展览级 RA-4 彩色相纸"
        ),
        PaperStockSpec(
            id = "kodak_ektacolor_edge",
            nameEn = "Kodak Ektacolor Edge",
            nameZh = "柯达 Ektacolor Edge 经典冲印纸",
            subtitleEn = "Classic optical minilab print look with warm highlights",
            subtitleZh = "经典光学彩扩机冲印质感，温暖高光"
        ),
        PaperStockSpec(
            id = "kodak_2383",
            nameEn = "Kodak Vision Color Print 2383",
            nameZh = "柯达 2383 电影拷贝正片",
            subtitleEn = "Theatrical cinema release print stock, iconic teal-orange curve",
            subtitleZh = "院线电影发行拷贝正片，标志性电影感S曲线与色彩"
        ),
        PaperStockSpec(
            id = "kodak_2393",
            nameEn = "Kodak Vision Premier 2393",
            nameZh = "柯达 2393 旗舰电影拷贝正片",
            subtitleEn = "Premier theatrical print stock with deeper shadow density",
            subtitleZh = "旗舰级院线电影拷贝正片，更深邃的暗部密度"
        ),
        PaperStockSpec(
            id = "ilford_multigrade_iv_rc",
            nameEn = "Ilford Multigrade IV RC Deluxe",
            nameZh = "伊尔福 Multigrade IV 可变反差黑白相纸",
            subtitleEn = "Variable-contrast silver gelatin B&W paper (Y/M filtration controls Grade 00–5)",
            subtitleZh = "可变反差黑白银盐相纸（调节 Y/M 滤色片可无级控制 00–5 号反差）",
            isBlackAndWhite = true
        )
    )

    val filmFormats: List<FilmFormatSpec> = listOf(
        FilmFormatSpec("half_frame", "Half-Frame (18×24mm)", "半格 135 (18×24mm)", 24.0),
        FilmFormatSpec("135", "35mm Standard (36×24mm)", "135 全画幅 (36×24mm)", 36.0),
        FilmFormatSpec("xpan", "XPan Panoramic (65×24mm)", "宽幅 XPan (65×24mm)", 65.0),
        FilmFormatSpec("645", "Medium Format 645 (56mm)", "120 中画幅 645 (56mm)", 56.0, isMediumFormat = true),
        FilmFormatSpec("6x6", "Medium Format 6×6 (56mm)", "120 中画幅 6×6 (56mm)", 56.0, isMediumFormat = true),
        FilmFormatSpec("6x7", "Medium Format 6×7 (70mm)", "120 中画幅 6×7 (70mm)", 70.0, isMediumFormat = true),
        FilmFormatSpec("6x9", "Medium Format 6×9 (84mm)", "120 中画幅 6×9 (84mm)", 84.0, isMediumFormat = true)
    )

    val cameraFilters: List<CameraFilterSpec> = listOf(
        CameraFilterSpec("", "None (Clear)", "无滤镜", 0xFF9E9E9E),
        CameraFilterSpec("w8", "Wratten #8 Yellow", "#8 黄镜 (压暗蓝天)", 0xFFF2C94C),
        CameraFilterSpec("w11", "Wratten #11 Yellow-Green", "#11 黄绿 (人像肤色)", 0xFF8CD754),
        CameraFilterSpec("w12", "Wratten #12 Minus-Blue", "#12 减蓝黄镜", 0xFFE6B800),
        CameraFilterSpec("w15", "Wratten #15 Deep Yellow", "#15 深黄 (高反差云层)", 0xFFF2994A),
        CameraFilterSpec("w21", "Wratten #21 Orange", "#21 橙镜 (建筑/风光)", 0xFFEB7A34),
        CameraFilterSpec("w25", "Wratten #25 Red", "#25 红镜 (戏剧性黑天)", 0xFFEB5757),
        CameraFilterSpec("w47", "Wratten #47 Blue", "#47 蓝镜 (强化薄雾)", 0xFF2F80ED),
        CameraFilterSpec("w58", "Wratten #58 Green", "#58 绿镜 (植被细节)", 0xFF27AE60)
    )

    fun findFilm(id: String): FilmStockSpec =
        filmStocks.firstOrNull { it.id == id } ?: filmStocks.first()

    fun findPaper(id: String): PaperStockSpec =
        paperStocks.firstOrNull { it.id == id } ?: paperStocks.first()
}

data class DarkroomParams(
    // Film & Paper
    val filmStock: String = "kodak_portra_400",
    val printStock: String = "kodak_endura_premier",
    val filmFormatMm: Double = 36.0,

    // Camera & Exposure
    val autoExposure: Boolean = true,
    val autoExposureMethod: String = "center_weighted",
    val exposureCompensationEv: Double = 0.0,
    val densityCurveGamma: Double = 1.0,
    val cameraFilter: String = "",
    val lensBlurUm: Double = 0.0,

    // Optics, Halation, DIR & Grain
    val halationActive: Boolean = true,
    val antihalationRemoved: Boolean = false,
    val halationAmount: Double = 1.0,
    val halationScatterAmount: Double = 1.0,
    val halationBoostEv: Double = 0.0,
    val dirCouplersActive: Boolean = true,
    val dirCouplersAmount: Double = 1.0,
    val grainActive: Boolean = true,
    val grainSublayersActive: Boolean = true,
    val grainAmount: Double = 1.0,

    // Enlarger & Print
    val printExposure: Double = 1.0,
    val filterShiftScale: Double = 10.0,
    val yFilterShift: Double = 0.0,
    val mFilterShift: Double = 0.0,
    val preflashExposure: Double = 0.0,
    val glareActive: Boolean = false,
    val glareAmount: Double = 0.0,
    val scannerBlackCorrection: Boolean = false,
    val scannerWhiteCorrection: Boolean = false,

    // Geometry
    val geometryQuarterTurns: Int = 0,
    val geometryFlipH: Boolean = false,
    val geometryFlipV: Boolean = false,
    val geometryRotationDeg: Double = 0.0,
    val aspectCropPreset: String = "original", // original, 3:2, 4:3, 1:1, 65:24

    // Android Film Edge Rebate & Quartz Date Back (Post-Compositor)
    val filmEdgeActive: Boolean = false,
    val filmEdgeFrameNumber: Int = 24,
    val dateBackActive: Boolean = false,
    val dateBackText: String = "'26 11 04"
) {
    /**
     * Converts engine-relevant parameters into the flat JSON object expected by `spk_set_params`.
     */
    fun toEngineJson(previewLongEdge: Int = 1440): String {
        val (cropX, cropY, cropW, cropH) = computeCropRect(aspectCropPreset)
        return JSONObject().apply {
            put("film_stock", filmStock)
            put("print_stock", printStock)
            put("film_format_mm", filmFormatMm)
            put("auto_exposure", autoExposure)
            put("auto_exposure_method", autoExposureMethod)
            put("exposure_compensation_ev", exposureCompensationEv)
            put("density_curve_gamma", densityCurveGamma)
            put("camera_filter", cameraFilter)
            put("lens_blur_um", lensBlurUm)

            put("halation_active", halationActive)
            put("antihalation_removed", antihalationRemoved)
            put("halation_amount", halationAmount)
            put("halation_scatter_amount", halationScatterAmount)
            put("halation_boost_ev", halationBoostEv)

            put("dir_couplers_active", dirCouplersActive)
            put("dir_couplers_amount", dirCouplersAmount)

            put("grain_active", grainActive)
            put("grain_sublayers_active", grainSublayersActive)
            put("grain_amount", grainAmount)

            put("print_exposure", printExposure)
            put("filter_shift_scale", filterShiftScale)
            put("y_filter_shift", yFilterShift)
            put("m_filter_shift", mFilterShift)
            put("preflash_exposure", preflashExposure)
            put("glare_active", glareActive)
            put("glare_amount", glareAmount)
            put("scanner_black_correction", scannerBlackCorrection)
            put("scanner_white_correction", scannerWhiteCorrection)

            put("geometry_quarter_turns", geometryQuarterTurns)
            put("geometry_flip_h", geometryFlipH)
            put("geometry_flip_v", geometryFlipV)
            put("geometry_rotation_deg", geometryRotationDeg)
            put("geometry_crop_x", cropX)
            put("geometry_crop_y", cropY)
            put("geometry_crop_w", cropW)
            put("geometry_crop_h", cropH)

            put("preview_long_edge", previewLongEdge)
            put("striped", true)
            put("strip_rows", 512)
        }.toString()
    }

    /**
     * Returns true if only PRINT-layer parameters changed between `previous` and `this`,
     * enabling the engine's fast cached negative reprint path (`spk_reprint`).
     */
    fun onlyPrintLayerChangedComparedTo(previous: DarkroomParams?): Boolean {
        if (previous == null) return false
        return filmStock == previous.filmStock &&
            filmFormatMm == previous.filmFormatMm &&
            autoExposure == previous.autoExposure &&
            autoExposureMethod == previous.autoExposureMethod &&
            exposureCompensationEv == previous.exposureCompensationEv &&
            densityCurveGamma == previous.densityCurveGamma &&
            cameraFilter == previous.cameraFilter &&
            lensBlurUm == previous.lensBlurUm &&
            halationActive == previous.halationActive &&
            antihalationRemoved == previous.antihalationRemoved &&
            halationAmount == previous.halationAmount &&
            halationScatterAmount == previous.halationScatterAmount &&
            halationBoostEv == previous.halationBoostEv &&
            dirCouplersActive == previous.dirCouplersActive &&
            dirCouplersAmount == previous.dirCouplersAmount &&
            grainActive == previous.grainActive &&
            grainSublayersActive == previous.grainSublayersActive &&
            grainAmount == previous.grainAmount &&
            geometryQuarterTurns == previous.geometryQuarterTurns &&
            geometryFlipH == previous.geometryFlipH &&
            geometryFlipV == previous.geometryFlipV &&
            geometryRotationDeg == previous.geometryRotationDeg &&
            aspectCropPreset == previous.aspectCropPreset
    }

    companion object {
        fun computeCropRect(preset: String): DoubleArray {
            // Returns normalized [x, y, w, h] centered crop approximation
            return when (preset) {
                "1:1" -> doubleArrayOf(0.1667, 0.0, 0.6666, 1.0)
                "4:3" -> doubleArrayOf(0.0555, 0.0, 0.8889, 1.0)
                "65:24" -> doubleArrayOf(0.0, 0.222, 1.0, 0.556)
                else -> doubleArrayOf(0.0, 0.0, 1.0, 1.0)
            }
        }
    }
}

data class PhotoMetadata(
    val filePath: String = "",
    val displayName: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val isRaw: Boolean = false,
    val make: String = "",
    val model: String = "",
    val lens: String = "",
    val iso: Double = 0.0,
    val shutterS: Double = 0.0,
    val fNumber: Double = 0.0,
    val focalLengthMm: Double = 0.0,
    val wbTemperatureK: Double = 5500.0,
    val wbTint: Double = 0.0,
    val rawMode: String = "compatible16"
) {
    fun formatExifSummary(): String {
        val parts = mutableListOf<String>()
        val cam = "$make $model".trim()
        if (cam.isNotEmpty()) parts.add(cam)
        if (focalLengthMm > 0) parts.add("${focalLengthMm.toInt()}mm")
        if (fNumber > 0) parts.add("f/${String.format("%.1f", fNumber)}")
        if (shutterS > 0) {
            val s = if (shutterS < 1.0) "1/${Math.round(1.0 / shutterS)}s" else "${String.format("%.1f", shutterS)}s"
            parts.add(s)
        }
        if (iso > 0) parts.add("ISO ${iso.toInt()}")
        if (width > 0 && height > 0) parts.add("${width}×${height}")
        return parts.joinToString(" · ")
    }
}

data class HistogramData(
    val r: IntArray = IntArray(64),
    val g: IntArray = IntArray(64),
    val b: IntArray = IntArray(64),
    val l: IntArray = IntArray(64)
)

data class ExportConfig(
    val format: String = "jpeg",          // tiff16, tiff8, png, jpeg
    val colorSpace: String = "sRGB",      // sRGB, Display P3, ProPhoto RGB
    val resolutionTier: String = "full",  // full, preview
    val jpegQuality: Int = 95,
    val includeFilmEdgeAndDate: Boolean = true
)

data class ParityGateItem(
    val id: String,
    val title: String,
    val passed: Boolean,
    val maxError: Double,
    val tolerance: Double,
    val elapsedMs: Double,
    val detail: String
)

data class ParitySuiteReport(
    val overallPassed: Boolean = false,
    val gpuDeviceName: String = "",
    val buildStamp: String = "",
    val gates: List<ParityGateItem> = emptyList()
)
