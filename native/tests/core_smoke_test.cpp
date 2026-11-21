// Host-side smoke test of the native chain (sssteel -> sscore-lite).
#include <cmath>
#include <cstdio>
#include <cstring>

#include "sscore_lite.h"
#include "sssteel.h"

int main() {
    int fails = 0;
    ss_mesh m{};
    if (ss_box(10, 20, 30, &m) != SS_OK || m.triangle_count != 12) { std::puts("FAIL box"); ++fails; }
    if (std::fabs(ss_volume(&m) - 6000.0) > 1e-9) { std::printf("FAIL volume %f\n", ss_volume(&m)); ++fails; }
    ss_mesh_free(&m);
    if (ss_box(0, 1, 1, &m) != SS_ERR_INVALID_ARG) { std::puts("FAIL invalid arg"); ++fails; }
    if (std::strcmp(sssteel_core_version(), sscore_lite_version()) != 0) { std::puts("FAIL link"); ++fails; }
    std::printf("%s\n", fails ? "FAILED" : "OK");
    return fails;
}
