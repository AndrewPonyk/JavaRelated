#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <jni.h>
#include "JNIFoo.h"

JNIEXPORT void JNICALL Java_JNIFoo_nativeFoo
  (JNIEnv *env, jobject obj)
{
	printf("Code from C executed in Java");
    FILE *f = fopen("/home/andrii/temp/file.txt", "w");
    const char *text = "Write this to the file";
    fprintf(f, "Some text: %s\n", text);
    fclose(f);
}
