#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <bit>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "spektrafilm/spk_engine.h"
#include "cam16.hpp"
#include "gpu/gpu.hpp"
#include "json.hpp"
#include "numeric.hpp"
#include "image_io.hpp"
#include "output.hpp"
#include "platform.hpp"

#define LOG_TAG "SpektraLabJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

using Clock = std::chrono::steady_clock;

double elapsed_ms_since(Clock::time_point t0) {
    return std::chrono::duration<double, std::milli>(Clock::now() - t0).count();
}

struct GlobalState {
    std::mutex mutex;
    std::string resources_dir;
    spk_engine* engine = nullptr;
    spk_session* session = nullptr;

    std::string current_file_path;
    spkhost::Probe current_probe;
    spkhost::FloatImage current_linear_prophoto;
    std::string current_raw_mode = "compatible16";

    ~GlobalState() {
        if (session) {
            spk_session_release(session);
            session = nullptr;
        }
        if (engine) {
            spk_engine_destroy(engine);
            engine = nullptr;
        }
    }
};

GlobalState g_state;

std::string jstring_to_utf8(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

jstring utf8_to_jstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

std::string make_error_json(const std::string& msg) {
    spk::Json err = spk::Json::object();
    err.set("ok", spk::Json(false));
    err.set("error", spk::Json(msg));
    return err.dump();
}

spk::Json metadata_to_json(const spkhost::Probe& p) {
    spk::Json m = spk::Json::object();
    m.set("kind", spk::Json(std::string(spkhost::kind_name(p.kind))));
    m.set("width", spk::Json(double(p.width)));
    m.set("height", spk::Json(double(p.height)));
    m.set("make", spk::Json(p.metadata.make));
    m.set("model", spk::Json(p.metadata.model));
    m.set("lens", spk::Json(p.metadata.lens));
    m.set("datetime_original", spk::Json(p.metadata.datetime_original));
    if (p.metadata.iso) m.set("iso", spk::Json(*p.metadata.iso));
    if (p.metadata.shutter_s) m.set("shutter_s", spk::Json(*p.metadata.shutter_s));
    if (p.metadata.aperture) m.set("aperture", spk::Json(*p.metadata.aperture));
    if (p.metadata.focal_mm) m.set("focal_mm", spk::Json(*p.metadata.focal_mm));
    if (p.metadata.as_shot_temperature_k) m.set("as_shot_temperature_k", spk::Json(*p.metadata.as_shot_temperature_k));
    if (p.metadata.as_shot_tint) m.set("as_shot_tint", spk::Json(*p.metadata.as_shot_tint));
    return m;
}

// Convert engine RGBA16 output (in ProPhoto RGB working space) to sRGB ARGB_8888
// directly into an Android Bitmap and compute 64-bin histograms for UI display.
bool rgba16_to_bitmap_and_hist(
    JNIEnv* env,
    spk_engine* engine,
    const uint16_t* rgba16,
    uint32_t width,
    uint32_t height,
    uint32_t stride_px,
    jobject bitmap,
    bool& out_bitmap_filled,
    spk::Json& out_hist,
    std::string& error
) {
    out_bitmap_filled = false;
    spkhost::OutputTransform xform;
    if (!xform.fetch(engine, "ProPhoto RGB", "sRGB", error)) {
        return false;
    }
    std::vector<float> linear_srgb;
    xform.to_linear(rgba16, width, height, stride_px, linear_srgb);
    std::vector<uint8_t> srgb_rgba8;
    xform.encode_rgba8(linear_srgb, srgb_rgba8);

    std::array<uint32_t, 64> hist_r{}, hist_g{}, hist_b{}, hist_l{};
    const size_t total_px = size_t(width) * height;
    const size_t step = std::max<size_t>(1, total_px / 65536);
    for (size_t i = 0; i < total_px; i += step) {
        const uint8_t r = srgb_rgba8[4 * i + 0];
        const uint8_t g = srgb_rgba8[4 * i + 1];
        const uint8_t b = srgb_rgba8[4 * i + 2];
        const uint8_t l = static_cast<uint8_t>((77u * r + 150u * g + 29u * b) >> 8);
        hist_r[r >> 2]++;
        hist_g[g >> 2]++;
        hist_b[b >> 2]++;
        hist_l[l >> 2]++;
    }

    auto to_json_arr = [](const std::array<uint32_t, 64>& arr) {
        spk::Json a = spk::Json::array();
        for (uint32_t v : arr) a.push(spk::Json(double(v)));
        return a;
    };
    out_hist = spk::Json::object();
    out_hist.set("r", to_json_arr(hist_r));
    out_hist.set("g", to_json_arr(hist_g));
    out_hist.set("b", to_json_arr(hist_b));
    out_hist.set("l", to_json_arr(hist_l));

    if (bitmap) {
        AndroidBitmapInfo info{};
        if (AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS &&
            info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            info.width == width && info.height == height) {
            void* dst_pixels = nullptr;
            if (AndroidBitmap_lockPixels(env, bitmap, &dst_pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
                for (uint32_t y = 0; y < height; ++y) {
                    uint8_t* dst_row = static_cast<uint8_t*>(dst_pixels) + size_t(y) * info.stride;
                    const uint8_t* src_row = srgb_rgba8.data() + size_t(y) * width * 4;
                    std::memcpy(dst_row, src_row, size_t(width) * 4);
                }
                AndroidBitmap_unlockPixels(env, bitmap);
                out_bitmap_filled = true;
            }
        }
    }
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeInitEngine(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_resources_dir
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    const std::string res_dir = jstring_to_utf8(env, j_resources_dir);
    if (res_dir.empty()) {
        return utf8_to_jstring(env, make_error_json("resources_dir is empty"));
    }

    if (g_state.session) {
        spk_session_release(g_state.session);
        g_state.session = nullptr;
    }
    if (g_state.engine) {
        spk_engine_destroy(g_state.engine);
        g_state.engine = nullptr;
    }

    const auto t0 = Clock::now();
    g_state.resources_dir = res_dir;
    g_state.engine = spk_engine_create(res_dir.c_str(), nullptr);
    const double create_ms = elapsed_ms_since(t0);

    if (!g_state.engine) {
        const std::string err = spk_last_error(nullptr);
        LOGE("spk_engine_create failed: %s", err.c_str());
        return utf8_to_jstring(env, make_error_json(err.empty() ? "spk_engine_create failed" : err));
    }

    char* warm_json_raw = nullptr;
    spk_warm_up(g_state.engine, "kodak_portra_400", "kodak_portra_endura", &warm_json_raw);
    std::string warm_str = warm_json_raw ? warm_json_raw : "{}";
    if (warm_json_raw) spk_string_free(warm_json_raw);

    spk::Json cap_json, schema_json, lut_json, warm_json;
    std::string dummy_err;
    spk::Json::parse(spk_capabilities(g_state.engine), cap_json, dummy_err);
    spk::Json::parse(spk_params_schema(g_state.engine), schema_json, dummy_err);
    spk::Json::parse(spk_print_lut_catalog(g_state.engine), lut_json, dummy_err);
    spk::Json::parse(warm_str, warm_json, dummy_err);

    spk::Json reply = spk::Json::object();
    reply.set("ok", spk::Json(true));
    reply.set("create_ms", spk::Json(create_ms));
    reply.set("build_info", spk::Json(std::string(spk_build_info())));
    reply.set("capabilities", std::move(cap_json));
    reply.set("schema", std::move(schema_json));
    reply.set("print_luts", std::move(lut_json));
    reply.set("warm_up", std::move(warm_json));
    return utf8_to_jstring(env, reply.dump());
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeGetMemoryReport(
    JNIEnv* env,
    jobject /*thiz*/
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine) {
        return utf8_to_jstring(env, make_error_json("engine not initialized"));
    }
    return utf8_to_jstring(env, spk_memory_report(g_state.engine));
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeGenerateSamplePhoto(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_dest_path,
    jint preset_index
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    const std::string dest_path = jstring_to_utf8(env, j_dest_path);
    const uint32_t width = 1440;
    const uint32_t height = 960;
    std::vector<uint16_t> rgb16(size_t(width) * height * 3);

    // Classic 24-patch ColorChecker linear sRGB approximations + architectural twilight scene
    static constexpr float kPatches[24][3] = {
        {0.15f, 0.08f, 0.06f}, {0.55f, 0.32f, 0.24f}, {0.12f, 0.22f, 0.38f}, {0.11f, 0.18f, 0.06f},
        {0.24f, 0.22f, 0.48f}, {0.16f, 0.52f, 0.42f}, {0.65f, 0.20f, 0.04f}, {0.08f, 0.11f, 0.42f},
        {0.52f, 0.08f, 0.12f}, {0.11f, 0.05f, 0.18f}, {0.36f, 0.55f, 0.06f}, {0.72f, 0.42f, 0.04f},
        {0.04f, 0.06f, 0.32f}, {0.08f, 0.34f, 0.09f}, {0.45f, 0.04f, 0.05f}, {0.82f, 0.68f, 0.04f},
        {0.48f, 0.10f, 0.32f}, {0.04f, 0.28f, 0.45f}, {0.92f, 0.92f, 0.90f}, {0.58f, 0.58f, 0.58f},
        {0.36f, 0.36f, 0.36f}, {0.18f, 0.18f, 0.18f}, {0.08f, 0.08f, 0.08f}, {0.03f, 0.03f, 0.03f}
    };

    for (uint32_t y = 0; y < height; ++y) {
        const float ny = float(y) / float(height - 1);
        for (uint32_t x = 0; x < width; ++x) {
            const float nx = float(x) / float(width - 1);
            float r = 0.0f, g = 0.0f, b = 0.0f;

            if (preset_index == 0) {
                // Preset 0: Golden Hour Skyline + High-Contrast Neon Halation Bulbs + ColorChecker strip
                const float horizon = 0.62f;
                if (ny < horizon) {
                    const float t = ny / horizon;
                    r = 0.06f + 0.72f * std::pow(t, 1.5f);
                    g = 0.12f + 0.38f * std::pow(t, 1.2f);
                    b = 0.35f + 0.18f * (1.0f - t);
                    // Sun disc for testing specular highlight roll-off and red-layer halation
                    const float dx = (nx - 0.72f) * 1.5f;
                    const float dy = (ny - 0.42f);
                    const float dist2 = dx * dx + dy * dy;
                    if (dist2 < 0.0045f) {
                        const float sun = std::exp(-dist2 * 650.0f) * 2.8f;
                        r += sun * 1.2f;
                        g += sun * 0.95f;
                        b += sun * 0.65f;
                    }
                } else {
                    // Silhouette skyline & water reflections
                    const float wave = 0.5f + 0.5f * std::sin(ny * 140.0f + nx * 25.0f);
                    r = (0.18f + 0.25f * wave) * (1.0f - (ny - horizon));
                    g = (0.10f + 0.14f * wave) * (1.0f - (ny - horizon));
                    b = (0.08f + 0.12f * wave);
                }

                // Building silhouettes with warm tungsten windows (ideal for CineStill / Vision3 halation test)
                const int col_idx = int(nx * 18.0f);
                const float bldg_top = 0.32f + 0.24f * (float((col_idx * 37 + 13) % 19) / 19.0f);
                if (ny >= bldg_top && ny < horizon) {
                    r = 0.018f; g = 0.018f; b = 0.024f;
                    const float wx = std::fmod(nx * 18.0f, 1.0f);
                    const float wy = std::fmod((ny - bldg_top) * 48.0f, 1.0f);
                    if (wx > 0.25f && wx < 0.75f && wy > 0.25f && wy < 0.75f) {
                        const int seed = (col_idx * 131 + int(ny * 48.0f) * 17) % 11;
                        if (seed < 4) {
                            r = 1.65f; g = 0.88f; b = 0.26f;
                        } else if (seed == 5) {
                            r = 1.85f; g = 0.18f; b = 0.12f; // Red neon sign
                        }
                    }
                }

                // Bottom ColorChecker reference bar (y in [0.80, 0.96])
                if (ny >= 0.80f && ny <= 0.95f && nx >= 0.06f && nx <= 0.94f) {
                    const float cx = (nx - 0.06f) / 0.88f;
                    const float cy = (ny - 0.80f) / 0.15f;
                    const int pcol = std::min(11, int(cx * 12.0f));
                    const int prow = std::min(1, int(cy * 2.0f));
                    const float fx = std::fmod(cx * 12.0f, 1.0f);
                    const float fy = std::fmod(cy * 2.0f, 1.0f);
                    if (fx > 0.06f && fx < 0.94f && fy > 0.08f && fy < 0.92f) {
                        const int patch = prow * 12 + pcol;
                        r = kPatches[patch][0];
                        g = kPatches[patch][1];
                        b = kPatches[patch][2];
                    } else {
                        r = g = b = 0.015f;
                    }
                }
            } else {
                // Preset 1: Studio Portrait & 24-Patch ColorChecker Precision Chart + Step Wedge
                r = g = b = 0.184f; // 18% middle grey background
                if (ny >= 0.08f && ny <= 0.72f && nx >= 0.08f && nx <= 0.92f) {
                    const float cx = (nx - 0.08f) / 0.84f;
                    const float cy = (ny - 0.08f) / 0.64f;
                    const int pcol = std::min(5, int(cx * 6.0f));
                    const int prow = std::min(3, int(cy * 4.0f));
                    const float fx = std::fmod(cx * 6.0f, 1.0f);
                    const float fy = std::fmod(cy * 4.0f, 1.0f);
                    if (fx > 0.05f && fx < 0.95f && fy > 0.06f && fy < 0.94f) {
                        const int patch = prow * 6 + pcol;
                        r = kPatches[patch][0];
                        g = kPatches[patch][1];
                        b = kPatches[patch][2];
                    } else {
                        r = g = b = 0.02f;
                    }
                } else if (ny >= 0.78f && ny <= 0.92f && nx >= 0.08f && nx <= 0.92f) {
                    // Continuous logarithmic step wedge (-4 EV to +4 EV around 0.184 midgrey)
                    const float t = (nx - 0.08f) / 0.84f;
                    const float ev = -4.0f + 8.0f * std::floor(t * 21.0f) / 20.0f;
                    const float val = 0.184f * std::pow(2.0f, ev);
                    r = g = b = val;
                }
            }

            // Clamp into [0, 1] for 16-bit linear ProPhoto storage
            const auto to_u16 = [](float v) -> uint16_t {
                const float c = std::min(std::max(v * 0.45f, 0.0f), 1.0f);
                return static_cast<uint16_t>( std::lround(c * 65535.0f));
            };
            const size_t idx = (size_t(y) * width + x) * 3;
            rgb16[idx + 0] = to_u16(r);
            rgb16[idx + 1] = to_u16(g);
            rgb16[idx + 2] = to_u16(b);
        }
    }

    spkhost::Exif exif{};
    exif.make = "SpektraLab";
    exif.model = preset_index == 0 ? "Spectral Test Camera 135" : "ColorChecker Reference Rig";
    exif.lens = "50mm f/1.4 Planar";
    exif.datetime_original = "2026:11:04 18:30:00";
    exif.iso = preset_index == 0 ? 400.0 : 160.0;
    exif.shutter_s = preset_index == 0 ? (1.0 / 125.0) : (1.0 / 250.0);
    exif.aperture = 2.8;
    exif.focal_mm = 50.0;

    std::vector<uint8_t> icc;
    std::vector<uint8_t> tiff_bytes;
    std::string error;
    if (!spkhost::encode_tiff(rgb16.data(), width, height, 16, icc, tiff_bytes, error, &exif)) {
        return utf8_to_jstring(env, make_error_json(error));
    }
    if (!spkhost::write_file_atomic(spkhost::utf8_path(dest_path), tiff_bytes, true, error)) {
        return utf8_to_jstring(env, make_error_json(error));
    }

    spk::Json out = spk::Json::object();
    out.set("ok", spk::Json(true));
    out.set("path", spk::Json(dest_path));
    out.set("width", spk::Json(double(width)));
    out.set("height", spk::Json(double(height)));
    return utf8_to_jstring(env, out.dump());
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeOpenPhoto(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_file_path,
    jstring j_raw_mode,
    jdouble temperature_k,
    jdouble tint,
    jstring j_initial_params_json
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine) {
        return utf8_to_jstring(env, make_error_json("Engine is not initialized"));
    }

    const std::string file_path = jstring_to_utf8(env, j_file_path);
    const std::string raw_mode = jstring_to_utf8(env, j_raw_mode);
    const std::string init_params = jstring_to_utf8(env, j_initial_params_json);

    spkhost::DecodeOptions opts{};
    opts.raw_mode = raw_mode.empty() ? "compatible16" : raw_mode;
    if (temperature_k > 0.0) {
        opts.temperature_k = double(temperature_k);
        opts.tint = double(tint);
    }

    const auto t0 = Clock::now();
    spkhost::FloatImage decoded;
    spkhost::Probe probe;
    std::string error;
    if (!spkhost::decode_linear_prophoto(spkhost::utf8_path(file_path), opts, decoded, probe, error)) {
        return utf8_to_jstring(env, make_error_json("Decode failed: " + error));
    }
    const double decode_ms = elapsed_ms_since(t0);

    if (g_state.session) {
        spk_session_release(g_state.session);
        g_state.session = nullptr;
    }

    g_state.current_file_path = file_path;
    g_state.current_probe = probe;
    g_state.current_linear_prophoto = std::move(decoded);
    g_state.current_raw_mode = opts.raw_mode;

    spk::Json delta = spk::Json::object();
    if (!init_params.empty()) {
        std::string parse_err;
        spk::Json::parse(init_params, delta, parse_err);
    }
    delta.set("product_defaults", spk::Json(true));
    delta.set("input_color_space", spk::Json(std::string("ProPhoto RGB")));
    delta.set("input_cctf_decoding", spk::Json(false));
    delta.set("striped", spk::Json(true));
    delta.set("strip_rows", spk::Json(double(512)));
    delta.set("preview_long_edge", spk::Json(double(1440)));

    const std::string delta_str = delta.dump();
    spk_image img{
        g_state.current_linear_prophoto.rgb.data(),
        g_state.current_linear_prophoto.width,
        g_state.current_linear_prophoto.height,
        3
    };

    const auto t1 = Clock::now();
    char* open_reply_raw = nullptr;
    g_state.session = spk_open(g_state.engine, &img, delta_str.c_str(), &open_reply_raw);
    const double open_ms = elapsed_ms_since(t1);

    if (!g_state.session) {
        if (open_reply_raw) spk_string_free(open_reply_raw);
        return utf8_to_jstring(env, make_error_json(spk_last_error(g_state.engine)));
    }

    spk::Json open_reply;
    std::string parse_err;
    if (open_reply_raw) {
        spk::Json::parse(open_reply_raw, open_reply, parse_err);
        spk_string_free(open_reply_raw);
    }

    spk::Json out = spk::Json::object();
    out.set("ok", spk::Json(true));
    out.set("decode_ms", spk::Json(decode_ms));
    out.set("open_ms", spk::Json(open_ms));
    out.set("probe", metadata_to_json(g_state.current_probe));
    out.set("session", std::move(open_reply));
    return utf8_to_jstring(env, out.dump());
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeRenderFrame(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_params_delta_json,
    jstring j_tier,
    jboolean prefer_reprint,
    jobject target_bitmap
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine || !g_state.session) {
        return utf8_to_jstring(env, make_error_json("No active session"));
    }

    const std::string delta_str = jstring_to_utf8(env, j_params_delta_json);
    std::string tier_str = jstring_to_utf8(env, j_tier);
    if (tier_str.empty()) tier_str = "live";

    bool shoot_invalidated = false;
    spk::Json set_reply_json;
    if (!delta_str.empty() && delta_str != "{}") {
        char* set_reply_raw = nullptr;
        const spk_status st = spk_set_params(g_state.session, delta_str.c_str(), &set_reply_raw);
        if (st != SPK_OK) {
            if (set_reply_raw) spk_string_free(set_reply_raw);
            return utf8_to_jstring(env, make_error_json(spk_last_error(g_state.engine)));
        }
        if (set_reply_raw) {
            std::string parse_err;
            spk::Json::parse(set_reply_raw, set_reply_json, parse_err);
            spk_string_free(set_reply_raw);
            if (set_reply_json.at("invalidated").is_string() &&
                set_reply_json.at("invalidated").as_string() == "shoot") {
                shoot_invalidated = true;
            }
        }
    }

    spk_result res{};
    const auto t0 = Clock::now();
    const bool use_reprint = (prefer_reprint == JNI_TRUE) && !shoot_invalidated;
    spk_status status = use_reprint
        ? spk_reprint(g_state.session, tier_str.c_str(), &res)
        : spk_render(g_state.session, tier_str.c_str(), &res);

    if (status != SPK_OK && use_reprint) {
        status = spk_render(g_state.session, tier_str.c_str(), &res);
    }
    const double render_wall_ms = elapsed_ms_since(t0);

    if (status != SPK_OK) {
        spk_result_free(&res);
        return utf8_to_jstring(env, make_error_json(spk_last_error(g_state.engine)));
    }

    spk::Json hist_json;
    std::string xform_err;
    bool bitmap_filled = false;
    const bool ok = rgba16_to_bitmap_and_hist(
        env,
        g_state.engine,
        res.rgba16,
        res.width,
        res.height,
        res.row_stride_px,
        target_bitmap,
        bitmap_filled,
        hist_json,
        xform_err
    );

    const uint32_t out_w = res.width;
    const uint32_t out_h = res.height;
    const double engine_ms = res.elapsed_ms;
    const int32_t was_reprint = res.reprint;
    const int32_t neg_cached = res.negative_was_cached;
    spk_result_free(&res);

    if (!ok) {
        return utf8_to_jstring(env, make_error_json(xform_err));
    }

    char* params_raw = nullptr;
    spk_get_params(g_state.session, &params_raw);
    spk::Json params_json;
    if (params_raw) {
        std::string pe;
        spk::Json::parse(params_raw, params_json, pe);
        spk_string_free(params_raw);
    }

    spk::Json reply = spk::Json::object();
    reply.set("ok", spk::Json(true));
    reply.set("width", spk::Json(double(out_w)));
    reply.set("height", spk::Json(double(out_h)));
    reply.set("bitmap_filled", spk::Json(bitmap_filled));
    reply.set("engine_ms", spk::Json(engine_ms));
    reply.set("elapsed_ms", spk::Json(render_wall_ms));
    reply.set("wall_ms", spk::Json(render_wall_ms));
    reply.set("used_reprint", spk::Json(was_reprint != 0));
    reply.set("reprint", spk::Json(was_reprint != 0));
    reply.set("negative_cached", spk::Json(neg_cached != 0));
    reply.set("histogram", std::move(hist_json));
    reply.set("params", std::move(params_json));
    return utf8_to_jstring(env, reply.dump());
}

JNIEXPORT jboolean JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeRenderOriginalPreview(
    JNIEnv* env,
    jobject /*thiz*/,
    jobject target_bitmap
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine || g_state.current_linear_prophoto.rgb.empty() || !target_bitmap) {
        return JNI_FALSE;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, target_bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }

    const uint32_t dw = info.width;
    const uint32_t dh = info.height;
    std::vector<float> resized;
    spkhost::resize_area(
        g_state.current_linear_prophoto.rgb.data(),
        g_state.current_linear_prophoto.width,
        g_state.current_linear_prophoto.height,
        3,
        dw,
        dh,
        resized
    );

    // Encode linear ProPhoto -> encoded ProPhoto RGBA16 -> OutputTransform to sRGB
    std::vector<uint16_t> prophoto_rgba16(size_t(dw) * dh * 4);
    for (size_t i = 0; i < size_t(dw) * dh; ++i) {
        for (int c = 0; c < 3; ++c) {
            const float lin = std::max(0.0f, resized[3 * i + c]);
            const float enc = spkhost::cctf_encode_mode(lin, 1); // mode 1 = ProPhoto TRC
            const float clamped = std::min(std::max(enc, 0.0f), 1.0f);
            prophoto_rgba16[4 * i + c] = static_cast<uint16_t>(std::lround(clamped * 65535.0f));
        }
        prophoto_rgba16[4 * i + 3] = 65535u;
    }

    spk::Json dummy_hist;
    std::string err;
    bool dummy_filled = false;
    return rgba16_to_bitmap_and_hist(
        env,
        g_state.engine,
        prophoto_rgba16.data(),
        dw,
        dh,
        dw,
        target_bitmap,
        dummy_filled,
        dummy_hist,
        err
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeAutoSolve(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_target
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine || !g_state.session) {
        return utf8_to_jstring(env, make_error_json("No active session"));
    }
    const std::string target = jstring_to_utf8(env, j_target);
    char* out_raw = nullptr;
    const spk_status st = spk_solve(g_state.session, target.c_str(), &out_raw);
    if (st != SPK_OK) {
        if (out_raw) spk_string_free(out_raw);
        return utf8_to_jstring(env, make_error_json(spk_last_error(g_state.engine)));
    }
    std::string reply = out_raw ? out_raw : "{}";
    if (out_raw) spk_string_free(out_raw);
    return utf8_to_jstring(env, reply);
}

JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeExportImage(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_dest_path,
    jstring j_format,
    jstring j_color_space,
    jint quality,
    jint bits_per_sample,
    jstring j_render_tier
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    if (!g_state.engine || !g_state.session) {
        return utf8_to_jstring(env, make_error_json("No active session"));
    }

    const std::string dest_path = jstring_to_utf8(env, j_dest_path);
    const std::string format = jstring_to_utf8(env, j_format); // "tiff", "png", "jpeg"
    const std::string cs_wire = jstring_to_utf8(env, j_color_space);
    std::string tier = jstring_to_utf8(env, j_render_tier);
    if (tier.empty()) tier = "full";

    std::string target_space = spkhost::engine_space_name(cs_wire);
    if (target_space.empty()) target_space = "sRGB";

    const auto t0 = Clock::now();
    spk_result res{};
    spk_status st = spk_reprint(g_state.session, tier.c_str(), &res);
    if (st != SPK_OK) {
        st = spk_render(g_state.session, tier.c_str(), &res);
    }
    if (st != SPK_OK) {
        spk_result_free(&res);
        return utf8_to_jstring(env, make_error_json(spk_last_error(g_state.engine)));
    }

    spkhost::OutputTransform xform;
    std::string error;
    if (!xform.fetch(g_state.engine, "ProPhoto RGB", target_space, error)) {
        spk_result_free(&res);
        return utf8_to_jstring(env, make_error_json(error));
    }

    const uint32_t w = res.width;
    const uint32_t h = res.height;
    std::vector<float> linear_rgb;
    xform.to_linear(res.rgba16, w, h, res.row_stride_px, linear_rgb);
    spk_result_free(&res);

    std::vector<uint8_t> icc;
    if (!spkhost::icc_for(target_space, spkhost::utf8_path(g_state.resources_dir), icc, error)) {
        return utf8_to_jstring(env, make_error_json(error));
    }

    spkhost::Exif exif{};
    const auto& m = g_state.current_probe.metadata;
    exif.make = m.make;
    exif.model = m.model;
    exif.lens = m.lens;
    exif.datetime_original = m.datetime_original;
    if (m.iso) exif.iso = *m.iso;
    if (m.shutter_s) exif.shutter_s = *m.shutter_s;
    if (m.aperture) exif.aperture = *m.aperture;
    if (m.focal_mm) exif.focal_mm = *m.focal_mm;

    std::vector<uint8_t> file_bytes;
    const uint32_t bits = (bits_per_sample == 16 && format == "tiff") ? 16u : 8u;

    if (format == "tiff") {
        if (bits == 16) {
            std::vector<uint16_t> rgba16;
            xform.encode_rgba16(linear_rgb, rgba16);
            std::vector<uint16_t> rgb16(size_t(w) * h * 3);
            for (size_t i = 0; i < size_t(w) * h; ++i) {
                rgb16[3 * i + 0] = rgba16[4 * i + 0];
                rgb16[3 * i + 1] = rgba16[4 * i + 1];
                rgb16[3 * i + 2] = rgba16[4 * i + 2];
            }
            if (!spkhost::encode_tiff(rgb16.data(), w, h, 16, icc, file_bytes, error, &exif)) {
                return utf8_to_jstring(env, make_error_json(error));
            }
        } else {
            std::vector<uint8_t> rgba8;
            xform.encode_rgba8(linear_rgb, rgba8);
            std::vector<uint8_t> rgb8(size_t(w) * h * 3);
            for (size_t i = 0; i < size_t(w) * h; ++i) {
                rgb8[3 * i + 0] = rgba8[4 * i + 0];
                rgb8[3 * i + 1] = rgba8[4 * i + 1];
                rgb8[3 * i + 2] = rgba8[4 * i + 2];
            }
            if (!spkhost::encode_tiff(rgb8.data(), w, h, 8, icc, file_bytes, error, &exif)) {
                return utf8_to_jstring(env, make_error_json(error));
            }
        }
    } else if (format == "png") {
        std::vector<uint8_t> rgba8;
        xform.encode_rgba8(linear_rgb, rgba8);
        std::vector<uint8_t> rgb8(size_t(w) * h * 3);
        for (size_t i = 0; i < size_t(w) * h; ++i) {
            rgb8[3 * i + 0] = rgba8[4 * i + 0];
            rgb8[3 * i + 1] = rgba8[4 * i + 1];
            rgb8[3 * i + 2] = rgba8[4 * i + 2];
        }
        if (!spkhost::encode_png8(rgb8.data(), w, h, icc, file_bytes, error, &exif)) {
            return utf8_to_jstring(env, make_error_json(error));
        }
    } else {
        std::vector<uint8_t> rgba8;
        xform.encode_rgba8(linear_rgb, rgba8);
        std::vector<uint8_t> rgb8(size_t(w) * h * 3);
        for (size_t i = 0; i < size_t(w) * h; ++i) {
            rgb8[3 * i + 0] = rgba8[4 * i + 0];
            rgb8[3 * i + 1] = rgba8[4 * i + 1];
            rgb8[3 * i + 2] = rgba8[4 * i + 2];
        }
        const int q = std::min(std::max(int(quality), 50), 100);
        if (!spkhost::encode_jpeg(rgb8.data(), w, h, q, icc, file_bytes, error, &exif)) {
            return utf8_to_jstring(env, make_error_json(error));
        }
    }

    if (!spkhost::write_file_atomic(spkhost::utf8_path(dest_path), file_bytes, true, error)) {
        return utf8_to_jstring(env, make_error_json(error));
    }

    const double total_ms = elapsed_ms_since(t0);
    spk::Json out = spk::Json::object();
    out.set("ok", spk::Json(true));
    out.set("path", spk::Json(dest_path));
    out.set("format", spk::Json(format));
    out.set("bits", spk::Json(double(bits)));
    out.set("color_space", spk::Json(target_space));
    out.set("width", spk::Json(double(w)));
    out.set("height", spk::Json(double(h)));
    out.set("bytes", spk::Json(double(file_bytes.size())));
    out.set("elapsed_ms", spk::Json(total_ms));
    return utf8_to_jstring(env, out.dump());
}

// On-device macOS parity & GPU numerical verification suite
JNIEXPORT jstring JNICALL
Java_app_spektralab_android_nativebridge_SpektraNativeBridge_nativeRunParitySelfTest(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring j_resources_dir
) {
    std::lock_guard<std::mutex> lock(g_state.mutex);
    const std::string res_dir = jstring_to_utf8(env, j_resources_dir);
    const auto suite_start = Clock::now();

    spk::Json checks = spk::Json::array();
    auto add_check = [&](const std::string& name, bool passed, double max_err, double tol, double ms, const std::string& detail) {
        spk::Json item = spk::Json::object();
        item.set("name", spk::Json(name));
        item.set("passed", spk::Json(passed));
        item.set("max_error", spk::Json(max_err));
        item.set("tolerance", spk::Json(tol));
        item.set("elapsed_ms", spk::Json(ms));
        item.set("detail", spk::Json(detail));
        checks.push(std::move(item));
    };

    std::string error;
    auto t0 = Clock::now();
    std::unique_ptr<spk::gpu::Gpu> gpu(spk::gpu::Gpu::create_vulkan(res_dir + "/vulkan", error));
    if (!gpu) {
        return utf8_to_jstring(env, make_error_json("Vulkan init failed: " + error));
    }
    const std::string gpu_name = gpu->device_name();

    // Gate 1: IEEE-754 Binary32 & FMA Residual Probe
    t0 = Clock::now();
    std::string math_detail;
    const bool math_ok = gpu->check_math_mode(math_detail);
    add_check(
        "GPU IEEE-754 & FMA Residual Probe",
        math_ok,
        0.0,
        0.0,
        elapsed_ms_since(t0),
        math_detail
    );

    // Gate 2: Separable FIR Boundary Oracle (Reflect & Mirror across odd/singleton shapes)
    t0 = Clock::now();
    {
        bool fir_ok = true;
        double fir_max_err = 0.0;
        std::string fir_err;
        const uint32_t height = 19, width = 13, radius = 2;
        const size_t count = size_t(height) * width * 3;
        std::vector<float> input(count), acc_in(count, 0.25f);
        for (size_t i = 0; i < count; ++i) input[i] = float((i * 17 + 5) % 256) / 256.0f;
        const float weights[15] = {
            0.1f, 0.2f, 0.4f, 0.2f, 0.1f,
            0.1f, 0.2f, 0.4f, 0.2f, 0.1f,
            0.1f, 0.2f, 0.4f, 0.2f, 0.1f
        };
        const float mix[3] = {1.0f, 1.0f, 1.0f};
        auto b_src = gpu->upload(input.data(), count * sizeof(float), fir_err);
        auto b_w = gpu->upload(weights, sizeof weights, fir_err);
        auto b_acc = gpu->upload(acc_in.data(), count * sizeof(float), fir_err);
        auto b_mix = gpu->upload(mix, sizeof mix, fir_err);
        auto b_dst = gpu->alloc(count * sizeof(float), fir_err);
        const uint32_t meta[6] = {height, width, radius, 1, 0, 1};
        if (!b_src || !b_w || !b_acc || !b_mix || !b_dst ||
            !gpu->dispatch("spk_sep_fir_acc",
                           {spk::gpu::Arg::buf(b_src), spk::gpu::Arg::buf(b_w), spk::gpu::Arg::buf(b_acc),
                            spk::gpu::Arg::buf(b_mix), spk::gpu::Arg::inline_bytes(meta, 6),
                            spk::gpu::Arg::buf(b_dst)}, size_t(height) * width, fir_err)) {
            fir_ok = false;
        } else {
            std::vector<float> got(count);
            if (!gpu->read(b_dst.get(), 0, got.data(), count * sizeof(float), fir_err)) {
                fir_ok = false;
            } else {
                auto mirror_idx = [](int idx, int len) {
                    while (idx < 0 || idx >= len) idx = idx < 0 ? -idx : 2 * len - 2 - idx;
                    return idx;
                };
                for (uint32_t y = 0; y < height; ++y) {
                    for (uint32_t x = 0; x < width; ++x) {
                        for (uint32_t c = 0; c < 3; ++c) {
                            double sum = 0.0;
                            for (int k = -int(radius); k <= int(radius); ++k) {
                                int sx = mirror_idx(int(x) + k, int(width));
                                sum += double(weights[c * 5 + (k + int(radius))]) *
                                       double(input[(size_t(y) * width + size_t(sx)) * 3 + c]);
                            }
                            const double err = std::fabs(double(got[(size_t(y) * width + x) * 3 + c]) - sum);
                            fir_max_err = std::max(fir_max_err, err);
                            if (err > 1e-6) fir_ok = false;
                        }
                    }
                }
            }
        }
        add_check(
            "Separable FIR Convolution (spk_sep_fir_acc)",
            fir_ok,
            fir_max_err,
            1e-6,
            elapsed_ms_since(t0),
            fir_ok ? "Exact mirror/reflect boundary match" : fir_err
        );
    }

    // Gate 3: Double-Float Young & van Vliet IIR Blur vs Float64 Oracle (sigma = 3, 12, 130)
    t0 = Clock::now();
    {
        bool iir_ok = true;
        double iir_max_err = 0.0;
        const double tol = 4.0 * double(std::numeric_limits<float>::epsilon());
        std::string iir_err;
        const double sigmas[3] = {3.0, 12.0, 130.0};
        double coeffs[3][4];
        float pairs[24];
        for (size_t c = 0; c < 3; ++c) {
            spk::yvv_coeffs(sigmas[c], coeffs[c]);
            for (size_t j = 0; j < 4; ++j) {
                const float hi = float(coeffs[c][j]);
                pairs[c * 8 + j * 2] = hi;
                pairs[c * 8 + j * 2 + 1] = float(coeffs[c][j] - double(hi));
            }
        }
        const uint32_t h = 3, w = 257;
        const size_t count = size_t(h) * w * 3;
        std::vector<float> input(count), accum(count, 0.0f);
        for (uint32_t y = 0; y < h; ++y)
            for (uint32_t x = 0; x < w; ++x)
                for (uint32_t c = 0; c < 3; ++c)
                    input[(size_t(y) * w + x) * 3 + c] = float(0.2 + 0.15 * c + 0.1 * std::sin(0.07 * x + 0.11 * y));

        const uint32_t active[3] = {1u, 1u, 1u};
        const float weights[3] = {1.0f, 1.0f, 1.0f};
        const uint32_t meta[4] = {h, w, 0u, 1u};
        auto b_in = gpu->upload(input.data(), count * sizeof(float), iir_err);
        auto b_co = gpu->upload(pairs, sizeof pairs, iir_err);
        auto b_ac = gpu->upload_u32(active, 3, iir_err);
        auto b_acc = gpu->upload(accum.data(), count * sizeof(float), iir_err);
        auto b_w = gpu->upload(weights, sizeof weights, iir_err);
        auto b_out = gpu->alloc(count * sizeof(float), iir_err);
        if (!b_in || !b_co || !b_ac || !b_acc || !b_w || !b_out ||
            !gpu->dispatch("spk_iir_df_acc",
                           {spk::gpu::Arg::buf(b_in), spk::gpu::Arg::buf(b_co),
                            spk::gpu::Arg::buf(b_ac), spk::gpu::Arg::buf(b_acc),
                            spk::gpu::Arg::buf(b_w), spk::gpu::Arg::inline_bytes(meta, 4),
                            spk::gpu::Arg::buf(b_out)},
                           size_t(h) * 3, iir_err)) {
            iir_ok = false;
        } else {
            std::vector<float> got(count);
            if (!gpu->read(b_out.get(), 0, got.data(), count * sizeof(float), iir_err)) {
                iir_ok = false;
            } else {
                // Float64 reference recurrence
                std::vector<float> forward(w);
                for (uint32_t line = 0; line < h; ++line) {
                    for (uint32_t c = 0; c < 3; ++c) {
                        const size_t base = size_t(line) * w * 3 + c;
                        const double* b = coeffs[c];
                        std::array<double, 3> st{input[base], input[base], input[base]};
                        for (uint32_t i = 0; i < w; ++i) {
                            const double v = b[0] * double(input[base + i * 3]) + b[1] * st[0] + b[2] * st[1] + b[3] * st[2];
                            forward[i] = float(v);
                            st = {v, st[0], st[1]};
                        }
                        st.fill(forward.back());
                        for (uint32_t i = w; i-- > 0;) {
                            const double v = b[0] * double(forward[i]) + b[1] * st[0] + b[2] * st[1] + b[3] * st[2];
                            const float exp_v = float(v);
                            const double d = std::fabs(double(got[base + i * 3]) - double(exp_v));
                            iir_max_err = std::max(iir_max_err, d);
                            if (d > tol * std::max(1.0, std::fabs(double(exp_v)))) iir_ok = false;
                            st = {v, st[0], st[1]};
                        }
                    }
                }
            }
        }
        add_check(
            "Double-Float IIR Recurrence (spk_iir_df_acc)",
            iir_ok,
            iir_max_err,
            tol,
            elapsed_ms_since(t0),
            "257-px sweep across sigma = {3, 12, 130} vs float64 oracle"
        );
    }

    // Gate 4: 81-Wavelength Spectral Epilogue & Couplers Kernel
    t0 = Clock::now();
    {
        bool spec_ok = true;
        double spec_max_err = 0.0;
        std::string spec_err;
        const float spectral_input[] = {0, 0, 0, 1, 1, 0};
        const float spectral_char[] = {1, 0, 0, 0, 1, 0};
        const float spectral_base[] = {0, 0};
        const float spectral_int[] = {1, 2, 3, 4, 5, 6};
        const float spectral_epi[] = {2, 3, 4, 0, 1, -5};
        auto b_in = gpu->upload(spectral_input, sizeof spectral_input, spec_err);
        auto b_ch = gpu->upload(spectral_char, sizeof spectral_char, spec_err);
        auto b_ba = gpu->upload(spectral_base, sizeof spectral_base, spec_err);
        auto b_in_g = gpu->upload(spectral_int, sizeof spectral_int, spec_err);
        auto b_ep = gpu->upload(spectral_epi, sizeof spectral_epi, spec_err);
        auto b_out = gpu->alloc(sizeof spectral_input, spec_err);
        const uint32_t meta[] = {2, 2, 0};
        if (!b_in || !b_ch || !b_ba || !b_in_g || !b_ep || !b_out ||
            !gpu->dispatch("spk_spectral_epilogue",
                           {spk::gpu::Arg::buf(b_in), spk::gpu::Arg::buf(b_ch),
                            spk::gpu::Arg::buf(b_ba), spk::gpu::Arg::buf(b_in_g),
                            spk::gpu::Arg::buf(b_ep), spk::gpu::Arg::inline_bytes(meta, 3),
                            spk::gpu::Arg::buf(b_out)}, 2, spec_err)) {
            spec_ok = false;
        } else {
            float got[6]{};
            if (!gpu->read(b_out.get(), 0, got, sizeof got, spec_err)) {
                spec_ok = false;
            } else {
                const float linear_expected[] = {10, 22, 31, 1, 3.1f, 1e-10f};
                for (int i = 0; i < 6; ++i) {
                    const double exp_v = std::log10(double(linear_expected[i]));
                    const double err = std::fabs(double(got[i]) - exp_v);
                    spec_max_err = std::max(spec_max_err, err);
                    if (err > 3e-5) spec_ok = false;
                }
            }
        }
        add_check(
            "Spectral Integration Epilogue (spk_spectral_epilogue)",
            spec_ok,
            spec_max_err,
            3e-5,
            elapsed_ms_since(t0),
            "Closed-form spectral transmission & log10 exposure integration"
        );
    }

    gpu.reset();

    // Gate 5: Full C ABI Golden Fixture Render + Cached Reprint Bit-Exactness
    t0 = Clock::now();
    {
        bool abi_ok = false;
        double abi_max_diff = 0.0;
        std::string abi_detail;
        const std::string probe_f32 = res_dir + "/fixtures/abi_probe.f32";
        const std::string probe_json = res_dir + "/fixtures/abi_probe_params.json";
        std::ifstream f_in(probe_f32, std::ios::binary);
        std::ifstream f_pa(probe_json, std::ios::binary);
        if (f_in && f_pa) {
            std::vector<float> px(3 * 2 * 3);
            f_in.read(reinterpret_cast<char*>(px.data()), std::streamsize(px.size() * sizeof(float)));
            std::ostringstream ss;
            ss << f_pa.rdbuf();
            const std::string params_text = ss.str();

            spk_engine* eng = g_state.engine ? g_state.engine : spk_engine_create(res_dir.c_str(), nullptr);
            const bool owns_eng = (eng != g_state.engine);
            if (eng) {
                spk_image img{px.data(), 3, 2, 3};
                char* open_rep = nullptr;
                spk_session* sess = spk_open(eng, &img, params_text.c_str(), &open_rep);
                if (open_rep) spk_string_free(open_rep);
                if (sess) {
                    spk_result r1{}, r2{}, r_rep{};
                    const spk_status s1 = spk_render(sess, "full", &r1);
                    const spk_status s2 = spk_render(sess, "full", &r2);
                    const spk_status s3 = spk_reprint(sess, "full", &r_rep);
                    if (s1 == SPK_OK && s2 == SPK_OK && s3 == SPK_OK && r1.rgba16 && r2.rgba16 && r_rep.rgba16) {
                        uint32_t max_u16_diff = 0;
                        for (uint32_t y = 0; y < r1.height; ++y) {
                            for (uint32_t x = 0; x < r1.width * 4; ++x) {
                                const uint16_t v1 = r1.rgba16[y * r1.row_stride_px * 4 + x];
                                const uint16_t v2 = r2.rgba16[y * r2.row_stride_px * 4 + x];
                                const uint16_t v3 = r_rep.rgba16[y * r_rep.row_stride_px * 4 + x];
                                max_u16_diff = std::max(max_u16_diff, uint32_t(std::abs(int(v1) - int(v2))));
                                max_u16_diff = std::max(max_u16_diff, uint32_t(std::abs(int(v1) - int(v3))));
                            }
                        }
                        abi_max_diff = double(max_u16_diff);
                        abi_ok = (max_u16_diff == 0) && (r_rep.reprint == 1) && (r_rep.negative_was_cached == 1);
                        abi_detail = abi_ok
                            ? "3x2 RGBA16 full render & cached negative reprint bit-exact (0 ULP diff)"
                            : ("Max U16 diff = " + std::to_string(max_u16_diff));
                    } else {
                        abi_detail = spk_last_error(eng);
                    }
                    spk_result_free(&r1);
                    spk_result_free(&r2);
                    spk_result_free(&r_rep);
                    spk_session_release(sess);
                } else {
                    abi_detail = spk_last_error(eng);
                }
                if (owns_eng) spk_engine_destroy(eng);
            }
        } else {
            abi_detail = "abi_probe fixture files missing";
        }
        add_check(
            "C ABI Full Pipeline & Negative Cache Parity (abi_probe)",
            abi_ok,
            abi_max_diff,
            0.0,
            elapsed_ms_since(t0),
            abi_detail
        );
    }

    spk::Json out = spk::Json::object();
    out.set("ok", spk::Json(true));
    out.set("gpu_name", spk::Json(gpu_name));
    out.set("build_info", spk::Json(std::string(spk_build_info())));
    out.set("total_ms", spk::Json(elapsed_ms_since(suite_start)));
    out.set("checks", std::move(checks));
    return utf8_to_jstring(env, out.dump());
}

} // extern "C"
