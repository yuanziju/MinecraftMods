#version 450 core

#include "utils.glsl"

struct Light {
    vec3 position;
    vec3 color;
    float intensity;
    float radius;
};

struct Material {
    vec3 albedo;
    float roughness;
    float metalness;
    float ao;
    float emissive;
};

float D_ggx(float NdotH, float roughness) {
    float alpha = roughness * roughness;
    float alpha2 = alpha * alpha;
    float denom = NdotH * NdotH * (alpha2 - 1.0) + 1.0;
    return alpha2 / (PI * denom * denom);
}

float G_schlick(float NdotV, float roughness) {
    float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
}

float G_smith(float NdotV, float NdotL, float roughness) {
    return G_schlick(NdotV, roughness) * G_schlick(NdotL, roughness);
}

vec3 F_schlick(float HdotV, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - HdotV, 5.0);
}

vec3 cook_torrance(Light light, vec3 N, vec3 V, vec3 L, Material mat) {
    vec3 H = normalize(V + L);
    
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotH = max(dot(N, H), 0.0);
    float HdotV = max(dot(H, V), 0.0);
    
    vec3 F0 = mix(vec3(0.04), mat.albedo, mat.metalness);
    vec3 F = F_schlick(HdotV, F0);
    
    float D = D_ggx(NdotH, mat.roughness);
    float G = G_smith(NdotV, NdotL, mat.roughness);
    
    vec3 specular = (F * D * G) / max(4.0 * NdotV * NdotL, 0.001);
    
    vec3 diffuse = (1.0 - mat.metalness) * mat.albedo / PI;
    
    float dist = length(light.position - V);
    float attenuation = 1.0 / (dist * dist);
    float falloff = smoothstep(0.0, 1.0, 1.0 - dist / light.radius);
    
    return (diffuse + specular) * light.color * light.intensity * NdotL * attenuation * falloff;
}

vec3 ambient_occlusion(vec3 color, float ao) {
    return color * ao;
}

vec3 emissive(Material mat) {
    return mat.albedo * mat.emissive;
}

vec3 sun_lighting(vec3 N, vec3 V, vec3 sun_dir, vec3 sun_color, Material mat) {
    vec3 L = normalize(sun_dir);
    
    float NdotL = max(dot(N, L), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    
    vec3 H = normalize(V + L);
    float HdotV = max(dot(H, V), 0.0);
    float NdotH = max(dot(N, H), 0.0);
    
    vec3 F0 = mix(vec3(0.04), mat.albedo, mat.metalness);
    vec3 F = F_schlick(HdotV, F0);
    
    float D = D_ggx(NdotH, mat.roughness);
    float G = G_smith(NdotV, NdotL, mat.roughness);
    
    vec3 specular = (F * D * G) / max(4.0 * NdotV * NdotL, 0.001);
    vec3 diffuse = (1.0 - mat.metalness) * mat.albedo / PI;
    
    return (diffuse + specular) * sun_color * NdotL;
}

vec3 sky_lighting(vec3 N, vec3 V, vec3 sky_color, Material mat) {
    float NdotV = max(dot(N, V), 0.0);
    
    vec3 diffuse = mat.albedo * sky_color * 0.5;
    vec3 specular = sky_color * 0.1 * pow(NdotV, 4.0);
    
    return diffuse + specular;
}

vec3 reinhard_tonemap(vec3 color) {
    return color / (1.0 + color);
}

vec3 aces_tonemap(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 white_balance(vec3 color, float temperature) {
    float t = temperature / 1000.0;
    float t2 = t * t;
    
    vec3 rgb_r = vec3(1.0, 0.3963377774, 0.2158037573);
    vec3 rgb_g = vec3(1.0, 0.8124516612, 0.3100713913);
    vec3 rgb_b = vec3(1.0, 0.9092712712, 1.454266038);
    
    float r = (t <= 66.0) ? 1.0 : 329.698727446 * pow(t - 60.0, -0.1332047592);
    float g = (t <= 66.0) ? 99.4708025861 * log(t) - 161.1195681661 : 288.1221695283 * pow(t - 60.0, -0.0755148492);
    float b = (t >= 66.0) ? 1.0 : 138.5177312231 * log(t - 10.0) - 305.0447927307;
    
    return color * vec3(r / 255.0, g / 255.0, b / 255.0);
}

float shadow_pcf(vec2 uv, float depth, sampler2D shadow_map, float shadow_bias, float shadow_radius) {
    float shadow = 0.0;
    float samples = 4.0;
    float sample_step = shadow_radius / samples;
    
    for (float y = -shadow_radius; y <= shadow_radius; y += sample_step) {
        for (float x = -shadow_radius; x <= shadow_radius; x += sample_step) {
            float shadow_depth = texture(shadow_map, uv + vec2(x, y)).r;
            shadow += (depth - shadow_bias > shadow_depth) ? 0.0 : 1.0;
        }
    }
    
    return shadow / (samples * samples);
}

float contact_shadow(vec3 pos, vec3 normal, float max_dist, sampler2D depth_buffer) {
    vec3 ray_dir = -normal * max_dist;
    float step_size = max_dist / 8.0;
    
    for (float i = 0.0; i < 8.0; i++) {
        vec3 sample_pos = pos + ray_dir * (i / 8.0);
        vec2 uv = sample_pos.xy * 0.5 + 0.5;
        
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) break;
        
        float buffer_depth = texture(depth_buffer, uv).r;
        
        if (abs(sample_pos.z - buffer_depth) < 0.01) {
            return 0.5 + 0.5 * (1.0 - i / 8.0);
        }
    }
    
    return 1.0;
}