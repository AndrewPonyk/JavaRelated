Feature: Testing rest api with Karate
  Background:
    * def testRequest = read('json/test_request.json')


  Scenario: Testing get endpoint
    Given url 'http://google.com'
    When method GET
    Then status 200

  Scenario: Test post request and check response
    Given url 'https://postman-echo.com/post'
    And request testRequest // NOTE-----> HERE WE USE va
    And header Content-Type = 'application/json'
    When method POST
    Then status 200
    And match $.url == "https://postman-echo.com/post"
    And match $.data.a == 1