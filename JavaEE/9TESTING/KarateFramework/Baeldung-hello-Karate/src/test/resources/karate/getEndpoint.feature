Scenario: Testing get endpoint
  Given url http://google.com
  When method GET
  Then status 200