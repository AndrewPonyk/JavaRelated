package com.ap.buildui.client;


import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.ArrayList;

public class StudyDOM {

    private ArrayList<Element> keyboardEventReceivers = new ArrayList<Element>();

    public void registerForKeyboardEvents(Element e) {
        this.keyboardEventReceivers.add(e);
    }

    /*Each widget and panel has an underlying DOM element that you can access with the getElement() method.
    You can use the getElement() method to get the underlying element from the DOM.*/
    public native void putElementLinkIDsInList(Element elt, ArrayList<String> list) /*-{
        var links = elt.getElementsByTagName("a");

        for (var i = 0; i < links.length; i++) {
            var link = links.item(i);
            link.id = ("uid-a-" + i);
            list.@java.util.ArrayList::add(Ljava/lang/Object;)(link.id);
        }
    }-*/;

    public void rewriteLinksIterative() {
        ArrayList<String> links = new ArrayList<String>();
        putElementLinkIDsInList(RootPanel.getBodyElement(), links);
        for (int i = 0; i < links.size(); i++) {
            Element elt = DOM.getElementById(links.get(i));
            rewriteLink(elt, "www.example.com");
        }
    }



    private void rewriteLink(Element element, String sitename) {
        String href = element.getPropertyString("href");
        if (null == href) {
            return;
        }

        // We want to re-write absolute URLs that go outside of this site
        if (href.startsWith("http://") &&
        !href.startsWith("http://"+sitename+"/")) {
            element.setPropertyString("href", "http://"+sitename+"/Blocked.html");
        }
    }

    public void captureBrowserEvent(Element ... elements){
        for (Element element : elements) {
            this.keyboardEventReceivers.add(element);
        }
        setupKeyboardShortcuts();
    }


    private void setupKeyboardShortcuts() {
        Event.addNativePreviewHandler(new Event.NativePreviewHandler() {
            public void onPreviewNativeEvent(Event.NativePreviewEvent preview) {
                NativeEvent event = preview.getNativeEvent();

                Element elt = event.getEventTarget().cast();
                int keycode = event.getKeyCode();
                boolean ctrl = event.getCtrlKey();
                boolean shift = event.getShiftKey();
                boolean alt = event.getAltKey();
                boolean meta = event.getMetaKey();
                // http://quirksmode.org/dom/events/index.html
                if (event.getType().equalsIgnoreCase("click") && ctrl && keyboardEventReceivers.contains(elt)
                        /*|| !isInterestingKeycode(keycode)*/) {
                    // Tell the event handler to continue processing this event.
                    //This handler is executed before standard event handlers !!!!
                    Window.alert("Click happens , and it is intercepted. CTRL pressed:" + ctrl);
                    return;
                }

                GWT.log("Processing Keycode" + keycode, null);
                //handleKeycode(keycode);

                // Tell the event handler that this event has been consumed
                preview.consume();
            }
        });
    }
}
