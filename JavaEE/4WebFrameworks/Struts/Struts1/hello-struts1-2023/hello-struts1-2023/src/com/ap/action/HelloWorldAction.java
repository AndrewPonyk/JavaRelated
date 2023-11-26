package com.ap.action;

import com.ap.form.HelloWorldForm;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;

public class HelloWorldAction extends Action {

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        HelloWorldForm helloWorldForm = (HelloWorldForm) form;

        if (request.getParameter("needError") != null) {
            return mapping.findForward("failure");
        }


        if (helloWorldForm.getAction() == null || helloWorldForm.getAction().equals("")) {
            if (helloWorldForm.getAkaList().isEmpty()) {
                helloWorldForm.getAkaList().add(new MyEntry("First", true));
                helloWorldForm.getAkaList().add(new MyEntry("Second", false));
                helloWorldForm.setAkaListSize(helloWorldForm.getAkaListSize());
            }
        }

        if ("save".equals(helloWorldForm.getAction())) {
            helloWorldForm.setOutMessage("Saved successfully!");
        }

        return mapping.findForward("success");
    }

    public static class MyEntry {
        String key;
        Boolean value;

        public MyEntry(String key, Boolean value) {
            this.key = key;
            this.value = value;
        }

        public MyEntry() {
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public Boolean getValue() {
            return value;
        }

        public void setValue(Boolean value) {
            this.value = value;
        }
    }
}
