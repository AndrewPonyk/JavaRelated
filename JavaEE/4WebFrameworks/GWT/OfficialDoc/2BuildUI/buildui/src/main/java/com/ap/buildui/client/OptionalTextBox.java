/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ap.buildui.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 *
 * @author andrew
 */
public class OptionalTextBox extends Composite implements ClickHandler {

    private TextBox textBox = new TextBox();
    private CheckBox checkBox = new CheckBox();

    public OptionalTextBox(String caption) {
        VerticalPanel panel = new VerticalPanel();
        panel.add(checkBox);
        panel.add(textBox);
        
        // Set the check box's caption, and check it by default.
        checkBox.setText(caption);
        checkBox.setChecked(true);
        checkBox.addClickHandler(this);
        
        // All composites must call initWidget() in their constructors.
        initWidget(panel);

        // Give the overall composite a style name.
        setStyleName("example-OptionalCheckBox");

    }

    public void onClick(ClickEvent event) {
        Object sender = event.getSource();
        if (sender == checkBox) {
            // When the check box is clicked, update the text box's enabled state.
            textBox.setEnabled(checkBox.isChecked());
        }
    }
    
    public void setCaption(String caption) {
      // Note how we use the use composition of the contained widgets to provide
      // only the methods that we want to.
      checkBox.setText(caption);
    }

    
    public String getCaption() {
      return checkBox.getText();
    }


}
