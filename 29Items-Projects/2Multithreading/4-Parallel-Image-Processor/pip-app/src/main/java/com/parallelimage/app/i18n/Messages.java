package com.parallelimage.app.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Every string the CLI prints to an operator, resolved for one {@link Locale}.
 *
 * <p>A thin wrapper rather than calling {@link ResourceBundle} directly at each call site: it is the
 * one place that decides bundle name and {@link MessageFormat} substitution, so a call site reads
 * {@code messages.get("cli.usage")} instead of repeating both. {@code pip-ui} has an independent copy
 * of this same shape in its own {@code i18n} package rather than a shared dependency — see
 * {@link com.parallelimage.app.wiring.ServiceRegistry}'s module-graph note: {@code pip-ui} must not
 * depend on {@code pip-app}, so the two bundles cannot share a loader without inverting that direction.
 */
public final class Messages {

    private static final String BUNDLE_NAME = "com.parallelimage.app.i18n.Messages";

    private final ResourceBundle bundle;

    public Messages(Locale locale) {
        this.bundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNull(locale, "locale"));
    }

    /**
     * Looks up {@code key} and substitutes {@code args} via {@link MessageFormat}.
     *
     * <p>Substitution is skipped when there are no arguments. A message that happens to contain a
     * literal opening-brace character — none do today, but a future translation might — must not be
     * run through {@link MessageFormat} unless it actually needs to be, since an unescaped brace
     * would then throw.
     */
    public String get(String key, Object... args) {
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
