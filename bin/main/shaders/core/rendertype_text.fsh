// Vanilla Minimaps
// https://github.com/JNNGL/VanillaMinimaps

#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec2 texCoord2;
in float minimap;
in float keepEdges;
in float transition;
in float fullscreenMinimap;
in float sx, sy;
in float squareMinimap;

out vec4 fragColor;

#moj_import <minimap/fragment_util.glsl>

void main() {
    #moj_import <minimap/fragment_main.glsl>

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
