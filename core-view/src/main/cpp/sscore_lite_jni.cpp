// JNI glue: com.solidsketchapp.core.view.NativeCore <-> sscore-lite.
#include <jni.h>

#include <string>
#include <vector>

#include "sscore_lite.h"

namespace {

struct JMesh {   // a Java Mesh copied into native memory
    std::vector<float> p;
    std::vector<uint32_t> i;
    ss_mesh m{};
};

jclass meshClass(JNIEnv* env) { return env->FindClass("com/solidsketchapp/core/api/Mesh"); }

bool read(JNIEnv* env, jobject mesh, JMesh& out) {
    if (!mesh) return false;
    jclass c = meshClass(env);
    auto pa = (jfloatArray)env->CallObjectMethod(mesh, env->GetMethodID(c, "getPositions", "()[F"));
    auto ia = (jintArray)env->CallObjectMethod(mesh, env->GetMethodID(c, "getIndices", "()[I"));
    if (!pa || !ia) return false;
    out.p.resize(env->GetArrayLength(pa));
    out.i.resize(env->GetArrayLength(ia));
    env->GetFloatArrayRegion(pa, 0, (jsize)out.p.size(), out.p.data());
    env->GetIntArrayRegion(ia, 0, (jsize)out.i.size(), reinterpret_cast<jint*>(out.i.data()));
    out.m = ss_mesh{out.p.data(), (uint32_t)(out.p.size() / 3), out.i.data(), (uint32_t)(out.i.size() / 3)};
    return true;
}

jobject fail(JNIEnv* env, ss_status s) {
    static const char* names[] = {"OK", "INVALID_ARG", "NOT_CLOSED", "FAILED", "NOT_IMPLEMENTED"};
    std::string msg = std::string("sscore-lite: ") + (s >= 0 && s <= 4 ? names[s] : "error");
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), msg.c_str());
    return nullptr;
}

/* Takes ownership of `m` (frees it) and returns a new Java Mesh. */
jobject toJava(JNIEnv* env, ss_status s, ss_mesh& m) {
    if (s != SS_OK) { ss_mesh_free(&m); return fail(env, s); }
    jfloatArray pa = env->NewFloatArray((jsize)(m.vertex_count * 3));
    jintArray ia = env->NewIntArray((jsize)(m.triangle_count * 3));
    env->SetFloatArrayRegion(pa, 0, (jsize)(m.vertex_count * 3), m.positions);
    env->SetIntArrayRegion(ia, 0, (jsize)(m.triangle_count * 3), reinterpret_cast<const jint*>(m.indices));
    ss_mesh_free(&m);
    jclass c = meshClass(env);
    return env->NewObject(c, env->GetMethodID(c, "<init>", "([F[I)V"), pa, ia);
}

bool readAll(JNIEnv* env, jobjectArray arr, std::vector<JMesh>& out, std::vector<ss_mesh>& flat) {
    jsize n = env->GetArrayLength(arr);
    out.resize(n);
    for (jsize k = 0; k < n; ++k) if (!read(env, env->GetObjectArrayElement(arr, k), out[k])) return false;
    for (auto& j : out) flat.push_back(j.m);
    return true;
}

}  // namespace

#define JNI_FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_solidsketchapp_core_view_NativeCore_##name

JNI_FN(jstring, version)(JNIEnv* env, jobject) { return env->NewStringUTF(sscore_lite_version()); }

JNI_FN(jdouble, boxVolume)(JNIEnv*, jobject, jfloat sx, jfloat sy, jfloat sz) {
    ss_mesh m{};
    if (ss_box(sx, sy, sz, &m) != SS_OK) return -1.0;
    double v = ss_volume(&m);
    ss_mesh_free(&m);
    return v;
}

JNI_FN(jobject, box)(JNIEnv* env, jobject, jfloat sx, jfloat sy, jfloat sz) {
    ss_mesh m{}; ss_status s = ss_box(sx, sy, sz, &m); return toJava(env, s, m);
}

JNI_FN(jobject, extrude)(JNIEnv* env, jobject, jfloatArray xy, jfloat depth) {
    if (!xy) return fail(env, SS_ERR_INVALID_ARG);
    std::vector<float> p(env->GetArrayLength(xy));
    env->GetFloatArrayRegion(xy, 0, (jsize)p.size(), p.data());
    ss_mesh m{}; ss_status s = ss_extrude(p.data(), (uint32_t)(p.size() / 2), depth, &m); return toJava(env, s, m);
}

JNI_FN(jobject, cylinder)(JNIEnv* env, jobject, jfloat r, jfloat h, jint seg) {
    ss_mesh m{}; ss_status s = ss_cylinder(r, h, seg < 3 ? 3u : (uint32_t)seg, &m); return toJava(env, s, m);
}

JNI_FN(jobject, booleanOp)(JNIEnv* env, jobject, jint op, jobject a, jobject b) {
    JMesh A, B;
    if (!read(env, a, A) || !read(env, b, B)) return fail(env, SS_ERR_INVALID_ARG);
    ss_mesh m{};
    ss_status s = op == 0 ? ss_subtract(&A.m, &B.m, &m) : op == 1 ? ss_union(&A.m, &B.m, &m) : ss_intersect(&A.m, &B.m, &m);
    return toJava(env, s, m);
}

JNI_FN(jobject, subtractMany)(JNIEnv* env, jobject, jobject a, jobjectArray cutters) {
    JMesh A; std::vector<JMesh> C; std::vector<ss_mesh> flat;
    if (!read(env, a, A) || !cutters || !readAll(env, cutters, C, flat)) return fail(env, SS_ERR_INVALID_ARG);
    ss_mesh m{}; ss_status s = ss_subtract_many(&A.m, flat.data(), (uint32_t)flat.size(), &m); return toJava(env, s, m);
}

JNI_FN(jobject, unionMany)(JNIEnv* env, jobject, jobjectArray parts) {
    std::vector<JMesh> P; std::vector<ss_mesh> flat;
    if (!parts || !readAll(env, parts, P, flat)) return fail(env, SS_ERR_INVALID_ARG);
    ss_mesh m{}; ss_status s = ss_union_many(flat.data(), (uint32_t)flat.size(), &m); return toJava(env, s, m);
}

JNI_FN(jboolean, isClosedSolid)(JNIEnv* env, jobject, jobject mesh) {
    JMesh M; return read(env, mesh, M) && ss_is_closed_solid(&M.m) ? JNI_TRUE : JNI_FALSE;
}

JNI_FN(jdouble, volume)(JNIEnv* env, jobject, jobject mesh) {
    JMesh M; return read(env, mesh, M) ? ss_volume(&M.m) : 0.0;
}
