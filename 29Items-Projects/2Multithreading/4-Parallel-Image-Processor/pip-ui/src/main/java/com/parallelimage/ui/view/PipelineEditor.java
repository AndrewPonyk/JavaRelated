package com.parallelimage.ui.view;

import com.parallelimage.core.pipeline.ImageOperation;
import com.parallelimage.core.pipeline.PipelineFormat;
import com.parallelimage.ui.i18n.Messages;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A structured, reorderable view of the same pipeline DSL string the free-text field edits.
 *
 * <h2>One source of truth</h2>
 * {@link PipelineFormat#parse}/{@link PipelineFormat#render} are the only translation between text and
 * {@link ImageOperation}s; this class invents no second model. {@code operations} exists purely so the
 * {@link ListView} has something to reorder — every mutation of it is immediately re-rendered back into
 * {@code pipelineText}, and every external change to {@code pipelineText} (including someone typing in
 * the advanced text field) is re-parsed back into {@code operations}. A {@code syncingFromText} guard
 * breaks the obvious feedback loop between the two directions.
 *
 * <h2>No watermark in the "add" list</h2>
 * {@link PipelineFormat} documents, by design, that a {@code watermark} stage cannot round-trip through
 * this text format — its parameters may contain the format's own {@code >}/{@code :} delimiters, so
 * {@link PipelineFormat#parse} rejects it outright. Offering "watermark" here would build an operation
 * this editor could add but never redisplay after the next parse, which is worse than not offering it.
 */
public final class PipelineEditor extends VBox {

    private static final double GAP = 8.0d;
    private static final List<String> ADDABLE = List.of("grayscale", "resize", "blur", "sharpen", "enhance");

    private final ObservableList<ImageOperation> operations = FXCollections.observableArrayList();
    private final javafx.collections.ListChangeListener<ImageOperation> operationsListener;
    private final Messages messages;

    public PipelineEditor(StringProperty pipelineText, Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        setSpacing(GAP / 2.0d);
        getStyleClass().add("pipeline-editor");

        ListView<ImageOperation> list = buildList();
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPrefHeight(140);
        getChildren().addAll(list, buildAddRow(list));

        // Any structural change -- add, remove, reorder -- is re-rendered immediately, so the advanced
        // text field next to this editor never goes stale while the user is dragging rows around.
        // Detached before a text-driven setAll() below, rather than guarded with a boolean flag, so
        // there is exactly one listener instance and one obvious place its lifecycle is managed.
        operationsListener = change -> pipelineText.set(PipelineFormat.render(operations));
        operations.addListener(operationsListener);
        pipelineText.addListener((observable, old, value) -> syncFromText(value));
        syncFromText(pipelineText.get());
    }

    private void syncFromText(String text) {
        List<ImageOperation> parsed;
        try {
            parsed = PipelineFormat.parse(text);
        } catch (IllegalArgumentException e) {
            // A hand-typed edit can be transiently invalid mid-keystroke, or may name a stage this
            // format cannot parse (watermark). Either way, leave the structured view at its last good
            // state rather than clearing rows the user has not asked to remove.
            return;
        }
        if (parsed.equals(operations)) {
            return;
        }
        operations.removeListener(operationsListener);
        operations.setAll(parsed);
        operations.addListener(operationsListener);
    }

    // ------------------------------------------------------------------------
    //  List: reorder by drag, remove per row
    // ------------------------------------------------------------------------

    private ListView<ImageOperation> buildList() {
        ListView<ImageOperation> list = new ListView<>(operations);
        list.setPlaceholder(new Label(messages.get("pipelineEditor.list.placeholder")));
        list.setCellFactory(view -> new OperationCell());
        return list;
    }

    private final class OperationCell extends ListCell<ImageOperation> {

        private final Label text = new Label();
        private final Button remove = new Button(messages.get("pipelineEditor.button.remove"));
        private final HBox root = new HBox(GAP, text, remove);

        OperationCell() {
            HBox.setHgrow(text, Priority.ALWAYS);
            root.setAlignment(Pos.CENTER_LEFT);
            remove.setOnAction(event -> {
                ImageOperation item = getItem();
                if (item != null) {
                    operations.remove(item);
                }
            });

            // Drag-to-reorder: the dragged index travels as plain text on the dragboard, and the drop
            // target moves that element to its own index. No custom gesture object needed for a list
            // this small.
            setOnDragDetected(event -> {
                if (getItem() == null) {
                    return;
                }
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(String.valueOf(getIndex()));
                dragboard.setContent(content);
                event.consume();
            });
            setOnDragOver(event -> {
                if (!Objects.equals(event.getGestureSource(), this) && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                boolean accepted = false;
                if (dragboard.hasString()) {
                    int from = Integer.parseInt(dragboard.getString());
                    int to = getIndex();
                    if (from != to && from >= 0 && from < operations.size() && to >= 0 && to < operations.size()) {
                        ImageOperation moved = operations.remove(from);
                        operations.add(to, moved);
                        accepted = true;
                    }
                }
                event.setDropCompleted(accepted);
                event.consume();
            });
        }

        @Override
        protected void updateItem(ImageOperation item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                // Reuses the exact DSL fragment render() would emit for this one operation, so the row
                // label can never drift from what Start actually sends to the engine.
                text.setText(PipelineFormat.render(List.of(item)));
                setGraphic(root);
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Add row: operation type + its own parameter form
    // ------------------------------------------------------------------------

    private HBox buildAddRow(ListView<ImageOperation> list) {
        ChoiceBox<String> type = new ChoiceBox<>(FXCollections.observableArrayList(ADDABLE));
        type.setValue(ADDABLE.get(0));

        Spinner<Integer> width = intSpinner(1, 10_000, 1920);
        Spinner<Integer> height = intSpinner(1, 10_000, 1080);
        javafx.scene.control.CheckBox fit = new javafx.scene.control.CheckBox(messages.get("pipelineEditor.label.fit"));
        fit.setSelected(true);
        HBox resizeParams = new HBox(GAP, new Label(messages.get("pipelineEditor.label.width")), width,
                new Label(messages.get("pipelineEditor.label.height")), height, fit);

        Spinner<Integer> radius = intSpinner(1, 64, 3);
        HBox blurParams = new HBox(GAP, new Label(messages.get("pipelineEditor.label.radius")), radius);

        Spinner<Double> sharpenAmount = doubleSpinner(0.0d, 5.0d, 1.2d, 0.1d);
        HBox sharpenParams = new HBox(GAP, new Label(messages.get("pipelineEditor.label.amount")), sharpenAmount);

        ChoiceBox<ImageOperation.EnhanceMode> mode =
                new ChoiceBox<>(FXCollections.observableArrayList(ImageOperation.EnhanceMode.values()));
        mode.setValue(ImageOperation.EnhanceMode.CLAHE);
        Spinner<Double> strength = doubleSpinner(0.0d, 1.0d, 0.5d, 0.05d);
        HBox enhanceParams = new HBox(GAP, new Label(messages.get("pipelineEditor.label.mode")), mode,
                new Label(messages.get("pipelineEditor.label.strength")), strength);

        HBox grayscaleParams = new HBox();

        javafx.scene.layout.StackPane paramsStack = new javafx.scene.layout.StackPane(
                grayscaleParams, resizeParams, blurParams, sharpenParams, enhanceParams);
        bindParamVisibility(grayscaleParams, type, "grayscale");
        bindParamVisibility(resizeParams, type, "resize");
        bindParamVisibility(blurParams, type, "blur");
        bindParamVisibility(sharpenParams, type, "sharpen");
        bindParamVisibility(enhanceParams, type, "enhance");

        Button add = new Button(messages.get("pipelineEditor.button.add"));
        add.setOnAction(event -> {
            ImageOperation created = switch (type.getValue()) {
                case "grayscale" -> new ImageOperation.Grayscale();
                case "resize" -> new ImageOperation.Resize(width.getValue(), height.getValue(), fit.isSelected());
                case "blur" -> new ImageOperation.BoxBlur(radius.getValue());
                case "sharpen" -> new ImageOperation.Sharpen(sharpenAmount.getValue());
                case "enhance" -> new ImageOperation.Enhance(mode.getValue(), strength.getValue());
                default -> throw new IllegalStateException("unreachable: " + type.getValue());
            };
            operations.add(created);
            list.getSelectionModel().selectLast();
        });

        HBox row = new HBox(GAP, type, paramsStack, add);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Mirrors {@code BatchProcessorView.bindVisibility}: managed follows visible, so hidden param
     * forms for other operation types do not leave blank space in the row. */
    private static void bindParamVisibility(javafx.scene.Node node, ChoiceBox<String> type, String name) {
        node.visibleProperty().bind(type.valueProperty().isEqualTo(name));
        node.managedProperty().bind(node.visibleProperty());
    }

    private static Spinner<Integer> intSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.setEditable(true);
        spinner.setPrefWidth(80);
        return spinner;
    }

    private static Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
        Spinner<Double> spinner = new Spinner<>(
                new javafx.scene.control.SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
        spinner.setEditable(true);
        spinner.setPrefWidth(90);
        return spinner;
    }
}
