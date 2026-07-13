#version 450 core

#include "utils.glsl"

struct CloudLayer {
    float altitude;
    float thickness;
    float density;
    float speed;
    float frequency;
    float octaves;
};

float cloud_shape(vec3 p, float time, CloudLayer layer) {
    vec3 offset = p;
    offset.xz += time * layer.speed;
    
    float fbm = fbm13(offset * layer.frequency, int(layer.octaves), 2.0, 0.5);
    float noise = noise13(offset * layer.frequency * 0.5);
    
    float shape = fbm * 0.5 + noise * 0.3;
    
    float height = (p.y - layer.altitude + layer.thickness * 0.5) / layer.thickness;
    float height_mask = smoothstep(0.0, 0.1, height) * smoothstep(1.0, 0.9, height);
    
    return shape * height_mask * layer.density;
}

float cloud_density(vec3 p, float time, CloudLayer layers[4], int layer_count) {
    float total_density = 0.0;
    
    for (int i = 0; i < layer_count; i++) {
        total_density += cloud_shape(p, time, layers[i]);
    }
    
    return saturate(total_density);
}

vec3 cloud_shading(vec3 p, vec3 sun_dir, float density, vec3 sky_color) {
    float sun_dot = max(dot(normalize(p), sun_dir), 0.0);
    
    vec3 sun_color = vec3(1.0, 0.95, 0.85);
    vec3 ambient_color = sky_color * 0.5;
    
    float lighting = sun_dot * 0.7 + 0.3;
    
    vec3 color = mix(ambient_color, sun_color, lighting);
    
    return color * density;
}

float cloud_raymarch(vec3 ro, vec3 rd, float time, CloudLayer layers[4], int layer_count, float max_dist) {
    float step_size = max_dist / 64.0;
    float density = 0.0;
    
    for (float t = 0.0; t < max_dist; t += step_size) {
        vec3 p = ro + rd * t;
        
        if (p.y < 0.0 || p.y > 256.0) break;
        
        density += cloud_density(p, time, layers, layer_count) * step_size * 0.1;
        
        if (density > 1.0) {
            density = 1.0;
            break;
        }
    }
    
    return density;
}

vec3 cloud_render(vec3 ro, vec3 rd, float time, vec3 sun_dir, vec3 sky_color, CloudLayer layers[4], int layer_count) {
    float density = cloud_raymarch(ro, rd, time, layers, layer_count, 200.0);
    
    if (density < 0.01) return vec3(0.0);
    
    vec3 shading = cloud_shading(ro + rd * 100.0, sun_dir, density, sky_color);
    
    return shading;
}

float cloud_transmittance(vec3 ro, vec3 rd, float time, CloudLayer layers[4], int layer_count, float distance) {
    float step_size = distance / 32.0;
    float transmittance = 1.0;
    
    for (float t = 0.0; t < distance; t += step_size) {
        vec3 p = ro + rd * t;
        
        if (p.y < 0.0 || p.y > 256.0) continue;
        
        float density = cloud_density(p, time, layers, layer_count);
        transmittance *= 1.0 - density * step_size * 0.05;
        
        if (transmittance < 0.01) {
            transmittance = 0.01;
            break;
        }
    }
    
    return transmittance;
}

void init_cloud_layers(CloudLayer layers[4], float weather_factor) {
    layers[0] = CloudLayer(220.0, 30.0, 0.3 * (1.0 + weather_factor * 0.5), 0.02, 0.02, 4.0);
    layers[1] = CloudLayer(180.0, 40.0, 0.5 * (1.0 + weather_factor * 0.3), 0.03, 0.03, 5.0);
    layers[2] = CloudLayer(140.0, 50.0, 0.7 * (1.0 + weather_factor * 0.2), 0.04, 0.04, 6.0);
    layers[3] = CloudLayer(100.0, 60.0, 0.9 * weather_factor, 0.05, 0.05, 7.0);
}