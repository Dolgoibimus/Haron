cmake_minimum_required(VERSION 3.22.1)
project("haron_native")

# --- PTY library (terminal) ---
add_library(haron_pty SHARED pty.c)
target_link_libraries(haron_pty log)

# --- lwext4 library ---
set(LWEXT4_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lwext4)

add_library(lwext4 STATIC
    ${LWEXT4_DIR}/src/ext4.c
    ${LWEXT4_DIR}/src/ext4_balloc.c
    ${LWEXT4_DIR}/src/ext4_bcache.c
    ${LWEXT4_DIR}/src/ext4_bitmap.c
    ${LWEXT4_DIR}/src/ext4_block_group.c
    ${LWEXT4_DIR}/src/ext4_blockdev.c
    ${LWEXT4_DIR}/src/ext4_crc32.c
    ${LWEXT4_DIR}/src/ext4_debug.c
    ${LWEXT4_DIR}/src/ext4_dir.c
    ${LWEXT4_DIR}/src/ext4_dir_idx.c
    ${LWEXT4_DIR}/src/ext4_extent.c
    ${LWEXT4_DIR}/src/ext4_fs.c
    ${LWEXT4_DIR}/src/ext4_hash.c
    ${LWEXT4_DIR}/src/ext4_ialloc.c
    ${LWEXT4_DIR}/src/ext4_inode.c
    ${LWEXT4_DIR}/src/ext4_journal.c
    ${LWEXT4_DIR}/src/ext4_mbr.c
    ${LWEXT4_DIR}/src/ext4_mkfs.c
    ${LWEXT4_DIR}/src/ext4_super.c
    ${LWEXT4_DIR}/src/ext4_trans.c
    ${LWEXT4_DIR}/src/ext4_xattr.c
)

target_include_directories(lwext4 PUBLIC
    ${LWEXT4_DIR}/include
)

target_compile_definitions(lwext4 PRIVATE
    CONFIG_USE_DEFAULT_CFG=1
    CONFIG_HAVE_OWN_ERRNO=0
    CONFIG_HAVE_OWN_ASSERT=0
    CONFIG_DEBUG_PRINTF=0
    CONFIG_DEBUG_ASSERT=0
    CONFIG_JOURNALING_ENABLE=1
    CONFIG_XATTR_ENABLE=1
    CONFIG_EXTENTS_ENABLE=1
    CONFIG_BLOCK_DEV_CACHE_SIZE=16
)

# --- JNI bridge for lwext4 ---
add_library(haron_ext4 SHARED ext4_jni_bridge.c)
target_link_libraries(haron_ext4 lwext4 log)
target_include_directories(haron_ext4 PRIVATE ${LWEXT4_DIR}/include)
target_compile_definitions(haron_ext4 PRIVATE
    CONFIG_USE_DEFAULT_CFG=1
    CONFIG_HAVE_OWN_ERRNO=0
    CONFIG_HAVE_OWN_ASSERT=0
    CONFIG_DEBUG_PRINTF=0
    CONFIG_DEBUG_ASSERT=0
    CONFIG_JOURNALING_ENABLE=1
    CONFIG_XATTR_ENABLE=1
    CONFIG_EXTENTS_ENABLE=1
    CONFIG_BLOCK_DEV_CACHE_SIZE=16
)
