/*
 * sssteel: steel geometry (profiles, plates, bolts, cuts) for SolidSketch Steel.
 * Links sscore-lite now and the main port's sscore after the merge.
 */
#ifndef SSSTEEL_H
#define SSSTEEL_H

#ifdef __cplusplus
extern "C" {
#endif

const char* sssteel_version(void);
/* Version string of the core it links (proves the link in S0). */
const char* sssteel_core_version(void);

#ifdef __cplusplus
}
#endif

#endif /* SSSTEEL_H */
