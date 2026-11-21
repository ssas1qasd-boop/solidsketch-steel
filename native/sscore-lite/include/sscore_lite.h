/*
 * sscore-lite: small geometry core for SolidSketch Steel.
 * Same names and types as the main port's sscore where they overlap, so that at
 * merge time sssteel links sscore instead (see docs/steel/MERGE.md).
 *
 * S0: API shape and version only. Booleans/meshing arrive in S2 (Manifold).
 */
#ifndef SSCORE_LITE_H
#define SSCORE_LITE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ss_mesh {
    float*    positions;     /* xyz per vertex */
    uint32_t  vertex_count;
    uint32_t* indices;       /* 3 per triangle */
    uint32_t  triangle_count;
} ss_mesh;

typedef enum ss_status {
    SS_OK = 0,
    SS_ERR_INVALID_ARG = 1,
    SS_ERR_NOT_CLOSED = 2,
    SS_ERR_FAILED = 3,
    SS_ERR_NOT_IMPLEMENTED = 4
} ss_status;

const char* sscore_lite_version(void);

/* Shapes (out mesh is allocated by the core; release with ss_mesh_free). */
ss_status ss_box(float sx, float sy, float sz, ss_mesh* out);
ss_status ss_extrude(const float* outline_xy, uint32_t point_count, float depth, ss_mesh* out);

/* Booleans. */
ss_status ss_subtract(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);
ss_status ss_union(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);
ss_status ss_intersect(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);

/* Queries. */
int    ss_is_closed_solid(const ss_mesh* m);
double ss_volume(const ss_mesh* m);

void ss_mesh_free(ss_mesh* m);

#ifdef __cplusplus
}
#endif

#endif /* SSCORE_LITE_H */
