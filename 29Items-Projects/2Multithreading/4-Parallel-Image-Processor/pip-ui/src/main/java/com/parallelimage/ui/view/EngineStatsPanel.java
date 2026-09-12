package com.parallelimage.ui.view;

import com.parallelimage.core.engine.EngineStats;
import com.parallelimage.ui.i18n.Messages;
import javafx.beans.value.ObservableValue;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * The status bar's engine counters as individually labeled fields, replacing
 * {@link EngineStats#summary()}'s single concatenated string.
 *
 * <p>A screen reader announces a {@code Label} bound to one long string as one unbroken run of
 * text; there is no way for the user to ask "just the heap number" out of it. Six small labeled
 * fields, each with its own {@link javafx.scene.Node#setAccessibleText(String)}, let assistive
 * tech (and a sighted user scanning quickly) land on one number at a time.
 *
 * <p>Only fields {@link EngineStats} actually samples are shown here — no field is invented to
 * match a nicer-sounding label.
 */
public final class EngineStatsPanel extends GridPane {

    private static final double COLUMN_GAP = 16.0d;

    private final Label workersValue = new Label();
    private final Label queuedValue = new Label();
    private final Label stealsValue = new Label();
    private final Label heapValue = new Label();
    private final Label gcValue = new Label();
    private final Label collectorValue = new Label();

    private final Messages messages;

    public EngineStatsPanel(ObservableValue<EngineStats> stats, Messages messages) {
        this.messages = messages;
        getStyleClass().add("engine-stats-panel");
        setHgap(COLUMN_GAP);

        addField(0, messages.get("engineStats.caption.workers"), workersValue,
                messages.get("engineStats.help.workers"));
        addField(1, messages.get("engineStats.caption.queued"), queuedValue,
                messages.get("engineStats.help.queued"));
        addField(2, messages.get("engineStats.caption.steals"), stealsValue,
                messages.get("engineStats.help.steals"));
        addField(3, messages.get("engineStats.caption.heap"), heapValue,
                messages.get("engineStats.help.heap"));
        addField(4, messages.get("engineStats.caption.gcTime"), gcValue,
                messages.get("engineStats.help.gcTime"));
        addField(5, messages.get("engineStats.caption.collector"), collectorValue,
                messages.get("engineStats.help.collector"));

        stats.addListener((obs, previous, value) -> update(value));
        update(stats.getValue());
    }

    private void addField(int column, String caption, Label value, String accessibleHelp) {
        Label label = new Label(caption);
        label.getStyleClass().add("engine-stats-caption");

        value.getStyleClass().add("engine-stats-value");
        value.setAccessibleRole(AccessibleRole.TEXT);
        value.setAccessibleText(caption);
        value.setAccessibleHelp(accessibleHelp);

        VBox field = new VBox(2.0d, label, value);
        add(field, column, 0);
    }

    private void update(EngineStats stats) {
        EngineStats sample = stats == null ? EngineStats.UNAVAILABLE : stats;
        workersValue.setText(sample.activeThreads() + "/" + sample.parallelism());
        queuedValue.setText(String.valueOf(sample.queuedTasks()));
        stealsValue.setText(String.valueOf(sample.stealCount()));
        heapValue.setText("%.0f%%".formatted(sample.heapUtilisation() * 100.0d));
        gcValue.setText(messages.get("engineStats.value.gcMillis", String.valueOf(sample.gcTimeMillis())));
        collectorValue.setText(sample.shenandoahActive()
                ? messages.get("engineStats.value.shenandoah")
                : messages.get("engineStats.value.defaultGc"));
    }
}
