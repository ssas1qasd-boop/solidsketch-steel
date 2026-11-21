#include "sscore_lite.h"

#include <cstdlib>
#include <cstring>

extern "C" {

const char* sscore_lite_version(void) { return "sscore-lite 0.0.1 (S0 skeleton)"; }

static ss_status alloc_mesh(uint32_t vc, uint32_t tc, ss_mesh* out) {
    out->positions = static_cast<float*>(std::calloc(vc * 3u, sizeof(float)));
    out->indices = static_cast<uint32_t*>(std::calloc(tc * 3u, sizeof(uint32_t)));
    out->vertex_count = vc;
    out->triangle_count = tc;
    if (!out->positions || !out->indices) {
        ss_mesh_free(out);
        return SS_ERR_FAILED;
    }
    return SS_OK;
}

ss_status ss_box(float sx, float sy, float sz, ss_mesh* out) {
    if (!out || sx <= 0 || sy <= 0 || sz <= 0) return SS_ERR_INVALID_ARG;
    if (alloc_mesh(8, 12, out) != SS_OK) return SS_ERR_FAILED;
    const float v[24] = {0, 0, 0, sx, 0, 0, sx, sy, 0, 0, sy, 0,
                         0, 0, sz, sx, 0, sz, sx, sy, sz, 0, sy, sz};
    /* Outward-facing, counter-clockwise. */
    const uint32_t t[36] = {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4,
                            1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7};
    std::memcpy(out->positions, v, sizeof v);
    std::memcpy(out->indices, t, sizeof t);
    return SS_OK;
}

ss_status ss_extrude(const float*, uint32_t, float, ss_mesh*) { return SS_ERR_NOT_IMPLEMENTED; }
ss_status ss_subtract(const ss_mesh*, const ss_mesh*, ss_mesh*) { return SS_ERR_NOT_IMPLEMENTED; }
ss_status ss_union(const ss_mesh*, const ss_mesh*, ss_mesh*) { return SS_ERR_NOT_IMPLEMENTED; }
ss_status ss_intersect(const ss_mesh*, const ss_mesh*, ss_mesh*) { return SS_ERR_NOT_IMPLEMENTED; }
int ss_is_closed_solid(const ss_mesh*) { return 0; }

/* Signed volume by the divergence theorem (valid for closed, consistently oriented meshes). */
double ss_volume(const ss_mesh* m) {
    if (!m || !m->positions || !m->indices) return 0.0;
    double vol = 0.0;
    for (uint32_t i = 0; i < m->triangle_count; ++i) {
        const float* a = m->positions + 3 * m->indices[3 * i];
        const float* b = m->positions + 3 * m->indices[3 * i + 1];
        const float* c = m->positions + 3 * m->indices[3 * i + 2];
        vol += (double)a[0] * ((double)b[1] * c[2] - (double)b[2] * c[1])
             - (double)a[1] * ((double)b[0] * c[2] - (double)b[2] * c[0])
             + (double)a[2] * ((double)b[0] * c[1] - (double)b[1] * c[0]);
    }
    return vol / 6.0;
}

void ss_mesh_free(ss_mesh* m) {
    if (!m) return;
    std::free(m->positions);
    std::free(m->indices);
    m->positions = nullptr;
    m->indices = nullptr;
    m->vertex_count = m->triangle_count = 0;
}

}  // extern "C"
