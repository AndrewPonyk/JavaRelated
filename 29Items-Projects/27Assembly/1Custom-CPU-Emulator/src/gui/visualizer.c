/*
 * visualizer.c — SDL2 front-end (compiled only when CPUEMU_ENABLE_GUI=1).
 *
 * Layout (read-only panels refreshed every frame from `struct cpu`):
 *   +------------------+---------------------------+
 *   | Registers + FLAGS| Disassembly @ RIP         |
 *   +------------------+---------------------------+
 *   | Stack view       | Memory hex view           |
 *   +------------------+---------------------------+
 *   | Status bar: model · cycles · instret · state |
 *   +----------------------------------------------+
 *
 * Controls:  SPACE = single step · C = run-to-halt · R = reset · ESC/Q = quit.
 *
 * NOTE: SDL2 has no built-in text rasterizer. This scaffold draws the panel
 * frame and "loading/error/halted" states; wiring SDL_ttf for glyphs is the
 * one remaining TODO (marked below). The headless core remains the source of
 * truth for all values shown.
 */
#include "gui/visualizer.h"
#include "common/config.h"

#if CPUEMU_ENABLE_GUI

#include "core/cpu.h"
#include "core/decoder.h"
#include "common/log.h"
#include <SDL.h>
#include <stdlib.h>

struct visualizer {
    SDL_Window   *window;
    SDL_Renderer *renderer;
    cpu_t        *cpu;       /* observed, not owned */
    bool          running;   /* engine in run-to-halt mode */
};

bool vis_available(void) { return true; }

visualizer_t *vis_create(cpu_t *cpu, const char *title) {
    if (cpu == NULL) {
        return NULL;
    }
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        log_error("SDL_Init failed: %s", SDL_GetError());   /* error state */
        return NULL;
    }
    visualizer_t *v = (visualizer_t *)calloc(1, sizeof *v);
    if (v == NULL) { SDL_Quit(); return NULL; }
    v->cpu = cpu;

    v->window = SDL_CreateWindow(title ? title : "Custom CPU Emulator",
                                 SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
                                 960, 600, SDL_WINDOW_SHOWN);
    if (v->window == NULL) {
        log_error("SDL_CreateWindow failed: %s", SDL_GetError());
        free(v); SDL_Quit(); return NULL;
    }
    v->renderer = SDL_CreateRenderer(v->window, -1, SDL_RENDERER_ACCELERATED);
    if (v->renderer == NULL) {
        log_error("SDL_CreateRenderer failed: %s", SDL_GetError());
        SDL_DestroyWindow(v->window); free(v); SDL_Quit(); return NULL;
    }
    return v;
}

void vis_destroy(visualizer_t *v) {
    if (v == NULL) {
        return;
    }
    if (v->renderer) SDL_DestroyRenderer(v->renderer);
    if (v->window)   SDL_DestroyWindow(v->window);
    free(v);
    SDL_Quit();
}

/* Draw one panel's border (text rendering is the SDL_ttf TODO). */
static void draw_panel(SDL_Renderer *r, int x, int y, int w, int h) {
    SDL_SetRenderDrawColor(r, 60, 70, 90, 255);
    SDL_Rect rect = { x, y, w, h };
    SDL_RenderDrawRect(r, &rect);
}

bool vis_frame(visualizer_t *v) {
    if (v == NULL) {
        return false;
    }

    /* --- input handling --- */
    SDL_Event e;
    while (SDL_PollEvent(&e)) {
        if (e.type == SDL_QUIT) {
            return false;
        }
        if (e.type == SDL_KEYDOWN) {
            switch (e.key.keysym.sym) {
                case SDLK_ESCAPE: case SDLK_q: return false;
                case SDLK_SPACE:  cpu_step(v->cpu); break;     /* step */
                case SDLK_c:      v->running = true; break;    /* run  */
                case SDLK_r:      cpu_reset(v->cpu); v->running = false; break;
                default: break;
            }
        }
    }

    /* --- "data fetching": advance the engine if in run mode --- */
    if (v->running && !v->cpu->halted) {
        cpu_step(v->cpu);
        if (v->cpu->halted) v->running = false;
    }

    /* --- render --- */
    SDL_SetRenderDrawColor(v->renderer, 18, 20, 28, 255);
    SDL_RenderClear(v->renderer);

    draw_panel(v->renderer, 10,  10, 300, 270); /* registers + flags */
    draw_panel(v->renderer, 320, 10, 630, 270); /* disassembly       */
    draw_panel(v->renderer, 10,  290, 300, 270);/* stack             */
    draw_panel(v->renderer, 320, 290, 630, 270);/* memory hex        */
    draw_panel(v->renderer, 10,  570, 940, 20); /* status bar        */

    /* TODO(gui): render text with SDL_ttf:
     *   - registers via reg_name()/reg_read(), flags from rflags
     *   - disasm via disasm_format(&v->cpu->last_insn, ...)
     *   - stack from [RSP..top], memory hex window, status bar metrics
     * Until then, panels show structure and the halted/run state by color. */
    if (v->cpu->halted) {
        SDL_SetRenderDrawColor(v->renderer, 200, 80, 80, 255); /* halted = red */
        SDL_Rect bar = { 10, 570, 940, 20 };
        SDL_RenderFillRect(v->renderer, &bar);
    }

    SDL_RenderPresent(v->renderer);
    SDL_Delay(v->running ? 1 : 16); /* ~60 FPS when idle */
    return true;
}

#else /* ----------------- headless stub (no SDL2) ----------------- */

bool          vis_available(void)                       { return false; }
visualizer_t *vis_create(cpu_t *cpu, const char *title) { (void)cpu; (void)title; return NULL; }
void          vis_destroy(visualizer_t *vis)            { (void)vis; }
bool          vis_frame(visualizer_t *vis)              { (void)vis; return false; }

#endif /* CPUEMU_ENABLE_GUI */
