package com.parallelimage.ui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Every user-facing string in the JavaFX UI, resolved for one {@link Locale}.
 *
 * <p>A thin wrapper rather than calling {@link ResourceBundle} directly at each call site: it is the
 * one place that decides bundle name and {@link MessageFormat} substitution, so a call site reads
 * {@code messages.get("mainView.subtitle")} instead of repeating both. {@code pip-app} has an
 * independent copy of this same shape in its own {@code i18n} package rather than a shared
 * dependency, since {@code pip-ui} must not depend on {@code pip-app} (module-graph direction).
 */
public final class Messages {

    private static final String BUNDLE_NAME = "com.parallelimage.ui.i18n.Messages";

    private final ResourceBundle bundle;

    public Messages(Locale locale) {
        this.bundle = ResourceBundle.getBundle(BUNDLE_NAME, Objects.requireNonNull(locale, "locale"));
    }

    public String get(String key, Object... args) {
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
