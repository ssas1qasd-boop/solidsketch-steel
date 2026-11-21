#include "sssteel.h"

#include "sscore_lite.h"

extern "C" {

const char* sssteel_version(void) { return "sssteel 0.0.1 (S0 skeleton)"; }
const char* sssteel_core_version(void) { return sscore_lite_version(); }

}  // extern "C"
