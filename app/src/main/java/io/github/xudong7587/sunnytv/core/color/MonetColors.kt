package io.github.xudong7587.sunnytv.core.color

import io.github.xudong7587.sunnytv.core.color.mcu.dynamiccolor.ColorSpec.SpecVersion
import io.github.xudong7587.sunnytv.core.color.mcu.dynamiccolor.DynamicScheme
import io.github.xudong7587.sunnytv.core.color.mcu.hct.Hct
import io.github.xudong7587.sunnytv.core.color.mcu.scheme.*
import io.github.xudong7587.sunnytv.core.model.MonetPolicy

/** Google's audited HCT engine; the same scheme family/spec as the reference's Monet controller. */
object MonetColors {
    fun scheme(seed:Long,dark:Boolean,style:String,spec:SpecVersion=SpecVersion.SPEC_2025):DynamicScheme {
        val hct=Hct.fromInt(seed.toInt());val platform=DynamicScheme.Platform.PHONE
        return when(MonetPolicy.style(style)) {
            "neutral"->SchemeNeutral(hct,dark,0.0,spec,platform)
            "vibrant"->SchemeVibrant(hct,dark,0.0,spec,platform)
            "expressive"->SchemeExpressive(hct,dark,0.0,spec,platform)
            "monochrome"->SchemeMonochrome(hct,dark,0.0,spec,platform)
            "fidelity"->SchemeFidelity(hct,dark,0.0,spec,platform)
            "content"->SchemeContent(hct,dark,0.0,spec,platform)
            "rainbow"->SchemeRainbow(hct,dark,0.0,spec,platform)
            "fruit_salad"->SchemeFruitSalad(hct,dark,0.0,spec,platform)
            else->SchemeTonalSpot(hct,dark,0.0,spec,platform)
        }
    }
}
