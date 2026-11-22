#include "sscore_lite.h"

#include <cmath>
#include <cstdlib>
#include <cstring>
#include <vector>

#include "manifold/manifold.h"

using manifold::Manifold;
using manifold::MeshGL;

namespace {

ss_status alloc_mesh(uint32_t vc, uint32_t tc, ss_mesh* out) {
    out->positions = static_cast<float*>(std::calloc(vc ? vc * 3u : 1u, sizeof(float)));
    out->indices = static_cast<uint32_t*>(std::calloc(tc ? tc * 3u : 1u, sizeof(uint32_t)));
    out->vertex_count = vc;
    out->triangle_count = tc;
    if (!out->positions || !out->indices) {
        ss_mesh_free(out);
        return SS_ERR_FAILED;
    }
    return SS_OK;
}

bool valid(const ss_mesh* m) {
    if (!m || !m->positions || !m->indices || m->vertex_count < 4 || m->triangle_count < 4) return false;
    for (uint32_t i = 0; i < m->triangle_count * 3u; ++i)
        if (m->indices[i] >= m->vertex_count) return false;
    return true;
}

/* ss_mesh -> Manifold. Welds duplicated vertices first (flat-shaded input). */
bool to_manifold(const ss_mesh* m, Manifold& out) {
    if (!valid(m)) return false;
    MeshGL g;
    g.numProp = 3;
    g.vertProperties.assign(m->positions, m->positions + m->vertex_count * 3u);
    g.triVerts.assign(m->indices, m->indices + m->triangle_count * 3u);
    g.Merge();
    out = Manifold(g);
    return out.Status() == Manifold::Error::NoError;
}

ss_status from_manifold(const Manifold& m, ss_mesh* out) {
    if (m.Status() != Manifold::Error::NoError) return SS_ERR_FAILED;
    MeshGL g = m.GetMeshGL();
    const uint32_t vc = static_cast<uint32_t>(g.NumVert()), tc = static_cast<uint32_t>(g.NumTri());
    if (alloc_mesh(vc, tc, out) != SS_OK) return SS_ERR_FAILED;
    for (uint32_t v = 0; v < vc; ++v)
        for (int k = 0; k < 3; ++k) out->positions[3 * v + k] = g.vertProperties[v * g.numProp + k];
    std::memcpy(out->indices, g.triVerts.data(), sizeof(uint32_t) * 3u * tc);
    return SS_OK;
}

ss_status binary(const ss_mesh* a, const ss_mesh* b, manifold::OpType op, ss_mesh* out) {
    if (!out) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    Manifold ma, mb;
    if (!valid(a) || !valid(b)) return SS_ERR_INVALID_ARG;
    if (!to_manifold(a, ma) || !to_manifold(b, mb)) return SS_ERR_NOT_CLOSED;
    return from_manifold(ma.Boolean(mb, op), out);
}

}  // namespace

extern "C" {

const char* sscore_lite_version(void) { return "sscore-lite 0.2.0 (Manifold 3.5.4)"; }

ss_status ss_box(float sx, float sy, float sz, ss_mesh* out) {
    if (!out || !(sx > 0) || !(sy > 0) || !(sz > 0)) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    return from_manifold(Manifold::Cube({sx, sy, sz}, false), out);
}

ss_status ss_extrude(const float* xy, uint32_t n, float depth, ss_mesh* out) {
    if (!out || !xy || n < 3 || !(depth > 0)) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    manifold::SimplePolygon poly;
    poly.reserve(n);
    double area2 = 0;
    for (uint32_t i = 0; i < n; ++i) {
        poly.push_back({xy[2 * i], xy[2 * i + 1]});
        const uint32_t j = (i + 1) % n;
        area2 += (double)xy[2 * i] * xy[2 * j + 1] - (double)xy[2 * j] * xy[2 * i + 1];
    }
    if (std::fabs(area2) < 1e-12) return SS_ERR_INVALID_ARG;
    if (area2 < 0) std::vector<manifold::vec2>(poly.rbegin(), poly.rend()).swap(poly);  // Manifold wants CCW
    return from_manifold(Manifold::Extrude({poly}, depth), out);
}

ss_status ss_cylinder(float radius, float height, uint32_t segments, ss_mesh* out) {
    if (!out || !(radius > 0) || !(height > 0) || segments < 3) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    return from_manifold(Manifold::Cylinder(height, radius, -1.0, static_cast<int>(segments)), out);
}

ss_status ss_subtract(const ss_mesh* a, const ss_mesh* b, ss_mesh* out) { return binary(a, b, manifold::OpType::Subtract, out); }
ss_status ss_union(const ss_mesh* a, const ss_mesh* b, ss_mesh* out) { return binary(a, b, manifold::OpType::Add, out); }
ss_status ss_intersect(const ss_mesh* a, const ss_mesh* b, ss_mesh* out) { return binary(a, b, manifold::OpType::Intersect, out); }

ss_status ss_subtract_many(const ss_mesh* a, const ss_mesh* cutters, uint32_t count, ss_mesh* out) {
    if (!out || !valid(a) || (count && !cutters)) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    std::vector<Manifold> ms(1 + count);
    if (!to_manifold(a, ms[0])) return SS_ERR_NOT_CLOSED;
    for (uint32_t i = 0; i < count; ++i) {
        if (!valid(&cutters[i])) return SS_ERR_INVALID_ARG;
        if (!to_manifold(&cutters[i], ms[1 + i])) return SS_ERR_NOT_CLOSED;
    }
    return from_manifold(count ? Manifold::BatchBoolean(ms, manifold::OpType::Subtract) : ms[0], out);
}

ss_status ss_union_many(const ss_mesh* parts, uint32_t count, ss_mesh* out) {
    if (!out || !parts || count == 0) return SS_ERR_INVALID_ARG;
    *out = ss_mesh{};
    std::vector<Manifold> ms(count);
    for (uint32_t i = 0; i < count; ++i) {
        if (!valid(&parts[i])) return SS_ERR_INVALID_ARG;
        if (!to_manifold(&parts[i], ms[i])) return SS_ERR_NOT_CLOSED;
    }
    return from_manifold(Manifold::BatchBoolean(ms, manifold::OpType::Add), out);
}

int ss_is_closed_solid(const ss_mesh* m) {
    Manifold mm;
    return to_manifold(m, mm) && !mm.IsEmpty() ? 1 : 0;
}

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
