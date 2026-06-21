package com.sshmanager.tools;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class SvgToPngConverter extends Application {

    private static final Path IMAGES_DIR = Path.of(System.getProperty("svgDir", "src/main/resources/images"));
    private static final Path PNG_DIR = IMAGES_DIR.resolve(System.getProperty("pngDirName", "png"));
    private static final String ICON_COLOR = System.getProperty("iconColor", "#111827");
    private static final int SIZE = 128;

    private final WebView webView = new WebView();
    private List<Path> svgFiles;
    private int index;
    private Path currentSvgPath;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        if (Files.exists(PNG_DIR)) {
            try (var paths = Files.walk(PNG_DIR)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(PNG_DIR))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete stale PNG " + path, e);
                            }
                        });
            }
        }
        Files.createDirectories(PNG_DIR);
        svgFiles = Files.list(IMAGES_DIR)
                .filter(path -> path.getFileName().toString().endsWith(".svg"))
                .sorted()
                .toList();

        webView.setPageFill(Color.TRANSPARENT);
        webView.setPrefSize(SIZE, SIZE);
        webView.setMinSize(SIZE, SIZE);
        webView.setMaxSize(SIZE, SIZE);

        stage.setScene(new Scene(webView, SIZE, SIZE, Color.TRANSPARENT));
        stage.setOpacity(0);
        stage.show();

        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED && currentSvgPath != null) {
                Path loadedPath = currentSvgPath;
                Platform.runLater(() -> Platform.runLater(() -> snapshot(loadedPath)));
            }
        });

        convertNext();
    }

    private void convertNext() {
        if (index >= svgFiles.size()) {
            System.out.println("Converted " + svgFiles.size() + " SVG files to PNG in " + PNG_DIR);
            Platform.exit();
            return;
        }

        Path svgPath = svgFiles.get(index++);
        currentSvgPath = svgPath;
        try {
            String svg = Files.readString(svgPath, StandardCharsets.UTF_8)
                    .replaceFirst("<svg\\s+", "<svg style=\"color:" + ICON_COLOR + "\" ");
            String html = """
                    <html>
                      <head>
                        <style>
                          html, body { margin: 0; width: 100%%; height: 100%%; overflow: hidden; background: rgba(0,0,0,0); color: %s; }
                          body { display: flex; align-items: center; justify-content: center; }
                          svg { width: 100%%; height: 100%%; display: block; }
                        </style>
                      </head>
                      <body>%s</body>
                    </html>
                    """.formatted(ICON_COLOR, svg);

            webView.getEngine().loadContent(html);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + svgPath, e);
        }
    }

    private void snapshot(Path svgPath) {
        try {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            WritableImage image = webView.snapshot(parameters, new WritableImage(SIZE, SIZE));
            String pngName = svgPath.getFileName().toString().replaceFirst("\\.svg$", ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", PNG_DIR.resolve(pngName).toFile());
            currentSvgPath = null;
            convertNext();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write PNG for " + svgPath, e);
        }
    }
}
