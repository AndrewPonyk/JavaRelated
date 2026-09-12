package com.parallelimage.ui.view;

import com.parallelimage.core.model.JobStatus;
import com.parallelimage.core.port.JobRepository;
import com.parallelimage.core.progress.ProgressEvent;
import com.parallelimage.ui.i18n.Messages;
import com.parallelimage.ui.viewmodel.BatchViewModel;
import com.parallelimage.ui.viewmodel.BatchViewModel.State;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * The batch screen: a form, a progress area, and one of four result panes.
 *
 * <h2>Programmatic, not FXML</h2>
 * FXML buys a designer-editable layout and a live preview. It costs a second language, a reflective
 * controller-injection step whose failures appear at runtime as a null field, and a build that must
 * copy resources correctly before the UI can be smoke-tested. For a screen this size — one form and one
 * results pane — the trade is not worth it. Java gives compile-time checking of every binding, and a
 * typo in a property name is an error rather than a silently missing control.
 *
 * <h2>State is switched, not toggled</h2>
 * Each of {@link State#LOADING}, {@link State#LOADED}, {@link State#EMPTY} and {@link State#ERROR} owns
 * a node in one {@link StackPane}, and exactly one is visible. The alternative — setting
 * {@code visible}/{@code managed} on several nodes from several listeners — is how a UI ends up showing
 * a spinner on top of an error message, because the two updates came from different events and one of
 * them was missed.
 *
 * <p>Note {@code managed} is set alongside {@code visible}. An invisible but managed node still
 * occupies its layout slot, which shows up as a results pane that is inexplicably pushed halfway down
 * the window.
 *
 * <h2>What this class does not do</h2>
 * No processing, no threading, no validation. Every button handler is one call into
 * {@link BatchViewModel}; every label is a binding out of it. If something here needs a {@code Thread},
 * it belongs in the view model.
 */
public final class BatchProcessorView extends VBox {

    private static final int GAP = 10;

    /** Formats ImageIO can reliably write. Deliberately not queried from ImageIO at startup: the
     * installed writer set can include exotic entries whose quality parameter we do not support. */
    private static final String[] OUTPUT_FORMATS = {"png", "jpg", "webp", "bmp", "gif"};

    private final BatchViewModel viewModel;
    private final Messages messages;

    public BatchProcessorView(BatchViewModel viewModel, Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");

        setSpacing(GAP);
        setPadding(new Insets(GAP));
        getStyleClass().add("batch-view");

        StackPane results = buildResultPanes();
        VBox.setVgrow(results, Priority.ALWAYS);
        getChildren().addAll(buildForm(), buildProgressArea(), results);
    }

    // ------------------------------------------------------------------------
    //  Form
    // ------------------------------------------------------------------------

    private GridPane buildForm() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("form");
        grid.setHgap(GAP);
        grid.setVgap(GAP / 2.0);

        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(96);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);   // the path fields, not the buttons, absorb extra width
        ColumnConstraints buttons = new ColumnConstraints();
        grid.getColumnConstraints().addAll(labels, fields, buttons);

        grid.add(new Label(messages.get("batchView.form.input")), 0, 0);
        TextField inputField = pathField(viewModel.inputDirectoryProperty(),
                messages.get("batchView.form.input.prompt"));
        inputField.setAccessibleRole(javafx.scene.AccessibleRole.TEXT_FIELD);
        inputField.setAccessibleText(messages.get("batchView.form.input.accessibleText"));
        enableDirectoryDrop(inputField, viewModel.inputDirectoryProperty());
        grid.add(inputField, 1, 0);
        grid.add(browseButton(messages.get("batchView.form.browse"), viewModel.inputDirectoryProperty(),
                messages.get("batchView.form.input.browseTitle"),
                messages.get("batchView.form.input.browseAccessibleText")), 2, 0);

        grid.add(new Label(messages.get("batchView.form.output")), 0, 1);
        TextField outputField = pathField(viewModel.outputDirectoryProperty(),
                messages.get("batchView.form.output.prompt"));
        outputField.setAccessibleRole(javafx.scene.AccessibleRole.TEXT_FIELD);
        outputField.setAccessibleText(messages.get("batchView.form.output.accessibleText"));
        grid.add(outputField, 1, 1);
        grid.add(browseButton(messages.get("batchView.form.browse"), viewModel.outputDirectoryProperty(),
                messages.get("batchView.form.output.browseTitle"),
                messages.get("batchView.form.output.browseAccessibleText")), 2, 1);

        grid.add(new Label(messages.get("batchView.form.pipeline")), 0, 2);
        TextField pipeline = new TextField();
        pipeline.setPromptText(messages.get("batchView.form.pipeline.prompt"));
        pipeline.textProperty().bindBidirectional(viewModel.pipelineTextProperty());
        // The prompt text carries the grammar because there is nowhere else a user would look for it,
        // and an empty field is a legal value that means "re-encode without touching pixels".
        pipeline.setTooltip(new Tooltip(messages.get("batchView.form.pipeline.tooltip")));
        grid.add(pipeline, 1, 2, 2, 1);

        grid.add(new Label(""), 0, 3);
        grid.add(new PipelineEditor(viewModel.pipelineTextProperty(), messages), 1, 3, 2, 1);

        grid.add(new Label(messages.get("batchView.form.format")), 0, 4);
        grid.add(buildOptionsRow(), 1, 4, 2, 1);

        // Inputs are disabled while a batch runs. Not merely cosmetic: changing the pipeline mid-batch
        // would have no effect on the running jobs, so leaving it editable would be a lie.
        grid.disableProperty().bind(viewModel.stateProperty().isEqualTo(State.LOADING));
        return grid;
    }

    private HBox buildOptionsRow() {
        ChoiceBox<String> format = new ChoiceBox<>();
        format.getItems().addAll(OUTPUT_FORMATS);
        format.valueProperty().bindBidirectional(viewModel.outputFormatProperty());

        CheckBox recursive = new CheckBox(messages.get("batchView.form.recursive"));
        recursive.selectedProperty().bindBidirectional(viewModel.recursiveProperty());

        CheckBox overwrite = new CheckBox(messages.get("batchView.form.overwrite"));
        overwrite.selectedProperty().bindBidirectional(viewModel.overwriteExistingProperty());
        overwrite.setTooltip(new Tooltip(messages.get("batchView.form.overwrite.tooltip")));

        HBox row = new HBox(GAP, format, recursive, overwrite);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private TextField pathField(javafx.beans.property.ObjectProperty<Path> property, String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        // One-way binding plus a commit handler, rather than a bidirectional StringConverter binding.
        // Path.of() throws InvalidPathException on illegal characters, and a converter that throws
        // during a keystroke leaves the control in a broken state; committing on Enter/focus-loss lets
        // the bad value simply not be accepted.
        property.addListener((observable, old, value) -> field.setText(value == null ? "" : value.toString()));
        field.setOnAction(event -> commitPath(field, property));
        field.focusedProperty().addListener((observable, was, isFocused) -> {
            if (!isFocused) {
                commitPath(field, property);
            }
        });
        return field;
    }

    private void commitPath(TextField field, javafx.beans.property.ObjectProperty<Path> property) {
        String text = field.getText();
        if (text == null || text.isBlank()) {
            property.set(null);
            return;
        }
        try {
            property.set(Path.of(text.trim()));
        } catch (RuntimeException e) {
            // Reject silently and restore: the view model reports "choose a folder first" when the
            // user presses Start, which is a better moment for the message than mid-typing.
            Path current = property.get();
            field.setText(current == null ? "" : current.toString());
        }
    }

    private Button browseButton(String text, javafx.beans.property.ObjectProperty<Path> property, String title,
            String accessibleText) {
        Button button = new Button(text);
        button.setAccessibleText(accessibleText);
        button.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(title);
            Path current = property.get();
            // setInitialDirectory throws if the directory does not exist -- which is exactly the state a
            // half-typed path leaves it in, so it is checked rather than trusted.
            if (current != null && Files.isDirectory(current)) {
                chooser.setInitialDirectory(current.toFile());
            }
            Window window = getScene() == null ? null : getScene().getWindow();
            java.io.File chosen = chooser.showDialog(window);
            if (chosen != null) {
                property.set(chosen.toPath());
            }
        });
        return button;
    }

    /**
     * Lets a folder (or a file, whose parent folder is used instead) be dropped onto {@code node} to
     * set {@code property} — the same property the Browse button next to it already writes to.
     *
     * <p>Only wired onto the input row: dropping a folder to fill in a source location is the case
     * users actually reach for. The output row keeps Browse-only, since there is rarely an existing
     * folder to drag from for a destination that a batch has not written to yet.
     */
    private void enableDirectoryDrop(javafx.scene.Node node, javafx.beans.property.ObjectProperty<Path> property) {
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        node.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            java.util.List<java.io.File> files = dragboard.getFiles();
            java.io.File chosen = files.stream()
                    .filter(java.io.File::isDirectory)
                    .findFirst()
                    // No directory in the drop: fall back to the first file's parent, so dropping a
                    // single photo still lands on the folder it lives in rather than being rejected.
                    .orElseGet(() -> files.isEmpty() ? null : files.get(0).getParentFile());
            boolean accepted = chosen != null;
            if (accepted) {
                property.set(chosen.toPath());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    // ------------------------------------------------------------------------
    //  Progress
    // ------------------------------------------------------------------------

    private HBox buildProgressArea() {
        ProgressBar bar = new ProgressBar(0.0d);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.progressProperty().bind(viewModel.progressProperty());
        bar.setAccessibleRole(javafx.scene.AccessibleRole.PROGRESS_INDICATOR);
        bar.setAccessibleText(messages.get("batchView.progress.accessibleText"));
        HBox.setHgrow(bar, Priority.ALWAYS);

        Button start = new Button(messages.get("batchView.button.start"));
        start.setMnemonicParsing(true);
        start.getStyleClass().add("primary");
        start.setDefaultButton(true);
        start.setAccessibleText(messages.get("batchView.button.start.accessibleText"));
        start.setOnAction(event -> viewModel.start());
        start.disableProperty().bind(viewModel.stateProperty().isEqualTo(State.LOADING));

        Button cancel = new Button(messages.get("batchView.button.cancel"));
        cancel.setMnemonicParsing(true);
        // The default button already responds to Enter; Escape is the natural counterpart for the
        // button that stops it, and setCancelButton wires that up without a manual key handler.
        cancel.setCancelButton(true);
        cancel.setAccessibleText(messages.get("batchView.button.cancel.accessibleText"));
        cancel.setOnAction(event -> viewModel.cancel());
        // Bound to cancellable, not to state: after Cancel is pressed the batch is still LOADING but
        // there is nothing left to cancel, and a button that does nothing when clicked twice invites a
        // bug report.
        cancel.disableProperty().bind(viewModel.cancellableProperty().not());

        HBox row = new HBox(GAP, bar, start, cancel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("progress-row");
        return row;
    }

    // ------------------------------------------------------------------------
    //  Result panes
    // ------------------------------------------------------------------------

    private StackPane buildResultPanes() {
        VBox loaded = buildLoadedPane();
        VBox empty = buildMessagePane("empty-pane", messages.get("batchView.pane.empty.title"),
                messages.get("batchView.pane.empty.body"));
        VBox error = buildErrorPane();
        VBox loading = buildLoadingPane();
        VBox idle = buildMessagePane("idle-pane", messages.get("batchView.pane.idle.title"),
                messages.get("batchView.pane.idle.body"));

        StackPane stack = new StackPane(idle, loading, loaded, empty, error);
        stack.getStyleClass().add("results");

        bindVisibility(idle, State.IDLE);
        bindVisibility(loading, State.LOADING);
        bindVisibility(loaded, State.LOADED);
        bindVisibility(empty, State.EMPTY);
        bindVisibility(error, State.ERROR);
        return stack;
    }

    /** Ties one pane to one state. {@code managed} follows {@code visible} — see the class javadoc. */
    private void bindVisibility(javafx.scene.Node node, State when) {
        node.visibleProperty().bind(viewModel.stateProperty().isEqualTo(when));
        node.managedProperty().bind(node.visibleProperty());
    }

    private VBox buildLoadingPane() {
        Label heading = new Label(messages.get("batchView.pane.loading.heading"));
        heading.getStyleClass().add("heading");
        Label detail = new Label();
        detail.textProperty().bind(viewModel.statusTextProperty());
        detail.setWrapText(true);

        // Failures appear during the run, not only at the end. A user watching a long batch fail on
        // every file should not have to wait for it to finish to find out why.
        ListView<String> live = new ListView<>(viewModel.failures());
        live.setPlaceholder(new Label(messages.get("batchView.pane.loading.failuresPlaceholder")));
        live.setAccessibleText(messages.get("batchView.pane.loading.failuresAccessibleText"));
        VBox.setVgrow(live, Priority.ALWAYS);

        VBox pane = new VBox(GAP / 2.0, heading, detail,
                new Label(messages.get("batchView.pane.loading.failuresLabel")), live);
        pane.getStyleClass().addAll("pane", "loading-pane");
        return pane;
    }

    private VBox buildLoadedPane() {
        Label heading = new Label();
        heading.getStyleClass().add("heading");
        heading.textProperty().bind(viewModel.statusTextProperty());
        heading.setWrapText(true);

        ListView<String> failures = new ListView<>(viewModel.failures());
        failures.setPlaceholder(new Label(messages.get("batchView.pane.loaded.failuresPlaceholder")));
        failures.setAccessibleText(messages.get("batchView.pane.loaded.failuresAccessibleText"));
        failures.getStyleClass().add("failure-list");
        // Only shown when there is something in it: an empty "Failures" box under a clean run reads as
        // though something went wrong.
        failures.visibleProperty().bind(Bindings.isNotEmpty(viewModel.failures()));
        failures.managedProperty().bind(failures.visibleProperty());
        failures.setPrefHeight(120);

        Label failureHeading = new Label(messages.get("batchView.pane.loaded.failuresLabel"));
        failureHeading.visibleProperty().bind(failures.visibleProperty());
        failureHeading.managedProperty().bind(failures.visibleProperty());

        TableView<JobRepository.JobRecord> history = buildHistoryTable();
        VBox.setVgrow(history, Priority.ALWAYS);

        PreviewPane preview = new PreviewPane(messages);
        // The list is replaced wholesale by refreshHistory(), never mutated in place, so any change at
        // all -- add, remove, whole-list swap -- means "recompute which job to preview".
        viewModel.history().addListener(
                (javafx.collections.ListChangeListener<JobRepository.JobRecord>) change -> updatePreview(preview));
        updatePreview(preview);

        VBox pane = new VBox(GAP / 2.0, heading, failureHeading, failures,
                new Label(messages.get("batchView.pane.loaded.recentJobs")), history,
                new Label(messages.get("batchView.pane.loaded.preview")), preview);
        pane.getStyleClass().addAll("pane", "loaded-pane");
        return pane;
    }

    /**
     * Shows the first successfully completed job in the current history as a before/after pair.
     *
     * <p>First, not most-recent-of-some-other-order: {@link BatchViewModel#history()} is already
     * newest-first (mirroring {@link JobRepository#recentJobs}), so this is "the most recent success",
     * which is also the job whose output a user watching the table just saw appear.
     */
    private void updatePreview(PreviewPane preview) {
        for (JobRepository.JobRecord record : viewModel.history()) {
            if (record.status() == JobStatus.COMPLETED) {
                preview.show(Path.of(record.sourcePath()), Path.of(record.targetPath()));
                return;
            }
        }
        preview.clear();
    }

    private TableView<JobRepository.JobRecord> buildHistoryTable() {
        TableView<JobRepository.JobRecord> table = new TableView<>(viewModel.history());
        table.setPlaceholder(new Label(messages.get("batchView.history.placeholder")));
        table.setAccessibleText(messages.get("batchView.history.accessibleText"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getColumns().add(column(messages.get("batchView.history.column.file"), 220,
                record -> fileName(record.sourcePath())));
        table.getColumns().add(column(messages.get("batchView.history.column.status"), 90,
                record -> record.status().name()));
        table.getColumns().add(column(messages.get("batchView.history.column.ms"), 70,
                record -> String.valueOf(record.durationMillis())));
        table.getColumns().add(column(messages.get("batchView.history.column.pixels"), 100,
                record -> megapixels(record.pixelsProcessed())));
        table.getColumns().add(column(messages.get("batchView.history.column.reason"), 260,
                record -> record.failureReason() == null ? "" : record.failureReason()));

        // A row style class rather than a colour set in code: the palette belongs in app.css, and a
        // failed row must stay legible if the user runs a dark system theme.
        table.setRowFactory(view -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(JobRepository.JobRecord item, boolean empty) {
                super.updateItem(item, empty);
                // Always remove before conditionally adding: TableRow instances are recycled as the
                // user scrolls, so a class left behind from a previous item stains an unrelated row.
                getStyleClass().remove("failed-row");
                if (!empty && item != null && item.failed()) {
                    getStyleClass().add("failed-row");
                }
            }
        });
        return table;
    }

    private static TableColumn<JobRepository.JobRecord, String> column(
            String title, double width, java.util.function.Function<JobRepository.JobRecord, String> value) {
        TableColumn<JobRepository.JobRecord, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        // A plain read-only wrapper, not a PropertyValueFactory: the latter looks up getters
        // reflectively by name and fails at runtime with an empty column when a name changes, which is
        // exactly the class of error a record's accessors let the compiler catch instead.
        column.setCellValueFactory(features ->
                new javafx.beans.property.ReadOnlyStringWrapper(value.apply(features.getValue())));
        return column;
    }

    private VBox buildErrorPane() {
        Label heading = new Label(messages.get("batchView.pane.error.heading"));
        heading.getStyleClass().addAll("heading", "error");

        Label detail = new Label();
        detail.textProperty().bind(viewModel.errorTextProperty());
        detail.setWrapText(true);
        detail.getStyleClass().add("error-detail");

        Button retry = new Button(messages.get("batchView.pane.error.retry"));
        retry.setOnAction(event -> viewModel.start());

        VBox pane = new VBox(GAP / 2.0, heading, detail, retry);
        pane.getStyleClass().addAll("pane", "error-pane");
        pane.setAlignment(Pos.TOP_LEFT);
        return pane;
    }

    private VBox buildMessagePane(String styleClass, String title, String body) {
        Label heading = new Label(title);
        heading.getStyleClass().add("heading");
        Label detail = new Label(body);
        detail.setWrapText(true);
        VBox pane = new VBox(GAP / 2.0, heading, detail);
        pane.getStyleClass().addAll("pane", styleClass);
        return pane;
    }

    // ------------------------------------------------------------------------
    //  Formatting
    // ------------------------------------------------------------------------

    /**
     * Last path element only.
     *
     * <p>Not decoration: full paths in a UI leak directory structure into screenshots and bug reports,
     * which ARCHITECTURE §2.6 rules out. {@link com.parallelimage.core.model.ImageJob#displayName()}
     * makes the same choice for the same reason, but a {@link JobRepository.JobRecord} carries a
     * {@code String}, so the trim happens here.
     */
    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String megapixels(long pixels) {
        return pixels <= 0L ? "" : "%.1f MP".formatted(pixels / 1_000_000.0d);
    }

    /** Visible for tests and for {@link MainView}: lets a caller trigger the first history load. */
    public void loadHistory() {
        viewModel.refreshHistory();
    }

    /**
     * Documented dependency, kept honest by a reference: the panes above are driven entirely by
     * {@link ProgressEvent}s that {@link com.parallelimage.ui.task.ProgressBridge} has already
     * coalesced. Nothing in this class subscribes to the engine directly.
     */
    BatchViewModel viewModel() {
        return viewModel;
    }
}
