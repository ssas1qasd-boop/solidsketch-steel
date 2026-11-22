// Host tests of the native chain: sscore-lite (Manifold) and sssteel linking it.
#include <cmath>
#include <cstdio>
#include <cstring>
#include <vector>

#include "sscore_lite.h"
#include "sssteel.h"

static int fails = 0;
#define CHECK(cond, ...) do { if (!(cond)) { std::printf("FAIL %s:%d: ", __FILE__, __LINE__); std::printf(__VA_ARGS__); std::printf("\n"); ++fails; } } while (0)
static bool near(double a, double b, double tol) { return std::fabs(a - b) <= tol; }

static void shift(ss_mesh& m, float dx, float dy, float dz) {
    for (uint32_t v = 0; v < m.vertex_count; ++v) { m.positions[3 * v] += dx; m.positions[3 * v + 1] += dy; m.positions[3 * v + 2] += dz; }
}
static ss_mesh box_at(float sx, float sy, float sz, float x, float y, float z) { ss_mesh m{}; ss_box(sx, sy, sz, &m); shift(m, x, y, z); return m; }
static double polygon_circle_area(double r, int n) { return 0.5 * n * r * r * std::sin(2 * M_PI / n); }

int main() {
    // Shapes.
    ss_mesh m = box_at(10, 20, 30, 0, 0, 0);
    CHECK(near(ss_volume(&m), 6000.0, 1e-6), "box volume %f", ss_volume(&m));
    CHECK(ss_is_closed_solid(&m) == 1, "box should be closed");
    ss_mesh_free(&m);
    CHECK(ss_box(0, 1, 1, &m) == SS_ERR_INVALID_ARG, "zero size box accepted");

    // Binary booleans with an exact overlap of 5 x 10 x 10.
    ss_mesh a = box_at(10, 10, 10, 0, 0, 0), b = box_at(10, 10, 10, 5, 0, 0), r{};
    CHECK(ss_subtract(&a, &b, &r) == SS_OK && near(ss_volume(&r), 500.0, 1e-3), "subtract %f", ss_volume(&r)); ss_mesh_free(&r);
    CHECK(ss_union(&a, &b, &r) == SS_OK && near(ss_volume(&r), 1500.0, 1e-3), "union %f", ss_volume(&r));
    CHECK(ss_is_closed_solid(&r) == 1, "union result not closed"); ss_mesh_free(&r);
    CHECK(ss_intersect(&a, &b, &r) == SS_OK && near(ss_volume(&r), 500.0, 1e-3), "intersect %f", ss_volume(&r)); ss_mesh_free(&r);

    // Plate 100 x 100 x 10 with four M20 holes (d0 22, 32 sides) in one pass.
    ss_mesh plate = box_at(100, 100, 10, 0, 0, 0);
    std::vector<ss_mesh> holes(4);
    const float hx[4] = {25, 75, 25, 75}, hy[4] = {25, 25, 75, 75};
    for (int i = 0; i < 4; ++i) { ss_cylinder(11, 30, 32, &holes[i]); shift(holes[i], hx[i], hy[i], -10); }
    CHECK(ss_subtract_many(&plate, holes.data(), 4, &r) == SS_OK, "subtract_many failed");
    const double expect = 100.0 * 100 * 10 - 4 * polygon_circle_area(11, 32) * 10;
    CHECK(near(ss_volume(&r), expect, 1e-2), "plate with holes %f expected %f", ss_volume(&r), expect);
    ss_mesh_free(&r);
    CHECK(ss_union_many(holes.data(), 4, &r) == SS_OK && near(ss_volume(&r), 4 * polygon_circle_area(11, 32) * 30, 1e-2), "union_many %f", ss_volume(&r));
    ss_mesh_free(&r);
    for (auto& h : holes) ss_mesh_free(&h);

    // Extrude an L outline (area 3) given clockwise and counter-clockwise.
    const float ccw[] = {0, 0, 2, 0, 2, 1, 1, 1, 1, 2, 0, 2};
    float cw[12]; for (int i = 0; i < 6; ++i) { cw[2 * i] = ccw[2 * (5 - i)]; cw[2 * i + 1] = ccw[2 * (5 - i) + 1]; }
    CHECK(ss_extrude(ccw, 6, 5, &r) == SS_OK && near(ss_volume(&r), 15.0, 1e-4), "extrude ccw %f", ss_volume(&r)); ss_mesh_free(&r);
    CHECK(ss_extrude(cw, 6, 5, &r) == SS_OK && near(ss_volume(&r), 15.0, 1e-4), "extrude cw %f", ss_volume(&r)); ss_mesh_free(&r);

    // Flat-shaded input (24 corners, duplicated) must weld and work.
    {
        ss_mesh flat{}; flat.vertex_count = a.triangle_count * 3; flat.triangle_count = a.triangle_count;
        std::vector<float> P(flat.vertex_count * 3); std::vector<uint32_t> I(flat.vertex_count);
        for (uint32_t t = 0; t < a.triangle_count * 3; ++t) { std::memcpy(&P[3 * t], &a.positions[3 * a.indices[t]], 3 * sizeof(float)); I[t] = t; }
        flat.positions = P.data(); flat.indices = I.data();
        CHECK(ss_subtract(&flat, &b, &r) == SS_OK && near(ss_volume(&r), 500.0, 1e-3), "flat-shaded subtract %f", ss_volume(&r)); ss_mesh_free(&r);
    }

    // An open mesh (one triangle removed) is not a solid and booleans refuse it.
    {
        ss_mesh open = a; open.triangle_count = a.triangle_count - 1;
        CHECK(ss_is_closed_solid(&open) == 0, "open mesh reported closed");
        CHECK(ss_subtract(&open, &b, &r) == SS_ERR_NOT_CLOSED, "open mesh boolean not refused");
    }
    CHECK(ss_subtract(nullptr, &b, &r) == SS_ERR_INVALID_ARG, "null input accepted");
    ss_mesh_free(&a); ss_mesh_free(&b);

    CHECK(std::strcmp(sssteel_core_version(), sscore_lite_version()) == 0, "sssteel does not link sscore-lite");
    std::printf("%s (%s)\n", fails ? "FAILED" : "OK", sscore_lite_version());
    return fails;
}
