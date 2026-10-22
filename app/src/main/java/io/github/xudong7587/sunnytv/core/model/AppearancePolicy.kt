package io.github.xudong7587.sunnytv.core.model

data class AppearanceMaterial(val id:String,val name:String,val note:String,val highCost:Boolean=false,val glass:Boolean=false)
object AppearancePolicy {
    val catalog=listOf(
        AppearanceMaterial("soft","柔软浮雕","柔和凸起 · 按下微凹"),
        AppearanceMaterial("clear","清晰默认","中性实色 · 轻盈边线"),
        AppearanceMaterial("paper","纸页档案","暖纸纤维 · 内嵌压痕"),
        AppearanceMaterial("linen","亚麻织纹","细密交织 · 哑光触感"),
        AppearanceMaterial("glass","液态玻璃","背景折射 · 柔化采样 · 弧面亮边",true,true),
        AppearanceMaterial("frosted","雾面玻璃","平面雾面 · 斜切棱边",true,true),
        AppearanceMaterial("aurora","极光玻璃","通透玻璃 · 内蕴淡彩",true,true),
        AppearanceMaterial("duotone","深浅渐层","同一主色 · 深浅交融"),
        AppearanceMaterial("satin","缎光瓷面","斜向釉光 · 细腻亮面"),
        AppearanceMaterial("outline","精致描边","精细轮廓 · 轻薄厚度"),
        AppearanceMaterial("metal","拉丝金属","横向拉丝 · 冷银高光"),
        AppearanceMaterial("clay","陶土哑光","暖色颗粒 · 柔和厚度"),
        AppearanceMaterial("marble","大理石","自然石纹 · 温润釉面"),
        AppearanceMaterial("neon","霓虹暗夜","深色基底 · 边缘辉光",true),
        AppearanceMaterial("cutout","纸片拼贴","实色边线 · 错位硬影"),
        AppearanceMaterial("mesh","雾面色场","同色柔光 · 哑光色场"))
    val materials=catalog.map {it.id to it.name}
    val corners=listOf("tight" to "紧致","default" to "标准","round" to "圆润")
    private val legacyColors=listOf("brick","amber","celadon","blue","blue","violet","rose","amber","indigo","celadon")
    private val migrations=mapOf("green" to "celadon","mustard" to "amber","slate" to "blue",
        "catppuccin" to "rose","nord" to "blue","iris" to "violet","deepsea" to "blue","coast" to "celadon",
        "lava" to "brick","macaron" to "celadon","cedar" to "celadon","peach" to "brick","lavender" to "violet",
        "mist" to "blue","forest" to "celadon","honey" to "amber","oasis" to "amber","ruby" to "rose","nightviolet" to "violet")
    fun color(id:String,legacyIndex:Int=2,custom:Long=AppearanceColors.DEFAULT_CUSTOM):AppearanceColor =
        if(id=="custom") AppearanceColors.custom(custom) else AppearanceColors.find(migrations[id] ?: id)
            ?: AppearanceColors.find(if(id.isEmpty()) legacyColors.getOrElse(legacyIndex) {"celadon"} else "celadon")!!
    fun material(id:String)=catalog.firstOrNull {it.id==id}?.id ?: "clear"
    fun spec(id:String)=catalog.first {it.id==material(id)}
    fun corner(id:String)=id.takeIf {candidate->corners.any {it.first==candidate}} ?: "default"
    fun cornerScale(id:String)=when(corner(id)) {"tight"->.42f;"round"->1.9f;else->1f}
    fun allowsHighCostThemes(optIn:Boolean,lean:Boolean)=optIn && !lean
    fun blockedReason(id:String,optIn:Boolean,lean:Boolean,sdk:Int,hardware:Boolean=true):String? {
        val spec=spec(id)
        return when {
            !spec.highCost->null
            !optIn->"需开启高开销主题"
            lean->"低负载模式下不可用"
            spec.glass && (sdk<33 || !hardware)->"需 Android 13 及硬件加速"
            else->null
        }
    }
    fun effectiveMaterial(id:String,optIn:Boolean,lean:Boolean,sdk:Int,hardware:Boolean=true)=
        if(blockedReason(id,optIn,lean,sdk,hardware)==null) material(id) else "clear"
}
