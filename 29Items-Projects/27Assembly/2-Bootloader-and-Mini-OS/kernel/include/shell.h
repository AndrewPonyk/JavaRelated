/* =============================================================================
 *  shell.h  --  Tiny interactive command shell
 *
 *  Runs as a normal scheduled task: drains the keyboard ring buffer, echoes
 *  input, and on Enter parses and executes a command. Demonstrates the full
 *  IRQ -> driver -> buffer -> task -> screen path end to end.
 * ===========================================================================*/
#ifndef MINIOS_SHELL_H
#define MINIOS_SHELL_H

void shell_run(void);       /* task entry point; never returns */

#endif /* MINIOS_SHELL_H */
