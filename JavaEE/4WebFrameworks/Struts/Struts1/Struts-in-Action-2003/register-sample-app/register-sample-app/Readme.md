Has a lot of issues


First: Spring in action use older version of struts (not 1.3.1) - so in action they have perform methods instead of execute !!!!!!



--------------------
Second: configure application.properties - they totally havent config for this
I put resources folder with application.properties directly in src folder (so in packed app it appears in web-inf/classes/resources/application.properties)
 and use next config !!!

    <message-resources parameter="resources.application" null="false">
        <set-property property="factory" value="org.apache.struts.util.PropertyMessageResourcesFactory" />
    </message-resources>
----------------------------

One more ActionErrors - has changes from version 1.2 to 1.3
i used next code :

if (!validated) {
// credentials don't match
ActionErrors errors = new ActionErrors();

            errors.add("name",
                    new ActionMessage("error.logon.invalid"));
            saveErrors(request, errors);
            // return to input page
            return (new ActionForward(mapping.getInput()));
        }


works!

----------------------
