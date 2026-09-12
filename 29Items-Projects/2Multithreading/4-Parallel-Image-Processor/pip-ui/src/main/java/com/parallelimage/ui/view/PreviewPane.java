package com.parallelimage.ui.view;

import com.parallelimage.ui.i18n.Messages;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;

/**
 * A side-by-side before/after view of one finished job, read straight off disk.
 *
 * <p>Deliberately not wired to any in-flight progress event: {@code ProgressEvent} carries no pixel
 * data by design, and threading a {@link BufferedImage} through the engine for the UI's benefit would
 * put a decode inside every worker's hot path for a feature only the FX thread needs. The two files are
 * already durable by the time a batch shows up in history, so this re-reads them instead — one extra
 * decode of a pair the user just watched get written, on the thread that is about to display it.
 */
public final class PreviewPane extends VBox {

    private static final Logger LOG = System.getLogger(PreviewPane.class.getName());
    private static final double THUMB_WIDTH = 320.0d;

    private final Messages messages;
    private final ImageView before = imageView();
    private final ImageView after = imageView();
    private final Label message;
    private final SplitPane split;

    public PreviewPane(Messages messages) {
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.message = new Label(messages.get("previewPane.default"));
        setSpacing(6.0d);
        getStyleClass().add("preview-pane");

        message.getStyleClass().add("preview-message");
        message.setWrapText(true);

        split = new SplitPane(labeled(messages.get("previewPane.before"), before),
                labeled(messages.get("previewPane.after"), after));
        split.setDividerPositions(0.5d);
        split.getStyleClass().add("preview-split");

        // Exactly one of the two is ever shown: a split pane with two blank panes reads as broken,
        // not as "nothing to show yet".
        split.visibleProperty().bind(message.visibleProperty().not());
        split.managedProperty().bind(split.visibleProperty());
        message.managedProperty().bind(message.visibleProperty());

        getChildren().addAll(message, split);
    }

    /** Loads {@code source} and {@code target} from disk and shows them side by side. */
    public void show(Path source, Path target) {
        Image beforeImage = load(source);
        Image afterImage = load(target);
        before.setImage(beforeImage);
        after.setImage(afterImage);
        if (beforeImage != null && afterImage != null) {
            message.setVisible(false);
        } else {
            before.setImage(null);
            after.setImage(null);
            message.setText(messages.get("previewPane.unavailable",
                    String.valueOf(beforeImage == null ? source : target)));
            message.setVisible(true);
        }
    }

    /** Back to the initial "nothing to show" state, e.g. when a fresh batch starts. */
    public void clear() {
        before.setImage(null);
        after.setImage(null);
        message.setText(messages.get("previewPane.default"));
        message.setVisible(true);
    }

    private static Image load(Path path) {
        if (path == null) {
            return null;
        }
        try {
            BufferedImage buffered = ImageIO.read(path.toFile());
            return buffered == null ? null : SwingFXUtils.toFXImage(buffered, null);
        } catch (IOException e) {
            LOG.log(Level.WARNING, () -> "cannot read " + path + " for preview: " + e);
            return null;
        }
    }

    private static VBox labeled(String title, ImageView view) {
        Label heading = new Label(title);
        heading.getStyleClass().add("preview-heading");
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        return new VBox(4.0d, heading, scroll);
    }

    private static ImageView imageView() {
        ImageView view = new ImageView();
        view.setPreserveRatio(true);
        view.setFitWidth(THUMB_WIDTH);
        return view;
    }
}
