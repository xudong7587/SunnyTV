package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import io.github.xudong7587.sunnytv.core.model.*

/** Native diagram of a material, following the small-card vocabulary of MediaIndex Next's picker.
 * A 42dp legend needs crisp edges, not the full-size panel's blur radius. No backdrop/effect layer.
 * The real material renderer remains in NativeAppearance/LiquidGlass and the larger review sample.
 */
@Composable internal fun MaterialOptionSwatch(spec:AppearanceMaterial,settings:AppSettings,modifier:Modifier=Modifier) {
    val seed=LocalSystemMonet.current.seed
    val p=remember(settings,seed) {palette(settings,seed)}
    Box(modifier.drawWithCache {
        val dark=p.background.luminance()<.3f
        val solid=if(dark) p.raised else Color.White
        val accent=p.accent
        val id=spec.id
        val radius=when(id) {"paper","cutout"->5f;"outline"->6f;else->10f}
        val card=Path().apply {addRoundRect(RoundRect(1f,1f,40f,39f,CornerRadius(radius)))}
        val vein=Path().apply {
            moveTo(7f,1f);cubicTo(30f,12f,4f,22f,25f,39f)
            moveTo(20f,1f);cubicTo(38f,16f,19f,30f,39f,35f)
        }
        val base=when(id) {
            "paper"->if(dark) mixSrgb(solid,Color(0xFFE8DCC0),.12f) else Color(0xFFE8DCC0)
            "linen"->mixSrgb(solid,Color(0xFFBAA584),if(dark) .12f else .32f)
            "clay"->mixSrgb(solid,Color(0xFFCB9D76),if(dark) .18f else .5f)
            "soft"->mixSrgb(p.background,accent,.10f)
            "neon"->Color(0xFF141B2A)
            else->solid
        }
        val fill=when(id) {
            "aurora","duotone"->Brush.linearGradient(listOf(mixSrgb(solid,accent,if(id=="duotone") .60f else .18f),
                mixSrgb(solid,p.accent3,if(id=="duotone") .36f else .10f)),Offset.Zero,Offset(42f,42f))
            "satin"->Brush.linearGradient(0f to solid,.15f to solid,.48f to mixSrgb(solid,accent,.24f),
                .52f to solid,1f to mixSrgb(solid,accent,.33f),start=Offset.Zero,end=Offset(30f,42f))
            "metal"->Brush.verticalGradient(listOf(mixSrgb(solid,p.text,.25f),solid,mixSrgb(solid,p.text,.18f),solid),endY=42f)
            "glass"->Brush.linearGradient(listOf(mixSrgb(solid,accent,.03f),mixSrgb(solid,accent,.07f),solid),Offset.Zero,Offset(42f,42f))
            "frosted"->Brush.linearGradient(listOf(mixSrgb(solid,accent,.05f),mixSrgb(solid,accent,.05f)))
            else->Brush.linearGradient(listOf(base,base))
        }
        val sheen=Brush.linearGradient(listOf(Color.White.copy(if(dark) .26f else .72f),Color.Transparent,Color.White.copy(.24f)),Offset.Zero,Offset(35f,42f))
        onDrawBehind {withTransform({scale(size.width/42f,size.height/42f,Offset.Zero)}) {
            if(id=="soft") {
                translate(-1f,-1f) {drawPath(card,Color.White.copy(if(dark) .08f else .7f))}
                translate(2f,3f) {drawPath(card,Color.Black.copy(if(dark) .25f else .12f))}
            } else if(id in setOf("paper","cutout","satin","glass")) {
                translate(if(id=="cutout") 2f else 0f,2f) {drawPath(card,if(id=="paper") Color(0xFFBCAF92) else p.text.copy(.12f))}
            }
            drawPath(card,fill)
            clipPath(card) {
                when(id) {
                    "linen"->{for(i in 1..10) {
                        drawLine(p.text.copy(.08f),Offset(i*4f,0f),Offset(i*4f,40f),.5f)
                        drawLine(p.text.copy(.08f),Offset(0f,i*4f),Offset(40f,i*4f),.5f)
                    }}
                    "paper","clay"->for(i in 0..35) {
                        val x=3f+(i*17%34);val y=3f+(i*11%32)
                        drawLine(p.text.copy(.09f),Offset(x,y),Offset(x+if(id=="paper") 2f else .5f,y+.4f),.5f)
                    }
                    "metal"->for(i in 1..17) drawLine(p.text.copy(.07f),Offset(0f,i*2f),Offset(42f,i*2f),.5f)
                    "marble"->drawPath(vein,accent.copy(.22f),style=Stroke(.7f))
                    "mesh"->drawCircle(accent.copy(.18f),25f,Offset(5f,5f))
                    "glass"->{
                        drawPath(card,sheen)
                        drawLine(Color.White.copy(.7f),Offset(5f,4f),Offset(24f,4f),1f)
                    }
                    "frosted"->{
                        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(.65f),p.text.copy(.12f)),Offset.Zero,Offset(42f,42f)),
                            Offset(2f,2f),Size(37f,36f),CornerRadius(9f),style=Stroke(3f))
                        drawRoundRect(p.border.copy(.4f),Offset(4f,4f),Size(33f,32f),CornerRadius(7f),style=Stroke(.6f))
                    }
                }
            }
            val border=when(id) {
                "outline","cutout","neon"->accent
                "paper"->if(dark) p.border else Color(0xFFC8BDA2)
                "glass","frosted","aurora","satin"->Color.White.copy(if(dark) .35f else .75f)
                "soft","duotone"->Color.Transparent
                else->p.border
            }
            drawPath(card,border,style=Stroke(1f))
            // Readable inner card + short line are consistent across every material.
            val inner=if(id=="neon") accent.copy(.28f) else mixSrgb(solid,accent,.20f)
            drawRoundRect(inner,Offset(8f,9f),Size(25f,12f),CornerRadius(4f))
            drawRoundRect(Color.White.copy(if(dark) .22f else .50f),Offset(8f,9f),Size(25f,12f),CornerRadius(4f),style=Stroke(.8f))
            drawRoundRect(accent.copy(.50f),Offset(8f,29f),Size(16f,3f),CornerRadius(1.5f))
        }}
    })
}
