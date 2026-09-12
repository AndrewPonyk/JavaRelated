# ============================================================================
#  cmake/FindOracle.cmake
#  Locate the Oracle Instant Client + OCCI headers/libraries. Oracle's client
#  is license-gated and not on Conan Center, so it is vendored / installed out
#  of band and discovered here.
#
#  Set ORACLE_HOME (or env ORACLE_HOME / ORACLE_INSTANT_CLIENT) to the SDK root.
#  Provides imported target Oracle::OCCI and variables ORACLE_{FOUND,INCLUDE_DIRS,LIBRARIES}.
# ============================================================================
find_path(ORACLE_INCLUDE_DIR
    NAMES occi.h
    HINTS ${ORACLE_HOME} $ENV{ORACLE_HOME} $ENV{ORACLE_INSTANT_CLIENT}
    PATH_SUFFIXES sdk/include include)

find_library(ORACLE_OCCI_LIBRARY
    NAMES occi libocci
    HINTS ${ORACLE_HOME} $ENV{ORACLE_HOME} $ENV{ORACLE_INSTANT_CLIENT}
    PATH_SUFFIXES lib sdk/lib)

find_library(ORACLE_CLNTSH_LIBRARY
    NAMES clntsh oci
    HINTS ${ORACLE_HOME} $ENV{ORACLE_HOME} $ENV{ORACLE_INSTANT_CLIENT}
    PATH_SUFFIXES lib sdk/lib)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(Oracle
    REQUIRED_VARS ORACLE_INCLUDE_DIR ORACLE_OCCI_LIBRARY ORACLE_CLNTSH_LIBRARY)

if(Oracle_FOUND)
    set(ORACLE_INCLUDE_DIRS ${ORACLE_INCLUDE_DIR})
    set(ORACLE_LIBRARIES ${ORACLE_OCCI_LIBRARY} ${ORACLE_CLNTSH_LIBRARY})
    if(NOT TARGET Oracle::OCCI)
        add_library(Oracle::OCCI UNKNOWN IMPORTED)
        set_target_properties(Oracle::OCCI PROPERTIES
            IMPORTED_LOCATION "${ORACLE_OCCI_LIBRARY}"
            INTERFACE_INCLUDE_DIRECTORIES "${ORACLE_INCLUDE_DIR}"
            INTERFACE_LINK_LIBRARIES "${ORACLE_CLNTSH_LIBRARY}")
    endif()
endif()

mark_as_advanced(ORACLE_INCLUDE_DIR ORACLE_OCCI_LIBRARY ORACLE_CLNTSH_LIBRARY)
