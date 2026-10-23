package io.github.xudong7587.sunnytv.feature.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import io.github.xudong7587.sunnytv.core.model.AppearanceColor
import kotlin.math.*
import kotlin.random.Random

@Immutable
data class NativeAppearance(val material:String="clear",val cornerScale:Float=1f,val shadows:Boolean=true)
val LocalNativeAppearance=staticCompositionLocalOf {NativeAppearance()}
val LocalGlassControls=staticCompositionLocalOf {false}
internal val LocalChoiceControls=staticCompositionLocalOf {false}

@Composable fun AppearanceSwatch(color:AppearanceColor,dark:Boolean,selected:Boolean) {
    Box(Modifier.size(22.dp).background(Color(if(dark) color.dark else color.light),CircleShape)
        .border(if(selected) 2.dp else 0.dp,SunnyColors.Text,CircleShape))
}
@Composable fun sunnyShape(radius:Dp):RoundedCornerShape {
    val scale=LocalNativeAppearance.current.cornerScale
    return remember(radius,scale) {RoundedCornerShape(radius*scale)}
}
internal fun panelRadius(radius:Dp,appearance:NativeAppearance)=radius*appearance.cornerScale*
    when(appearance.material) {"outline","paper","cutout"->12f/18f;else->1f}

/** The child's corner is concentric with its parent's *resolved* outline, including pill clamping. */
data class InsetPanelShape(val outerRadius:Dp,val inset:Dp):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline=with(density) {
        val gap=inset.toPx()
        val radius=(min(outerRadius.toPx(),min(size.width+2*gap,size.height+2*gap)/2)-gap).coerceAtLeast(0f)
        Outline.Rounded(RoundRect(0f,0f,size.width,size.height,CornerRadius(radius)))
    }
}
@Composable fun sunnyInsetShape(outerRadius:Dp,inset:Dp):Shape {
    val radius=panelRadius(outerRadius,LocalNativeAppearance.current)
    return remember(radius,inset) {InsetPanelShape(radius,inset)}
}

/** Exposed for contrast tests: all opaque material stops stay within the reading surface envelope. */
internal fun materialStops(id:String,p:SunnyPalette):List<Color> {
    val dark=p.surface.luminance()<.3f
    val bright=if(dark) mixSrgb(p.surface,Color.White,.055f) else Color.White
    val shade=mixSrgb(p.surface,if(dark) Color.Black else p.accent,if(dark) .12f else .075f)
    return when(id) {
        "soft"->listOf(bright,p.surface,shade)
        "glass","frosted"->listOf(bright,p.surface)
        "aurora","duotone"->listOf(mixSrgb(p.surface,p.accent,.12f),p.surface,mixSrgb(p.surface,p.accent2,.18f))
        "satin"->listOf(bright,shade,bright,p.surface)
        "metal"->listOf(bright,if(dark) shade else Color(0xFFCFD9E3),bright,if(dark) shade else Color(0xFFBCC9D5))
        "clay"->listOf(bright,p.surface,shade)
        "marble"->listOf(bright,p.surface,shade)
        "neon"->listOf(p.raised,p.surface)
        else->listOf(p.surface,p.surface)
    }
}


/** Shared optical face: page buttons sample a registered background-only layer; nested controls
 * stay flat. Shader/effects are cached and focus changes only surface opacity, never background capture.
 */
@Composable fun Modifier.glassButtonFace(shape:Shape,focused:Boolean=false):Modifier =
    if(LocalNativeAppearance.current.material in setOf("glass","frosted","aurora")) glassControlSurface(shape,false,focused) else this

@Composable private fun Modifier.glassControlSurface(shape:Shape,pressed:Boolean,focused:Boolean=false):Modifier {
    val p=LocalSunnyPalette.current
    val material=LocalNativeAppearance.current.material
    val floating=LocalGlassControls.current
    val dark=p.surface.luminance()<.3f
    // Controls inside the glass navigation shell are flat. Only the enclosing shell has optics.
    if(floating) return this
    if(material=="glass") {
        val opacity=animateFloatAsState(if(focused || pressed) .10f else .03f,LocalMotion.current.fade(150),label="glass-opacity")
        val scene=LocalGlassButtonScene.current
        val source=scene?.artwork ?: scene?.fallback ?: LocalGlassBackdrop.current
        if(source!=null) return liquidGlass(source,shape,0.dp,"glass") {opacity.value}
        return navigationGlassHighlight(shape).drawWithCache {
            val boundary=shape.createOutline(size,layoutDirection,this)
            onDrawBehind {drawOutline(boundary,p.surface.copy(alpha=opacity.value))}
        }
    }
    return drawWithCache {
        val boundary=shape.createOutline(size,layoutDirection,this)
        val frost=material=="frosted"
        val bevel=3.dp.toPx().coerceAtMost(size.minDimension/5f)
        val bevelEdge=concentricInsetOutline(shape,size,bevel/2f,layoutDirection,this)
        val flatFace=concentricInsetOutline(shape,size,bevel,layoutDirection,this)
        val bevelLight=Brush.linearGradient(listOf(Color.White.copy(if(dark) .24f else .50f),
            Color.White.copy(.12f),Color.Black.copy(if(dark) .12f else .07f)),Offset.Zero,Offset(size.width,size.height))
        val tint1=Brush.radialGradient(listOf(p.accent.copy(.14f),Color.Transparent),
            Offset(size.width*.25f,size.height*.3f),size.maxDimension*.85f)
        val tint2=Brush.radialGradient(listOf(p.accent3.copy(.10f),Color.Transparent),
            Offset(size.width*.85f,size.height*.85f),size.maxDimension*.75f)
        val rim=concentricInsetOutline(shape,size,.4.dp.toPx(),layoutDirection,this)
        onDrawWithContent {
            if(frost) {
                // One sloped frame around a level frosted face: no stacked rounded halos.
                drawOutline(boundary,p.surface.copy(if(pressed) .78f else .70f))
                drawOutline(bevelEdge,bevelLight,style=Stroke(bevel))
                drawOutline(flatFace,Color.White.copy(if(dark) .02f else .08f))
                drawOutline(flatFace,p.text.copy(.06f),style=Stroke(.6.dp.toPx()))
            } else {
                // Colour lives inside the transparent body; the edge stays thin and quiet.
                drawOutline(boundary,p.surface.copy(if(pressed) .24f else .16f))
                drawOutline(boundary,tint1);drawOutline(boundary,tint2)
                drawOutline(rim,Color.White.copy(if(dark) .18f else .42f),style=Stroke(.8.dp.toPx()))
            }
            drawContent()
        }
    }
}

/** Cached paths/brushes, no bitmap textures, frame loops, downloads or repeated path construction. */
@Composable fun Modifier.materialSurface(shape:Shape,control:Boolean=false,preview:Boolean=false,pressed:Boolean=false,focused:Boolean=false):Modifier {
    val a=LocalNativeAppearance.current;val p=LocalSunnyPalette.current
    val id=a.material
    if((control && id=="glass") || id in setOf("frosted","aurora")) return glassControlSurface(shape,pressed,focused)
    val elevation=if(control || !a.shadows) 0.dp else when(id) {"soft","clay"->4.dp;"glass","aurora"->5.dp;else->0.dp}
    return shadow(elevation,shape,clip=false)
        .drawWithCache {
            val outline=shape.createOutline(size,layoutDirection,this)
            val offset=if(id=="cutout") 3.dp.toPx() else 2.dp.toPx()
            onDrawBehind {
                if(a.shadows && !control && id in listOf("cutout","outline","paper")) {
                    translate(left=if(id=="cutout") offset else 0f,top=offset) {drawOutline(outline,p.accent.copy(alpha=.22f))}
                }
            }
        }.clip(shape).drawWithCache {
            val dark=p.surface.luminance()<.3f
            val stops=materialStops(id,p).let {if(id=="soft" && pressed) it.reversed() else it}
            val fill=Brush.linearGradient(stops,start=Offset.Zero,end=Offset(size.width*.7f,size.height))
            val rim=concentricInsetOutline(shape,size,.5.dp.toPx(),layoutDirection,this)
            val specular=Brush.linearGradient(listOf(Color.White.copy(if(dark) .2f else .9f),
                Color.White.copy(.02f),p.accent.copy(.18f),Color.White.copy(if(dark) .12f else .7f)))
            val fibers=Path();val random=Random(17)
            val spacing=if(preview) 5.dp.toPx() else 8.dp.toPx()
            when(id) {
                "linen"->{
                    var x=-size.height;var count=0
                    while(x<size.width+size.height && count++<350) {
                        fibers.moveTo(x,0f);fibers.lineTo(x+size.height,size.height)
                        fibers.moveTo(x,size.height);fibers.lineTo(x+size.height,0f);x+=spacing
                    }
                }
                "metal"->{var y=0f;var count=0;while(y<size.height && count++<250) {fibers.moveTo(0f,y);fibers.lineTo(size.width,y);y+=3.dp.toPx()}}
                "paper","clay"->repeat(min(180,(size.width*size.height/2500).toInt().coerceAtLeast(16))) {
                    val x=random.nextFloat()*size.width;val y=random.nextFloat()*size.height
                    fibers.moveTo(x,y);fibers.lineTo(x+if(id=="paper") 4.dp.toPx() else .6.dp.toPx(),y+1.dp.toPx())
                }
                "marble"->repeat(7) {index->
                    val start=size.width*(index/6f)-size.width*.2f
                    fibers.moveTo(start,0f)
                    fibers.cubicTo(start+size.width*.14f,size.height*.22f,start-size.width*.08f,size.height*.7f,start+size.width*.3f,size.height)
                }
            }
            val mesh1=Brush.radialGradient(listOf(p.accent.copy(.22f),Color.Transparent),Offset(size.width*.12f,size.height*.05f),size.maxDimension*.8f)
            val mesh2=Brush.radialGradient(listOf(p.accent2.copy(.18f),Color.Transparent),Offset(size.width*.95f,size.height*.9f),size.maxDimension*.7f)
            val inset=concentricInsetOutline(shape,size,2.dp.toPx(),layoutDirection,this)
            onDrawWithContent {
                drawRect(fill)
                if(id=="mesh" || id=="aurora") {drawRect(mesh1);drawRect(mesh2)}
                if(id in listOf("linen","paper","clay","metal","marble")) {
                    drawPath(fibers,if(dark) Color.White.copy(.045f) else p.text.copy(if(id in listOf("linen","marble")) .10f else .055f),style=Stroke(if(id=="marble") 1.4.dp.toPx() else .65.dp.toPx()))
                }
                if(id=="neon" && a.shadows) {
                    drawOutline(inset,p.accent.copy(.05f),style=Stroke(10.dp.toPx()))
                    drawOutline(inset,p.accent.copy(.10f),style=Stroke(5.dp.toPx()))
                }
                if(id in listOf("soft","glass","frosted","aurora","satin","metal","clay","marble")) {
                    drawOutline(rim,specular,style=Stroke(if(id=="frosted") 2.dp.toPx() else 1.5.dp.toPx()))
                } else drawOutline(rim,if(id in listOf("outline","cutout","neon")) p.accent.copy(.65f) else p.border,style=Stroke(1.dp.toPx()))
                if(id=="paper") drawOutline(inset,Color.White.copy(if(dark) .08f else .7f),style=Stroke(1.dp.toPx()))
                drawContent()
            }
        }
}

@Composable fun Modifier.sunnyPanel(radius:Dp=18.dp):Modifier {
    val appearance=LocalNativeAppearance.current
    val shape=remember(radius,appearance) {RoundedCornerShape(panelRadius(radius,appearance))}
    val backdrop=LocalGlassBackdrop.current
    return if(appearance.material in setOf("glass","frosted","aurora") && backdrop!=null)
        liquidGlass(backdrop,shape,panelRadius(radius,appearance),appearance.material)
    else materialSurface(shape)
}

/** Dialogs sample the same complete page as navigation; native dialog windows never enter that layer. */
@Composable internal fun Modifier.choicePanel(radius:Dp):Modifier {
    val a=LocalNativeAppearance.current
    val scene=LocalGlassButtonScene.current
    val source=LocalChoiceBackdrop.current ?: LocalGlassBackdrop.current ?: scene?.artwork ?: scene?.fallback
    // Toolbar buttons are 48dp high: their visible corner never exceeds 24dp, even in round mode.
    val corner=(radius*a.cornerScale).coerceAtMost(24.dp)
    val shape=remember(corner) {RoundedCornerShape(corner)}
    return if(a.material=="glass" && source!=null) liquidGlass(source,shape,corner,"glass") {.21f}
        else if(a.material in setOf("frosted","aurora") && source!=null) liquidGlass(source,shape,corner,a.material)
        else materialSurface(shape)
}
