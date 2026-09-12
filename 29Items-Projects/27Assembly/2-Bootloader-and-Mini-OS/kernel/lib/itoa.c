/* =============================================================================
 *  itoa.c  --  Unsigned integer -> ASCII (decimal / hex), no libc
 *
 *  Builds the digits in reverse into a scratch buffer, then reverses them into
 *  the caller's buffer. Supports base 10 and base 16 (lowercase). Returns buf.
 * ===========================================================================*/
#include "string.h"

char *itoa(u32 value, char *buf, int base) {
    static const char digits[] = "0123456789abcdef";
    char tmp[12];
    int i = 0;

    if (base != 10 && base != 16) base = 10;    /* defensive default */

    if (value == 0) {
        buf[0] = '0';
        buf[1] = '\0';
        return buf;
    }

    while (value > 0 && i < (int)sizeof(tmp)) {
        tmp[i++] = digits[value % (u32)base];
        value /= (u32)base;
    }

    int j = 0;
    while (i > 0) buf[j++] = tmp[--i];           /* reverse into output */
    buf[j] = '\0';
    return buf;
}
