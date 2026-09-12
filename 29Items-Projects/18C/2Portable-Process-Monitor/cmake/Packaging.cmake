# Packaging.cmake — portable ZIP artifact via CPack.

set(CPACK_PACKAGE_NAME "ppmon")
set(CPACK_PACKAGE_VENDOR "Portable Process Monitor")
set(CPACK_PACKAGE_VERSION "${PROJECT_VERSION}")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "${PROJECT_DESCRIPTION}")

# ZIP keeps the deliverable install-free and architecture-tagged.
set(CPACK_GENERATOR "ZIP")
set(CPACK_PACKAGE_FILE_NAME
    "ppmon-${PROJECT_VERSION}-${CMAKE_SYSTEM_PROCESSOR}")

set(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/LICENSE")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "ppmon")

include(CPack)
