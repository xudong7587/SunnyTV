// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Lens adapted upstream from Kyant0/AndroidLiquidGlass (Apache-2.0).
// SunnyTV adaptation: extracted lens and dual-peak bloom; fixed TV light.
// Bloom uses premultiplied coverage/SrcOver to preserve translucent scrolling-layer composition.
// Source revision and original paths: third_party/miuix-glass/UPSTREAM.json.
package io.github.xudong7587.sunnytv.feature.ui

internal const val NAVIGATION_LENS_SHADER="""
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}


float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

internal const val NAVIGATION_BLOOM_SHADER="""
uniform float2 halfView;
uniform float2 halfViewFloor;
uniform float4 cornerRadii;
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float innerBlurRadiusSq;
uniform float highlightAlpha;

layout(color) uniform half4 strokeColor;
uniform float strokeAlphaMul;

uniform float3 lightDir1;
layout(color) uniform half4 lightColor1;
uniform float lightIntensity1;

uniform float3 lightDir2;
layout(color) uniform half4 lightColor2;
uniform float lightIntensity2;

float pickRadius(float2 fragCoord, float4 radii) {
    float2 up = fragCoord.y < halfView.y ? radii.xy : radii.zw;
    return fragCoord.x < halfView.x ? up.x : up.y;
}

// caller passes non-negative pos (already abs-folded), so skip the redundant abs.
float roundedBoxSDF(float2 pos, float2 halfSize, float radius) {
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 d = pos - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}

float3 getNormal(float2 fragCoord, float sdf, float R) {
    float2 xy = fragCoord - halfViewFloor;
    float2 xy_a = abs(xy);
    float t = smoothstep(-innerBlurRadius, 0.0, sdf);
    float z = sqrt(max(innerBlurRadiusSq - t * t, 0.0));
    float3 coord = float3(xy_a, -z);

    float2 corner = halfView - R;
    corner.x = min(corner.x, xy_a.x);
    corner.y = min(corner.y, xy_a.y);

    float2 dir = normalize(coord.xy - corner.xy);
    corner += dir * (R - innerBlurRadius);

    if (any(lessThan(xy_a, corner))) {
        return float3(0.0, 0.0, -1.0);
    }

    float2 signal = sign(xy);
    float3 n = normalize(coord - float3(corner, 0.0));
    n.xy *= signal;
    return n;
}

half4 main(float2 fragCoord) {
    float2 xy = abs(fragCoord - halfView);

    float originRadius = pickRadius(fragCoord, cornerRadii);
    float R = max(originRadius, innerBlurRadius);

    if (all(lessThan(xy, halfView - R))) {
        return half4(0.0);
    }

    float sdf = roundedBoxSDF(xy, halfView, originRadius);
    half outMask = half(smoothstep(0.0, -1.0, sdf));
    float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);

    // Native: stroke = uStrokeColor * sa; result += stroke.rgb * stroke.a
    //       = strokeColor.rgb * strokeColor.a * sa^2
    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);

    float3 n = getNormal(fragCoord, sdf, R);

    float l1 = dot(n.xy, lightDir1.xy);
    rgb += half(l1 * l1 * lightIntensity1) * lightColor1.rgb;
    float l2 = dot(n.xy, lightDir2.xy);
    rgb += half(l2 * l2 * lightIntensity2) * lightColor2.rgb;

    half3 glow = clamp(rgb * half(highlightAlpha), 0.0, 1.0);
    half coverage = max(glow.r, max(glow.g, glow.b));
    return half4(glow, coverage) * outMask;
}
"""
