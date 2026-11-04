cmake_minimum_required(VERSION 3.22.1)
project(spektralab_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

get_filename_component(SPEKTRALAB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
set(ENGINE_DIR "${SPEKTRALAB_ROOT}/engine")
set(DEPS_DIR "${SPEKTRALAB_ROOT}/build/deps")

# 1. Vulkan Headers (pinned in build/deps/Vulkan-Headers or Android NDK sysroot fallback)
if(EXISTS "${DEPS_DIR}/Vulkan-Headers/include/vulkan/vulkan_core.h")
    set(VULKAN_INCLUDE_DIR "${DEPS_DIR}/Vulkan-Headers/include")
else()
    set(VULKAN_INCLUDE_DIR "${CMAKE_SYSROOT}/usr/include")
endif()

# 2. volk dynamic Vulkan loader (dlopens libvulkan.so at runtime on Android)
add_library(spk_volk STATIC "${ENGINE_DIR}/third_party/volk/volk.c")
set_target_properties(spk_volk PROPERTIES POSITION_INDEPENDENT_CODE ON LINKER_LANGUAGE C)
target_include_directories(spk_volk PUBLIC
    "${ENGINE_DIR}/third_party/volk"
    "${VULKAN_INCLUDE_DIR}"
)
target_link_libraries(spk_volk PUBLIC dl)

# 3. Portable C++20 spectral & colour core
add_library(spektralab_core STATIC
    "${ENGINE_DIR}/src/core/blob.cpp"
    "${ENGINE_DIR}/src/core/cam16.cpp"
    "${ENGINE_DIR}/src/core/colour.cpp"
    "${ENGINE_DIR}/src/core/curves.cpp"
    "${ENGINE_DIR}/src/core/digital_intermediate.cpp"
    "${ENGINE_DIR}/src/core/hanatos.cpp"
    "${ENGINE_DIR}/src/core/json.cpp"
    "${ENGINE_DIR}/src/core/latitude_fit.cpp"
    "${ENGINE_DIR}/src/core/numeric.cpp"
    "${ENGINE_DIR}/src/core/params.cpp"
    "${ENGINE_DIR}/src/core/print_lut.cpp"
    "${ENGINE_DIR}/src/core/printing.cpp"
    "${ENGINE_DIR}/src/core/profile.cpp"
    "${ENGINE_DIR}/src/core/setup_cache.cpp"
    "${ENGINE_DIR}/src/core/spectral.cpp"
)
target_compile_features(spektralab_core PUBLIC cxx_std_20)
target_include_directories(spektralab_core PUBLIC "${ENGINE_DIR}/src/core")

# 4. Render pipeline + Vulkan compute backend
add_library(spektralab_engine STATIC
    "${ENGINE_DIR}/src/pipeline/blur.cpp"
    "${ENGINE_DIR}/src/pipeline/engine.cpp"
    "${ENGINE_DIR}/src/pipeline/pipeline.cpp"
    "${ENGINE_DIR}/src/pipeline/scene_latitude.cpp"
    "${ENGINE_DIR}/src/pipeline/windows_unported.cpp"
    "${ENGINE_DIR}/src/gpu/vulkan_gpu.cpp"
)
target_compile_features(spektralab_engine PUBLIC cxx_std_20)
target_include_directories(spektralab_engine PUBLIC
    "${ENGINE_DIR}/include"
    "${ENGINE_DIR}/src"
    "${ENGINE_DIR}/src/core"
)
target_link_libraries(spektralab_engine PUBLIC spektralab_core spk_volk)

# 5. LibRaw 0.22.2 (pinned source from build/deps/LibRaw-0.22.2)
set(LIBRAW_DIR "${DEPS_DIR}/LibRaw-0.22.2")
if(NOT EXISTS "${LIBRAW_DIR}/libraw/libraw.h")
    message(FATAL_ERROR "LibRaw-0.22.2 missing in ${LIBRAW_DIR}; run engine/stage-android-assets.sh first")
endif()
file(GLOB_RECURSE LIBRAW_SOURCES CONFIGURE_DEPENDS "${LIBRAW_DIR}/src/*.cpp")
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX "/[^/]*_ph\\.cpp$")
add_library(spk_libraw STATIC ${LIBRAW_SOURCES})
target_include_directories(spk_libraw PUBLIC "${LIBRAW_DIR}")
target_compile_definitions(spk_libraw PUBLIC LIBRAW_NODLL PRIVATE LIBRAW_NOTHREADS)
target_compile_features(spk_libraw PUBLIC cxx_std_14)
target_compile_options(spk_libraw PRIVATE -w)
target_link_libraries(spk_libraw PUBLIC m)

# 6. Host RAW/image decode, ICC parser, OutputTransform (CAM16-UCS), and TIFF/PNG/JPEG/EXIF encoders
add_library(spk_host_io STATIC
    "${ENGINE_DIR}/src/io/raw_decoder.cpp"
    "${ENGINE_DIR}/src/io/raw_headroom.cpp"
    "${ENGINE_DIR}/src/io/image_writer.cpp"
    "${ENGINE_DIR}/host/image_io.cpp"
    "${ENGINE_DIR}/host/output.cpp"
    "${ENGINE_DIR}/host/platform.cpp"
    "${ENGINE_DIR}/host/stb_impl.cpp"
    "${ENGINE_DIR}/host/tiff_ifd.cpp"
    "${ENGINE_DIR}/host/white_balance.cpp"
)
target_compile_features(spk_host_io PUBLIC cxx_std_20)
target_include_directories(spk_host_io PUBLIC
    "${ENGINE_DIR}/host"
    "${ENGINE_DIR}/include"
    "${ENGINE_DIR}/src"
    "${ENGINE_DIR}/src/core"
    "${ENGINE_DIR}/third_party/stb"
    "${LIBRAW_DIR}"
)
target_link_libraries(spk_host_io PUBLIC spk_libraw spektralab_engine spektralab_core)

# 7. Android JNI shared library
add_library(spektralab_jni SHARED
    jni_spektralab.cpp
)
target_compile_features(spektralab_jni PRIVATE cxx_std_20)
target_include_directories(spektralab_jni PRIVATE
    "${ENGINE_DIR}/host"
    "${ENGINE_DIR}/include"
    "${ENGINE_DIR}/src"
    "${ENGINE_DIR}/src/core"
    "${ENGINE_DIR}/third_party/stb"
)
target_link_libraries(spektralab_jni PRIVATE
    spk_host_io
    spektralab_engine
    spektralab_core
    spk_volk
    spk_libraw
    android
    jnigraphics
    log
    dl
    m
)
