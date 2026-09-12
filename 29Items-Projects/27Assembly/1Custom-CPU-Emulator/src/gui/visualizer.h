/*
 * visualizer.h — SDL2 real-time view of the machine (optional build layer).
 *
 * The GUI is a READ-ONLY observer of the CPU: it pulls a fresh view of state
 * each frame and drives state changes only through cpu_step/cpu_reset (see
 * docs/ARCHITECTURE.md §2.2). When the project is built headless
 * (CPUEMU_ENABLE_GUI=0) these become safe no-ops so the rest of the program is
 * unchanged.
 */
#ifndef CPUEMU_GUI_VISUALIZER_H
#define CPUEMU_GUI_VISUALIZER_H

#include "common/types.h"

typedef struct visualizer visualizer_t;

/* True if SDL2 visualization was compiled in. */
bool vis_available(void);

/* Create a window bound to `cpu`. Returns NULL on failure or when headless. */
visualizer_t *vis_create(cpu_t *cpu, const char *title);

/* Free the window and SDL resources. */
void vis_destroy(visualizer_t *vis);

/* Handle input and render one frame. Returns false when the user asks to quit. */
bool vis_frame(visualizer_t *vis);

#endif /* CPUEMU_GUI_VISUALIZER_H */
