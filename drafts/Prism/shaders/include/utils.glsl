#version 450 core

#define PI 3.141592653589793
#define PI2 6.283185307179586
#define PI_HALF 1.5707963267948966
#define INV_PI 0.3183098861837907
#define INV_PI2 0.15915494309189535

#define saturate(x) clamp(x, 0.0, 1.0)
#define lerp(a, b, t) mix(a, b, t)

float hash11(float p) {
    return fract(sin(p * 78.233) * 43758.5453);
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float hash13(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 45.5432))) * 43758.5453);
}

vec2 hash21(float p) {
    vec2 o = fract(sin(vec2(p, p + 1.0)) * vec2(43758.5453, 22578.1459));
    return o * 2.0 - 1.0;
}

vec2 hash22(vec2 p) {
    vec2 o = fract(sin(p * vec2(12.9898, 78.233)) * 43758.5453);
    return o * 2.0 - 1.0;
}

vec3 hash31(float p) {
    vec3 o = fract(sin(vec3(p, p + 1.0, p + 2.0)) * vec3(43758.5453, 22578.1459, 19642.3498));
    return o * 2.0 - 1.0;
}

vec3 hash33(vec3 p) {
    vec3 o = fract(sin(p * vec3(12.9898, 78.233, 45.5432)) * 43758.5453);
    return o * 2.0 - 1.0;
}

float noise11(float p) {
    float i = floor(p);
    float f = fract(p);
    float u = f * f * (3.0 - 2.0 * f);
    return lerp(hash11(i), hash11(i + 1.0), u);
}

float noise12(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float u = f.x * f.x * (3.0 - 2.0 * f.x);
    float v = f.y * f.y * (3.0 - 2.0 * f.y);
    
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    
    return lerp(lerp(a, b, u), lerp(c, d, u), v);
}

float noise13(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float u = f.x * f.x * (3.0 - 2.0 * f.x);
    float v = f.y * f.y * (3.0 - 2.0 * f.y);
    float w = f.z * f.z * (3.0 - 2.0 * f.z);
    
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float f2 = hash13(i + vec3(1.0, 0.0, 1.0));
    float g = hash13(i + vec3(0.0, 1.0, 1.0));
    float h = hash13(i + vec3(1.0, 1.0, 1.0));
    
    return lerp(lerp(lerp(a, b, u), lerp(c, d, u), v), lerp(lerp(e, f2, u), lerp(g, h, u), v), w);
}

float fbm12(vec2 p, int octaves, float lacunarity, float gain) {
    float sum = 0.0;
    float amp = 1.0;
    float freq = 1.0;
    
    for (int i = 0; i < octaves; i++) {
        sum += noise12(p * freq) * amp;
        freq *= lacunarity;
        amp *= gain;
    }
    
    return sum;
}

float fbm13(vec3 p, int octaves, float lacunarity, float gain) {
    float sum = 0.0;
    float amp = 1.0;
    float freq = 1.0;
    
    for (int i = 0; i < octaves; i++) {
        sum += noise13(p * freq) * amp;
        freq *= lacunarity;
        amp *= gain;
    }
    
    return sum;
}

float voronoi12(vec2 p, float freq) {
    vec2 grid = floor(p * freq);
    vec2 frac = fract(p * freq);
    
    float min_dist = 1.0;
    
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(x, y);
            vec2 point = grid + offset + hash22(grid + offset) * 0.5;
            float dist = length((point - p * freq));
            min_dist = min(min_dist, dist);
        }
    }
    
    return min_dist;
}

float smoothstep_edge(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

vec3 tonemap_ACES(vec3 color) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    
    return clamp((color * (a * color + b)) / (color * (c * color + d) + e), 0.0, 1.0);
}

vec3 tonemap_Reinhard(vec3 color) {
    return color / (1.0 + color);
}

float fresnel(float cos_theta, float ior) {
    float r0 = (1.0 - ior) / (1.0 + ior);
    r0 *= r0;
    return r0 + (1.0 - r0) * pow(1.0 - cos_theta, 5.0);
}

float fresnel_schlick(float cos_theta, float F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cos_theta, 5.0);
}

vec3 world_to_view(vec3 world_pos, mat4 view_matrix) {
    return (view_matrix * vec4(world_pos, 1.0)).xyz;
}

vec3 view_to_screen(vec3 view_pos, mat4 proj_matrix) {
    vec4 clip_pos = proj_matrix * vec4(view_pos, 1.0);
    return clip_pos.xyz / clip_pos.w * 0.5 + 0.5;
}

float linear_depth(float depth, float near, float far) {
    return near * far / (far - depth * (far - near));
}

float screen_to_view_depth(float screen_depth, float near, float far) {
    float ndc = screen_depth * 2.0 - 1.0;
    return 2.0 * near * far / (far + near - ndc * (far - near));
}

vec3 screen_to_view_dir(vec2 uv, float fov_y, float aspect) {
    float fov_x = 2.0 * atan(tan(fov_y * 0.5) * aspect);
    float x = (uv.x - 0.5) * 2.0 * tan(fov_x * 0.5);
    float y = -(uv.y - 0.5) * 2.0 * tan(fov_y * 0.5);
    return normalize(vec3(x, y, -1.0));
}

float fog_density(float dist, float fog_start, float fog_end) {
    return smoothstep(fog_start, fog_end, dist);
}

vec3 apply_fog(vec3 color, vec3 fog_color, float fog_density) {
    return mix(color, fog_color, fog_density);
}

float shadow_bias(float NdotL, float shadow_bias_factor) {
    return shadow_bias_factor * (1.0 - NdotL);
}

float rand(vec2 seed) {
    return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 random_direction(vec2 seed) {
    float theta = rand(seed) * PI2;
    float phi = acos(2.0 * rand(seed + vec2(1.0, 0.0)) - 1.0);
    return vec3(sin(phi) * cos(theta), sin(phi) * sin(theta), cos(phi));
}

vec3 random_hemisphere(vec3 normal, vec2 seed) {
    vec3 dir = random_direction(seed);
    return dir * sign(dot(dir, normal));
}