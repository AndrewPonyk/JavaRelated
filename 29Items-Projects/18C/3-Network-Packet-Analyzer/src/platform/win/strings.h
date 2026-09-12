/* SPDX-License-Identifier: MIT
 *
 * platform/win/strings.h — resolves <strings.h> on Windows; maps the
 * case-insensitive compares to the MSVC CRT equivalents.
 */
#ifndef NPA_WIN_STRINGS_H
#define NPA_WIN_STRINGS_H
#include <string.h>
#define strcasecmp  _stricmp
#define strncasecmp _strnicmp
#endif
