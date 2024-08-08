package qupath.lib.extension.cedar;

import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableCell;

/**
 * Customized table cell to choose annotation type. By using this cell, we can reduce the clicks from 3 to 2.
 * Note: The code here is modified from JavaFX's ChoiceBoxTableCell so that we can control the behavior's choiceBox.
 * @param <S>
 * @param <T>
 */
public class AnnotationTypeChoiceBox<S, T> extends TableCell<S, T> {
    private final ChoiceBox<T> choiceBox;

    public AnnotationTypeChoiceBox(T[] items) {
        choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(items));

        choiceBox.showingProperty().addListener(o -> {
            if (!choiceBox.isShowing()) {
                commitEdit(choiceBox.getSelectionModel().getSelectedItem());
            }
        });
        choiceBox.setVisible(false);

        // Handle cell clicks to start editing
        setOnMouseClicked(event -> {
            if (!isEmpty()) {
                startEdit();
            }
        });
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                choiceBox.setValue(item);
                setGraphic(choiceBox);
                setText(null);
                choiceBox.setVisible(true);
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
            choiceBox.setValue(getItem());
            setGraphic(choiceBox);
            setText(null);
            choiceBox.setVisible(true);
            choiceBox.requestFocus(); // Request focus to show the dropdown
            choiceBox.show(); // Show the dropdown immediately
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setText(getItem() != null ? getItem().toString() : null);
        setGraphic(null);
    }
}
