package com.ap.buildui.client.editors;

import com.google.gwt.editor.client.Editor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

public class EmployeeEditor extends Composite implements Editor<Employee> {
    TextBox name;

    @Editor.Path(value = "title")
    TextBox employeeTitle;

    @Editor.Ignore
    Label id;

    public EmployeeEditor(){

    }
}
