cmake_minimum_required(VERSION 3.8)
project(nova-renderer)

# Functions
function(nova_set_all_target_outputs target output_dir)
    set_target_properties(${target} PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${output_dir})
    set_target_properties(${target} PROPERTIES LIBRARY_OUTPUT_DIRECTORY_DEBUG ${output_dir})
    set_target_properties(${target} PROPERTIES LIBRARY_OUTPUT_DIRECTORY_RELEASE ${output_dir})
    set_target_properties(${target} PROPERTIES ARCHIVE_OUTPUT_DIRECTORY ${output_dir})
    set_target_properties(${target} PROPERTIES ARCHIVE_OUTPUT_DIRECTORY_DEBUG ${output_dir})
    set_target_properties(${target} PROPERTIES ARCHIVE_OUTPUT_DIRECTORY_RELEASE ${output_dir})
    set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${output_dir})
    set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY_DEBUG ${output_dir})
    set_target_properties(${target} PROPERTIES RUNTIME_OUTPUT_DIRECTORY_RELEASE ${output_dir})
endfunction()

# Initialize cmake options
set(CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
set(CMAKE_BUILD_TYPE DEBUG)

set(CMAKE_INSTALL_PREFIX ${CMAKE_CURRENT_LIST_DIR})

# Setup 3rd party dependencies.
set(3RD_PARTY_DIR ${CMAKE_CURRENT_LIST_DIR}/3rdparty)

find_package(Vulkan)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

set(BUILD_DEMOS OFF)
set(BUILD_ICD OFF)

set(SHADERC_SKIP_TESTS TRUE)

set(BUILD_SHARED_LIBS TRUE)

set(EASY_PROFILER_NO_GUI ON)
set(BUILD_SHARED_LIBS ON)

# need to compile GLFW and JSON
add_subdirectory(${3RD_PARTY_DIR}/glfw)
add_subdirectory(${3RD_PARTY_DIR}/json)
add_subdirectory(${3RD_PARTY_DIR}/glm)
add_subdirectory(${3RD_PARTY_DIR}/easyloggingpp)
add_subdirectory(${3RD_PARTY_DIR}/miniz)
add_subdirectory(${3RD_PARTY_DIR}/googletest)
add_subdirectory(${3RD_PARTY_DIR}/optional)
add_subdirectory(${3RD_PARTY_DIR}/glslang)
add_subdirectory(${3RD_PARTY_DIR}/shaderc)
add_subdirectory(${3RD_PARTY_DIR}/profiler)

include_directories(SYSTEM
        $ENV{VULKAN_SDK}/include
        ${JNI_INCLUDE_DIRS}
        ${3RD_PARTY_DIR}/glfw/include
        ${3RD_PARTY_DIR}/easyloggingpp/src
        ${3RD_PARTY_DIR}/glad/include
        ${3RD_PARTY_DIR}/glm
        ${3RD_PARTY_DIR}/miniz
        ${3RD_PARTY_DIR}/json/include
        ${3RD_PARTY_DIR}/googletest/googletest/include
        ${3RD_PARTY_DIR}/optional
        ${3RD_PARTY_DIR}/renderdocapi
        ${3RD_PARTY_DIR}/VulkanMemoryAllocator/src
        ${3RD_PARTY_DIR}/shaderc/libshaderc/include
        ${3RD_PARTY_DIR}/profiler/include
        )

# Setup the nova-core library.
set(NOVA_HEADERS

        render/objects/resources/uniform_buffer_definitions.h

        mc_interface/nova.h
        render/nova_renderer.h
        render/objects/resources/texture_manager.h
        utils/types.h

        render/objects/shaders/pipeline.h
        render/objects/resources/uniform_buffer.h
        render/objects/meshes/vk_mesh.h
        render/objects/resources/texture2D.h

        render/windowing/glfw_vk_window.h

        input/InputHandler.h

        mc_interface/mc_gui_objects.h
        mc_interface/mc_objects.h

        utils/utils.h
        data_loading/settings.h
        data_loading/loaders/loaders.h
        data_loading/loaders/shader_loading.h
        data_loading/loaders/loader_utils.h
        render/objects/meshes/mesh_store.h
        render/objects/render_object.h
        render/objects/resources/uniform_buffer_store.h
        render/objects/resources/uniform_buffer_definitions.h
        render/objects/resources/uniform_buffer.h

        physics/aabb.h
        data_loading/loaders/shader_source_structs.h
        render/objects/meshes/mesh_definition.h
        render/objects/camera.h
        utils/io.h
        render/objects/resources/shader_resource_manager.h
        data_loading/direct_buffers.h
        mc_interface/nova_jni.h
        render/objects/render_object.h
        utils/stb_image_write.h
        render/objects/renderpasses/renderpass_builder.h
        render/vulkan/render_context.h
        render/vulkan/command_pool.h
        render/objects/resources/auto_allocated_buffer.h
        render/objects/renderpasses/materials.h

        render/objects/renderpasses/render_passes.h
        render/render_graph.h
        render/objects/meshes/vertex_attributes.h
        render/swapchain_manager.h
        )

set(NOVA_SOURCE
        # Need to compile third party stuff, too...
        3rdparty/miniz/miniz.c
        3rdparty/miniz/miniz_tdef.c
        3rdparty/miniz/miniz_tinfl.c
        3rdparty/miniz/miniz_zip.c
        3rdparty/easyloggingpp/src/easylogging++.cc

        render/nova_renderer.cpp
        mc_interface/nova_facade.cpp
        render/objects/resources/texture_manager.cpp
        render/objects/resources/uniform_buffer_store.cpp

        input/InputHandler.cpp

        render/objects/shaders/pipeline.cpp
        render/objects/meshes/vk_mesh.cpp
        render/objects/resources/texture2D.cpp

        render/windowing/glfw_vk_window.cpp

        utils/utils.cpp

        data_loading/settings.cpp
        data_loading/loaders/shader_loading.cpp
        data_loading/loaders/loader_utils.cpp

        render/objects/meshes/mesh_store.cpp
        physics/aabb.cpp
        render/objects/meshes/mesh_definition.cpp

        mc_interface/mc_objects.cpp
        render/objects/camera.cpp
        data_loading/loaders/shader_source_structs.cpp
        data_loading/direct_buffers.cpp
        render/objects/render_object.cpp
        render/vulkan/command_pool.cpp
        render/objects/renderpasses/renderpass_builder.cpp
        render/objects/renderpasses/materials.cpp

        render/vulkan/render_context.cpp
        render/objects/resources/shader_resource_manager.cpp
        render/objects/resources/auto_allocated_buffer.cpp
        render/objects/renderpasses/render_passes.cpp
        data_loading/loaders/shader_file_loading.cpp
        render/render_graph.cpp
        render/objects/resources/uniform_buffer.cpp
        render/swapchain_manager.cpp)

# SPIRV-Cross doesn't like being an in-source build, meaning I can't easily add it as a dependency to Nova. The
# instructions are like "yeah just copy over whatever files you want cause that's totally the way modern software is
# built -_-
# So here we are,
set(SPIRV_CROSS_ROOT ${3RD_PARTY_DIR}/SPIRV-Cross)
set(SPIRV_CROSS_SOURCE
        ${SPIRV_CROSS_ROOT}/spirv_cfg.cpp
        ${SPIRV_CROSS_ROOT}/spirv_cross_util.cpp
        ${SPIRV_CROSS_ROOT}/spirv_cross.cpp
        ${SPIRV_CROSS_ROOT}/spirv_glsl.cpp
        dummy.cpp)

set(SPIRV_CROSS_HEADERS
        ${SPIRV_CROSS_ROOT}/spirv_cross.hpp
        )

if (WIN32)
    set(NOVA_SOURCE ${NOVA_SOURCE} ${NOVA_HEADERS} 3rdparty/renderdocapi/RenderDocManager.cpp)
endif (WIN32)

if (UNIX)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DELPP_STACKTRACE_ON_CRASH -g -fno-omit-frame-pointer")
    set(CMAKE_LINKER_FLAGS "${CMAKE_LINKER_FLAGS} -fno-omit-frame-pointer")
endif (UNIX)

if(MSVC)
    if(CMAKE_CXX_FLAGS MATCHES "/Wextra")
        string(REGEX REPLACE "/Wextra" "" CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")
    endif()

    # Force to always compile with W4
    if(CMAKE_CXX_FLAGS MATCHES "/W[0-4]")
        string(REGEX REPLACE "/W[0-4]" "/W4" CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")
    else()
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} /W4")
    endif()
elseif(CMAKE_COMPILER_IS_GNUCC OR CMAKE_COMPILER_IS_GNUCXX)
    # Update if necessary
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wno-long-long -pedantic")
endif()

# For now just put everthing in a single nova source group
# because there are several sub groups that would be extremely small
source_group("nova" FILES ${NOVA_HEADERS} ${NOVA_NO_COMPILE} ${NOVA_SOURCE})

add_library(nova-renderer SHARED ${NOVA_SOURCE} ${SPIRV_CROSS_SOURCE})
set_target_properties(nova-renderer PROPERTIES POSITION_INDEPENDENT_CODE TRUE)

target_compile_definitions(nova-renderer PRIVATE DLL_EXPORT ELPP_THREAD_SAFE BUILD_WITH_EASY_PROFILER)

target_compile_definitions(nova-renderer PUBLIC DLL_EXPORT ELPP_THREAD_SAFE BUILD_WITH_EASY_PROFILER)

if (WIN32)
    set_target_properties(nova-renderer PROPERTIES PREFIX "")
endif (WIN32)

# This is needed on MSVC otherwise the build files get spread out all over the place.
# The only *problem* is that we wont be able to create multi-config builds. Personally
# I dont see this as a problem currently. This will make the location more predictable
# cross platform as well. Though i think object files are still cached across build
# types so this should be fine.
get_target_property(OUTPUT_DIR nova-renderer LIBRARY_OUTPUT_DIRECTORY)
nova_set_all_target_outputs(nova-renderer ${OUTPUT_DIR})
if(CMAKE_SYSTEM_NAME MATCHES "Linux")
    set_target_properties(nova-renderer PROPERTIES CXX_VISIBILITY_PRESET hidden)
endif()

set(COMMON_LINK_LIBS ${CMAKE_DL_LIBS} glfw ${GLFW_LIBRARIES} ${Vulkan_LIBRARIES} shaderc stdc++fs nova_profiler)

add_executable(nova-test test/main.cpp ${NOVA_SOURCE})
target_link_libraries(nova-test ${COMMON_LINK_LIBS})
target_compile_definitions(nova-test PUBLIC STATIC_LINKAGE)
set_target_properties(nova-test PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}")

target_link_libraries(nova-renderer ${COMMON_LINK_LIBS})
