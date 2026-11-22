/*
 * sscore-lite: small geometry core for SolidSketch Steel (Manifold underneath).
 * Same names and types as the main port's sscore where they overlap, so that at
 * merge time sssteel links sscore instead (see docs/steel/MERGE.md).
 *
 * Meshes are triangle soups or indexed meshes in millimetres. Inputs may have
 * duplicated vertices (flat-shaded); they are welded before a boolean.
 * Every mesh the core returns is allocated by the core: release it with ss_mesh_free.
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
    uint32_t* indices;       /* 3 per triangle, counter-clockwise seen from outside */
    uint32_t  triangle_count;
} ss_mesh;

typedef enum ss_status {
    SS_OK = 0,
    SS_ERR_INVALID_ARG = 1,
    SS_ERR_NOT_CLOSED = 2,     /* an input is not a closed, oriented solid */
    SS_ERR_FAILED = 3,
    SS_ERR_NOT_IMPLEMENTED = 4
} ss_status;

const char* sscore_lite_version(void);

/* Shapes. */
ss_status ss_box(float sx, float sy, float sz, ss_mesh* out);   /* corner at the origin */
/* Prism: a simple polygon in the XY plane (either winding) extruded from z = 0 to z = depth. */
ss_status ss_extrude(const float* outline_xy, uint32_t point_count, float depth, ss_mesh* out);
/* Cylinder along +Z from z = 0, `segments` sides (>= 3). */
ss_status ss_cylinder(float radius, float height, uint32_t segments, ss_mesh* out);

/* Booleans. */
ss_status ss_subtract(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);
ss_status ss_union(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);
ss_status ss_intersect(const ss_mesh* a, const ss_mesh* b, ss_mesh* out);
/* a minus every cutter, in one pass (a part and all its cuts and holes). */
ss_status ss_subtract_many(const ss_mesh* a, const ss_mesh* cutters, uint32_t count, ss_mesh* out);
ss_status ss_union_many(const ss_mesh* parts, uint32_t count, ss_mesh* out);

/* Queries. */
int    ss_is_closed_solid(const ss_mesh* m);   /* 1 if Manifold accepts it as a closed, oriented solid */
double ss_volume(const ss_mesh* m);            /* signed volume; positive for an outward-facing solid */

void ss_mesh_free(ss_mesh* m);

#ifdef __cplusplus
}
#endif

#endif /* SSCORE_LITE_H */
