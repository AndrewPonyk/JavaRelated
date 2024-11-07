package com.ap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ReadTelegramDumpJson {
	private static final String TELEGRAM_DUMP_JSON = "C:\\Users\\Andrii_Ponyk\\Downloads\\Telegram Desktop\\ChatExport_2024-10-04\\result.json";

	private static int counter = 0;

	public static void main(String[] args) {
		JsonFactory jsonFactory = new JsonFactory();
		List<MessageResult> results = new ArrayList<>();

		try (JsonParser jsonParser = jsonFactory.createParser(new File(TELEGRAM_DUMP_JSON))) {
			// Move to the start of the JSON object
			if (jsonParser.nextToken() == JsonToken.START_OBJECT) {
				// Iterate through the JSON tokens
				while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
					String fieldName = jsonParser.getCurrentName();
					if ("messages".equals(fieldName)) {
						// Move to the start of the messages array
						if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
							// Iterate through the messages array
							while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
								String from = null;
								StringBuilder textEntities = new StringBuilder();

								// Process each message object
								while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
									String messageField = jsonParser.getCurrentName();
									if ("from".equals(messageField)) {
										jsonParser.nextToken();
										from = jsonParser.getText();
									} else if ("text_entities".equals(messageField)) {
										if (jsonParser.nextToken() == JsonToken.START_ARRAY) {
											// Iterate through the text_entities array
											while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
												while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
													String entityField = jsonParser.getCurrentName();
													if ("text".equals(entityField)) {
														jsonParser.nextToken();
														textEntities.append(jsonParser.getText()).append(" ");
													} else {
														jsonParser.skipChildren();
													}
												}
											}
										}
									} else {
										jsonParser.skipChildren();
									}
								}

								if (from != null && textEntities.toString().trim().length() > 0) {
									System.out.println(counter + ")" + from + " : " + textEntities.toString().trim());
									results.add(new MessageResult(counter, from, textEntities.toString().trim()));

									counter++;
								}
							}
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			objectMapper.writerWithDefaultPrettyPrinter()
					.writeValue(new File("C:\\Users\\Andrii_Ponyk\\Downloads\\Telegram Desktop\\ChatExport_2024-10-04\\result_format.json"), results);
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (PrintWriter writer = new PrintWriter(new FileWriter("C:\\Users\\Andrii_Ponyk\\Downloads\\Telegram Desktop\\ChatExport_2024-10-04\\result_format.txt"))) {
			for (MessageResult result : results) {
				writer.println(result.getCounter() + ") " + result.getFrom() + ": " + result.getMessage());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println(counter);
	}


	static class MessageResult {
		private int counter;
		private String from;
		private String message;

		public MessageResult(int counter, String from, String message) {
			this.counter = counter;
			this.from = from;
			this.message = message;
		}

		// Getters and setters
		public int getCounter() {
			return counter;
		}

		public void setCounter(int counter) {
			this.counter = counter;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
	}
}