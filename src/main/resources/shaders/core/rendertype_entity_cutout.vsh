// Vanilla Minimaps
// https://github.com/JNNGL/VanillaMinimaps

#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 overlayColor;
out vec2 texCoord0;
out vec2 texCoord1;
out vec2 texCoord2;
out float minimap;
out float keepEdges;
out float transition;
out float fullscreenMinimap;
out float sx, sy;

#moj_import <minimap/vertex_util.glsl>

void main() {
    vec4 vertex = vec4(Position, 1.0);
    vec4 vcolor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)
        * texelFetch(Sampler2, UV2 / 16, 0);

    gl_Position = ProjMat * ModelViewMat * vertex;
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    overlayColor = texelFetch(Sampler1, UV1, 0);

    #moj_import <minimap/vertex_main.glsl>
}
