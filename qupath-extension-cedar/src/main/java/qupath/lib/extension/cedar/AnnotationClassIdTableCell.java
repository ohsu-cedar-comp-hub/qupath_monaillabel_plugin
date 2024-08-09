package qupath.lib.extension.cedar;

import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public class AnnotationClassIdTableCell<S> extends TableCell<S, Integer> {
    private final TextField textField;

    public AnnotationClassIdTableCell() {
        textField = new TextField();

        // Commit edit when the text field loses focus
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                commitEdit(parseInteger(textField.getText()));
            }
        });

        // Commit edit when the mouse moves away from the cell
        setOnMouseExited(event -> {
            if (isEditing()) {
                commitEdit(parseInteger(textField.getText()));
            }
        });

        // Start editing on single click
        setOnMouseClicked(event -> {
            if (!isEditing() && !isEmpty()) {
                startEdit();
            }
        });

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                commitEdit(parseInteger(textField.getText()));
                event.consume();
            }
        });
    }

    @Override
    protected void updateItem(Integer item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                textField.setText(item.toString());
                setGraphic(textField);
                setText(null);
                textField.requestFocus();
            } else {
                setText(item.toString());
                setGraphic(null);
            }
        }
    }

    @Override
    public void startEdit() {
        super.startEdit();
        if (!isEmpty()) {
            textField.setText(getItem() != null ? getItem().toString() : "");
            setGraphic(textField);
            setText(null);
            textField.requestFocus();
            textField.selectAll(); // For easy editing
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() != null ? getItem().toString() : null);
        setGraphic(null);
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null; // Or handle the parse exception as needed
        }
    }
}
