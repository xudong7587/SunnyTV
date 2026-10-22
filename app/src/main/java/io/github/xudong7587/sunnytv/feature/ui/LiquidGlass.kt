package io.github.xudong7587.sunnytv.feature.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

/** A display list, not a CPU screenshot. Capture excludes consumers, so recursion is impossible. */
@Stable class GlassBackdrop(val layer:GraphicsLayer) {
    var coordinates:LayoutCoordinates? by mutableStateOf(null,neverEqualPolicy())
    var ready by mutableStateOf(false)
}
val LocalGlassBackdrop=staticCompositionLocalOf<GlassBackdrop?> {null}

/** Per-page source registry. Artwork is recorded before controls; consumers never record themselves. */
@Stable class GlassButtonSceneState(val fallback:GlassBackdrop) {
    var artwork:GlassBackdrop? by mutableStateOf(null)
}
val LocalGlassButtonScene=staticCompositionLocalOf<GlassButtonSceneState?> {null}
@Composable fun GlassButtonScene(content:@Composable ()->Unit) {
    val fallback=rememberGlassBackdrop(LocalNativeAppearance.current.material=="glass")
    val scene=remember(fallback) {fallback?.let {GlassButtonSceneState(it)}}
    CompositionLocalProvider(LocalGlassButtonScene provides scene) {
        Box(Modifier.fillMaxSize()) {
            if(fallback!=null) Box(Modifier.fillMaxSize().captureGlassBackdrop(fallback).background(SunnyColors.Background))
            content()
        }
    }
}
/** Register the real page background without re-recording posters, buttons or text into its lens. */
@Composable fun Modifier.captureButtonSceneBackdrop():Modifier {
    val scene=LocalGlassButtonScene.current
    val source=rememberGlassBackdrop(scene!=null)
    DisposableEffect(scene,source) {
        if(scene!=null) scene.artwork=source
        onDispose {if(scene?.artwork===source) scene?.artwork=null}
    }
    return captureGlassBackdrop(source)
}

@Composable fun rememberGlassBackdrop(enabled:Boolean):GlassBackdrop? {
    if(!enabled || Build.VERSION.SDK_INT<33) return null
    val layer=rememberGraphicsLayer()
    return remember(layer) {GlassBackdrop(layer)}
}
fun Modifier.captureGlassBackdrop(source:GlassBackdrop?):Modifier = if(source==null) this else
    onGloballyPositioned {source.coordinates=it}.drawWithContent {
        source.layer.record {this@drawWithContent.drawContent()}
        source.ready=true
        drawLayer(source.layer)
    }

/** Local padded crop only. Text/icons are drawn afterwards and never enter the shader. */
@Composable fun Modifier.liquidGlass(source:GlassBackdrop,shape:Shape,radius:Dp,material:String,surfaceAlpha:()->Float={.04f}):Modifier {
    if(Build.VERSION.SDK_INT<33) return materialSurface(shape)
    return liquidGlass33(source,shape,radius,material,surfaceAlpha)
}
private class GlassEffectCache {var size=Size.Zero;var effect:RenderEffect?=null}
@RequiresApi(33)
@Composable private fun Modifier.liquidGlass33(source:GlassBackdrop,shape:Shape,radius:Dp,material:String,surfaceAlpha:()->Float={.04f}):Modifier {
    val p=LocalSunnyPalette.current
    val shadows=LocalNativeAppearance.current.shadows
    val density=androidx.compose.ui.platform.LocalDensity.current
    val shader=remember(material) {android.graphics.RuntimeShader(if(material=="glass") NAVIGATION_LENS_SHADER else LIQUID_SHADER)}
    val crop=rememberGraphicsLayer()
    var coordinates by remember {mutableStateOf<LayoutCoordinates?>(null,neverEqualPolicy())}
    val cache=remember(shader,p,shape,radius,material,density) {GlassEffectCache()}
    return shadow(if(shadows) 10.dp else 0.dp,shape,clip=false,ambientColor=Color.Black.copy(.2f),spotColor=Color.Black.copy(.2f)).clip(shape)
        .then(if(material=="glass") Modifier.navigationGlassHighlight(shape) else Modifier)
        .onGloballyPositioned {coordinates=it}.drawWithContent {
            val src=source.coordinates;val dst=coordinates
            if(!source.ready || src==null || dst==null || !src.isAttached || !dst.isAttached) {
                drawRect(p.surface)
            } else {
                val offset=dst.positionOnScreen()-src.positionOnScreen()
                val pad=with(density) {40.dp.toPx()}.roundToInt().toFloat()
                val scale=density.density
                val dark=p.surface.luminance()<.3f
                if(cache.effect==null || cache.size!=size) {
                if(material=="glass") {
                    val rounded=(shape.createOutline(size,layoutDirection,this) as? Outline.Rounded)?.roundRect
                    val r=with(density) {radius.toPx()}.coerceAtMost(size.minDimension/2)
                    shader.setFloatUniform("size",size.width,size.height)
                    shader.setFloatUniform("offset",-pad,-pad)
                    shader.setFloatUniform("cornerRadii",rounded?.topLeftCornerRadius?.x ?: r,rounded?.topRightCornerRadius?.x ?: r,
                        rounded?.bottomRightCornerRadius?.x ?: r,rounded?.bottomLeftCornerRadius?.x ?: r)
                    shader.setFloatUniform("refractionHeight",24f*scale)
                    shader.setFloatUniform("refractionAmount",-24f*scale)
                    shader.setFloatUniform("depthEffect",0f)
                } else {
                shader.setFloatUniform("panelSize",size.width,size.height)
                shader.setFloatUniform("padding",pad)
                shader.setFloatUniform("radius",with(density) {radius.toPx()}.coerceAtMost(size.minDimension/2))
                shader.setFloatUniform("pixelScale",scale)
                shader.setFloatUniform("frost",if(material=="frosted") 1f else 0f)
                shader.setFloatUniform("aurora",if(material=="aurora") 1f else 0f)
                shader.setFloatUniform("dark",if(dark) 1f else 0f)
                shader.setFloatUniform("tint",p.surface.red,p.surface.green,p.surface.blue)
                shader.setFloatUniform("accent",p.accent.red,p.accent.green,p.accent.blue)
                }
                // RenderEffect snapshots the builder uniforms: recreate only for geometry/theme changes.
                val lens=android.graphics.RenderEffect.createRuntimeShaderEffect(shader,if(material=="glass") "content" else "backdrop")
                cache.effect=if(material=="glass") {
                    val saturation=android.graphics.ColorMatrix().apply {setSaturation(1.5f)}
                    val colorEffect=android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(saturation))
                    // Miuix defines sigma as radius * .45; Android maps radius to .57735*r + .5.
                    // Match the reference sigma while using the platform's cached separable blur.
                    val sigma=with(density) {4.dp.toPx()}*.45f
                    val blurRadius=((sigma-.5f)/.57735f).coerceAtLeast(.01f)
                    val blur=android.graphics.RenderEffect.createBlurEffect(blurRadius,blurRadius,colorEffect,android.graphics.Shader.TileMode.CLAMP)
                    android.graphics.RenderEffect.createChainEffect(lens,blur).asComposeRenderEffect()
                } else lens.asComposeRenderEffect()
                cache.size=size
                }
                crop.renderEffect=cache.effect
                crop.record(size=IntSize((size.width+2*pad).roundToInt(),(size.height+2*pad).roundToInt())) {
                    // Fill uncovered source extents, including first-frame and edge-of-page samples.
                    drawRect(p.background)
                    translate(pad-offset.x,pad-offset.y) {drawLayer(source.layer)}
                }
                translate(-pad,-pad) {drawLayer(crop)}
                if(material=="glass") drawRect(p.surface.copy(alpha=surfaceAlpha()))
            }
            drawContent()
        }
}

/** Original AGSL: rounded-rectangle SDF, refracted sampling, nine-tap diffusion, directional rim.
 * No time uniform or sensor loop. Changes are driven by actual page drawing and layout only.
 */
internal const val LIQUID_SHADER="""
uniform shader backdrop;
uniform float2 panelSize;
uniform float padding;
uniform float radius;
uniform float pixelScale;
uniform float frost;
uniform float aurora;
uniform float dark;
uniform float3 tint;
uniform float3 accent;
float distanceToEdge(float2 p) {
    float2 q=abs(p-panelSize*.5)-(panelSize*.5-radius);
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
half4 main(float2 coord) {
    float2 p=coord-padding;
    float d=distanceToEdge(p);
    float coverage=1.0-smoothstep(-.65,.65,d);
    if(coverage<=0.0) return half4(0.0);
    float2 n=float2(distanceToEdge(p+float2(.6,0.0))-distanceToEdge(p-float2(.6,0.0)),
                    distanceToEdge(p+float2(0.0,.6))-distanceToEdge(p-float2(0.0,.6)));
    n=n/max(length(n),.001);
    float depth=clamp(-d/(18.0*pixelScale),0.0,1.0);
    float bend=sin(depth*3.14159265)*4.5*pixelScale*(1.0-frost*.8);
    float2 uv=coord-n*bend;
    // A slight lens enlargement through the centre joins continuously to the refracted rim.
    uv=mix(uv,(coord-padding-panelSize*.5)*.985+panelSize*.5+padding,smoothstep(.3,1.0,depth)*.4*(1.0-frost));
    float spread=mix(.65,8.0,frost)*pixelScale;
    half4 c=backdrop.eval(uv)*.24;
    c+=(backdrop.eval(uv+float2(spread,0.0))+backdrop.eval(uv-float2(spread,0.0))
       +backdrop.eval(uv+float2(0.0,spread))+backdrop.eval(uv-float2(0.0,spread)))*.12;
    c+=(backdrop.eval(uv+float2(spread,spread))+backdrop.eval(uv-float2(spread,spread))
       +backdrop.eval(uv+float2(spread,-spread))+backdrop.eval(uv+float2(-spread,spread)))*.07;
    float tintAmount=mix(.13,.60,frost)+dark*.08;
    float3 rgb=mix(float3(c.rgb),tint,tintAmount);
    float light=max(dot(n,normalize(float2(-.55,-.83))),0.0);
    float opposite=max(dot(n,normalize(float2(.65,.76))),0.0);
    float edgeDistance=max(-d,0.0)/pixelScale;
    if(frost>.5) {
        // Flat face, three-dp planar slope, then a quiet inner cut.
        float slope=(1.0-smoothstep(3.0,3.4,edgeDistance))*smoothstep(0.0,.6,edgeDistance);
        rgb+=float3(1.0)*slope*(.02+.16*light-.07*opposite);
    } else {
        // Aurora has colour within the glass instead of a glowing perimeter.
        float rim=exp(-pow(edgeDistance/.7,2.0));
        rgb+=float3(1.0)*rim*(.015+.06*light);
    }
    float sheen=pow(max(0.0,1.0-length((p-panelSize*float2(.12,.05))/max(panelSize,float2(1.0)))),2.0);
    float colourCloud=exp(-length((p-panelSize*float2(.7,.65))/max(panelSize,float2(1.0)))*2.0);
    rgb=mix(rgb,accent,(.04+sheen*.08+colourCloud*.06)*aurora);
    return half4(half3(clamp(rgb,0.0,1.0))*half(coverage),half(coverage));
}
"""

/** Same dual-peak rim as the reference navigation; fixed -135-degree light suits a stationary TV.
 * Shader and uniforms are cached per outline. No sensors, timers or offscreen layer are added.
 */
@Composable internal fun Modifier.navigationGlassHighlight(shape:Shape):Modifier {
    if(Build.VERSION.SDK_INT<33) return this
    return navigationGlassHighlight33(shape)
}
@RequiresApi(33)
@Composable private fun Modifier.navigationGlassHighlight33(shape:Shape):Modifier {
    val shader=remember {android.graphics.RuntimeShader(NAVIGATION_BLOOM_SHADER)}
    return drawWithCache {
        val outline=shape.createOutline(size,layoutDirection,this)
        val r=(outline as? Outline.Rounded)?.roundRect
        shader.setFloatUniform("halfView",size.width/2,size.height/2)
        shader.setFloatUniform("halfViewFloor",kotlin.math.floor(size.width/2),kotlin.math.floor(size.height/2))
        shader.setFloatUniform("cornerRadii",r?.topLeftCornerRadius?.x ?: 0f,r?.topRightCornerRadius?.x ?: 0f,
            r?.bottomLeftCornerRadius?.x ?: 0f,r?.bottomRightCornerRadius?.x ?: 0f)
        shader.setFloatUniform("strokeWidth",1.dp.toPx())
        val blur=2.dp.toPx()
        shader.setFloatUniform("innerBlurRadius",blur)
        shader.setFloatUniform("innerBlurRadiusSq",blur*blur)
        shader.setFloatUniform("highlightAlpha",.75f)
        shader.setColorUniform("strokeColor",android.graphics.Color.WHITE)
        shader.setFloatUniform("strokeAlphaMul",.12f)
        shader.setFloatUniform("lightDir1",-.70622456f,-.70622456f,-.04993762f)
        shader.setFloatUniform("lightDir2",0f,.19611613f,-.9805807f)
        shader.setColorUniform("lightColor1",android.graphics.Color.WHITE)
        shader.setColorUniform("lightColor2",android.graphics.Color.WHITE)
        shader.setFloatUniform("lightIntensity1",1f)
        shader.setFloatUniform("lightIntensity2",.4f)
        val brush=ShaderBrush(shader)
        onDrawWithContent {
            drawContent()
            drawRect(brush,blendMode=BlendMode.SrcOver)
        }
    }
}
